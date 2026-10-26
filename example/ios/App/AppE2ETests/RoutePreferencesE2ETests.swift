import XCTest
import UIKit
import WebKit
import Capacitor
import CoreLocation
import Nbmap
import NbmapCoreNavigation
import NbmapNavigation

/// Opt-in live E2E in the real Capacitor host. No routing or navigation responses are mocked.
@MainActor
final class RoutePreferencesE2ETests: XCTestCase {
    private var web: WKWebView!
    private var host: CAPBridgeViewController!
    private var plugin: CAPPlugin!
    private var key = ""
    private var cases: [[String: Any]] = []
    private var failures: [String] = []
    private var output: URL!

    private struct Failure: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
    private func require(_ condition: Bool, _ message: String) throws {
        if !condition { throw Failure(message: message) }
    }
    private func json(_ object: Any) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: object, options: [.fragmentsAllowed, .sortedKeys]), as: UTF8.self)
    }
    @discardableResult private func js(_ code: String) async throws -> Any {
        try await web.evaluateJavaScript(code) ?? NSNull()
    }
    private func text(_ code: String) async throws -> String { String(describing: try await js(code)) }
    private func wait(_ description: String, seconds: Double = 30, predicate: () async throws -> Bool) async throws {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline {
            if try await predicate() { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        throw Failure(message: "Timed out: \(description)")
    }
    private func waitJS(_ code: String, seconds: Double = 30) async throws {
        try await wait(code, seconds: seconds) { (try await self.js(code) as? Bool) == true }
    }
    private func field(_ object: Any, _ name: String) throws -> Any {
        var mirror: Mirror? = Mirror(reflecting: object)
        while let current = mirror {
            if let raw = current.children.first(where: { $0.label == name })?.value {
                let value = Mirror(reflecting: raw)
                if value.displayStyle == .optional {
                    return try value.children.first?.value ?? { throw Failure(message: "Nil native field: \(name)") }()
                }
                return raw
            }
            mirror = current.superclassMirror
        }
        throw Failure(message: "Missing native field: \(name)")
    }
    private func nativeRoutes() throws -> [Route] { try XCTUnwrap(field(plugin!, "routes") as? [Route]) }
    private func body(_ options: RouteOptions) throws -> [String: Any] {
        let data = try XCTUnwrap(Directions.shared.requestBody(forCalculating: options))
        var result = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        result.removeValue(forKey: "key")
        return result
    }
    private func preview() throws -> NavigationMapView {
        let embedded = try field(plugin!, "preview")
        let holder = try XCTUnwrap(field(embedded, "holder") as? UIView)
        let map = try XCTUnwrap(field(embedded, "map") as? NavigationMapView)
        try require((try field(embedded, "ready") as? Bool) == true, "Preview not ready")
        try require(!holder.isHidden && !map.isHidden && map.window != nil, "Preview hidden/detached after return")
        try require(holder.superview === web.superview, "Preview attached to an old WebView parent")
        try require(!(try field(embedded, "routes") as! [Route]).isEmpty, "Preview lost route geometry")
        try require(map.style != nil && map.bounds.width > 0 && map.bounds.height > 0, "Preview style or layout missing")
        return map
    }
    private func save() throws {
        let report: [String: Any] = ["device": UIDevice.current.model, "iOS": UIDevice.current.systemVersion, "cases": cases, "failures": failures]
        let redacted = try json(report).replacingOccurrences(of: key, with: "[REDACTED]")
        try redacted.write(to: output.appendingPathComponent("results.json"), atomically: true, encoding: .utf8)
    }
    private func capture(_ name: String) throws {
        guard let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap(\.windows).first(where: \.isKeyWindow) else { return }
        let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in window.drawHierarchy(in: window.bounds, afterScreenUpdates: true) }
        try image.pngData()?.write(to: output.appendingPathComponent(name + ".png"))
        let attachment = XCTAttachment(image: image); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func visibleText(_ view: UIView) -> [String] {
        guard !view.isHidden, view.alpha > 0 else { return [] }
        var result: [String] = []
        if let label = view as? UILabel, let text = label.text, !text.isEmpty { result.append(text) }
        if let button = view as? UIButton, let text = button.title(for: .normal), !text.isEmpty { result.append(text) }
        return result + view.subviews.flatMap(visibleText)
    }
    private func open() async throws { try await js("document.getElementById('settings-open').click(); true"); try await waitJS("!document.getElementById('settings-modal').hidden") }
    private func trip(_ border: Bool = false, short: Bool = false) async throws {
        try await js("document.getElementById('preset').value='San Francisco · Short trip'; document.getElementById('preset').dispatchEvent(new Event('change')); true")
        if short { try await js("document.querySelectorAll('#stops .remove')[2].click(); true") }
        if border {
            try await js("""
            (() => { while(document.querySelectorAll('#stops .stop').length>2)document.querySelector('#stops .remove').click();
            const points=[['Seattle',47.6062,-122.3321],['Vancouver',49.2827,-123.1207]];
            document.querySelectorAll('#stops .stop').forEach((row,i)=>row.querySelectorAll('input').forEach((input,j)=>{input.value=points[i][j]; input.dispatchEvent(new Event('input',{bubbles:true}));})); return true; })()
            """)
        }
    }
    private func configure(_ options: [String: Any]) async throws {
        try await open()
        try await js("""
        (() => { const o=\(try json(options)), e=id=>document.getElementById(id);
        const set=(id,v)=>{e(id).value=v;e(id).dispatchEvent(new Event('change',{bubbles:true}));};
        e('route-defaults').click();e('avoid-clear').click();e('truck-clear').click();
        document.querySelector('[data-profile='+ (o.profile||'car') +']').click();
        document.querySelectorAll('#avoid-options input').forEach(i=>{i.checked=(o.avoid||[]).includes(i.value);i.dispatchEvent(new Event('change',{bubbles:true}));});
        if(o.departureTime!==undefined){const d=new Date(o.departureTime*1000),p=x=>String(x).padStart(2,'0');set('route-departure-mode','custom');set('route-departure-time',d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+'T'+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds()));}
        if(o.crossBorder!==undefined)set('route-cross-border',String(o.crossBorder));
        if(o.language)set('route-language',o.language);if(o.unit)set('route-unit',o.unit);
        if(o.turnAngleRange!==undefined)set('route-turn-angle',String(o.turnAngleRange));
        if(o.approaches){set('route-approaches-mode','custom');e('route-approaches').querySelectorAll('select').forEach((s,i)=>s.value=o.approaches[i]);}
        if(o.truck){const t=o.truck,s=t.size||{};for(const [id,v] of Object.entries({'truck-height':s.heightCm,'truck-width':s.widthCm,'truck-length':s.lengthCm,'truck-weight':t.weightKg,'truck-axle':t.axleLoadTonnes}))if(v!==undefined)set(id,String(v));e('truck-prefer').checked=!!t.preferTruckRoutes;if(t.types&&t.types.length)set('truck-type',t.types[0]);document.querySelectorAll('#hazmat-options input').forEach(i=>i.checked=(t.hazmatTypes||[]).includes(i.value));}
        e('simulate').checked=true;e('settings-save').click();return true;})()
        """)
        try await waitJS("document.getElementById('settings-modal').hidden")
        try require((try await js("document.getElementById('start').disabled") as? Bool) == true, "New settings did not invalidate the plan")
    }
    private func check(_ options: [String: Any], request: [String: Any], bridge: [String: Any], native: RouteOptions, began: Int) throws {
        let defaultOptions = NavigationRouteOptions(waypoints: native.waypoints, profile: .car)
        if let language = options["language"] as? String { defaultOptions.locale = Locale(identifier: language) }
        let defaults = try body(defaultOptions)
        try require(request["mode"] as? String == options["profile"] as? String ?? "car", "Profile did not reach SDK")
        for name in ["departureTime", "crossBorder", "turnAngleRange", "approaches", "language", "unit"] {
            try require((options[name] != nil) == (bridge[name] != nil), "Unexpected bridge default: \(name)")
        }
        for (name, value) in ["route_type": "fastest", "geometry": "polyline6", "overview": "full"] { try require(request[name] as? String == value, "Wrong \(name)") }
        try require(request["alternatives"] as? Bool == true && request["altcount"] as? Int == 2, "Alternative defaults differ")
        let departure = try XCTUnwrap(request["departure_time"] as? Int)
        if let expected = options["departureTime"] as? Int { try require(departure == expected, "Custom departure changed") }
        else { try require(departure >= began && departure <= Int(Date().timeIntervalSince1970) + 2, "SDK default time is stale") }
        let mapping = ["language": "lang", "unit": "unit", "crossBorder": "cross_border", "turnAngleRange": "turn_angle_range"]
        for (input, wire) in mapping {
            var expected = options[input] ?? defaults[wire]
            if input == "language", expected as? String == "zh" { expected = "zh-Hans" }
            try require(try json(request[wire] ?? NSNull()) == json(expected ?? NSNull()), "SDK field mismatch: \(wire)")
        }
        let approaches = options["approaches"] as? [String]
        if let approaches {
            try require(zip(native.waypoints.dropFirst(), approaches).allSatisfy { $0.allowsArrivingOnOppositeSide == ($1 == "unrestricted") }, "Approach order lost")
            if approaches.contains("curb") { try require(request["approaches"] as? String == approaches.joined(separator: ";"), "Approach wire order lost") }
        } else { try require(request["approaches"] as? String == defaults["approaches"] as? String, "Stale approaches") }
        func set(_ name: String) -> Set<String> { Set((request[name] as? String ?? "").split(separator: "|").map(String.init)) }
        try require(set("avoid") == Set(options["avoid"] as? [String] ?? []), "Avoidances differ")
        if let truck = options["truck"] as? [String: Any] {
            if let size = truck["size"] as? [String: Int] { try require(request["truck_size"] as? String == "\(size["heightCm"]!),\(size["widthCm"]!),\(size["lengthCm"]!)", "Truck dimension order/units differ") }
            if let weight = truck["weightKg"] as? Int { try require(request["truck_weight"] as? Int == weight, "Truck kg changed") }
            if let axle = truck["axleLoadTonnes"] as? Double { try require(request["truck_axle_load"] as? Double == axle, "Truck axle tonnes changed") }
            try require(set("hazmat_type") == Set(truck["hazmatTypes"] as? [String] ?? []), "Hazmat selections differ")
            try require(set("truck_type") == Set(truck["types"] as? [String] ?? []), "Truck types differ")
            if truck["preferTruckRoutes"] as? Bool == true { try require(request["prefer"] as? String == "truck_route", "Truck preference missing") }
        } else {
            for f in ["truck_size", "truck_weight", "truck_axle_load", "hazmat_type", "truck_type", "prefer"] { try require(request[f] == nil, "Stale truck option: \(f)") }
        }
    }
    private func actionButton(in view: UIView, target: AnyObject, action: String) -> UIButton? {
        if let button = view as? UIButton, button.actions(forTarget: target, forControlEvent: .touchUpInside)?.contains(action) == true { return button }
        return view.subviews.lazy.compactMap { self.actionButton(in: $0, target: target, action: action) }.first
    }
    private func navigate(_ name: String, arrival: Bool = false) async throws -> [String: Any] {
        try await js("document.getElementById('start').click(); true")
        try await wait("native guidance presentation", seconds: 90) { self.host.presentedViewController is NavigationViewController }
        let controller = try XCTUnwrap(host.presentedViewController as? NavigationViewController)
        let service = try XCTUnwrap(controller.navigationService)
        let initial = service.routeProgress.distanceTraveled
        try await wait("actual simulated guidance movement", seconds: 40) { service.routeProgress.distanceTraveled > initial + 5 }
        try require(controller.routeLineTracksTraversal, "Traversed route styling is disabled")
        let navigationMap = try XCTUnwrap(controller.navigationMapView)
        try require(navigationMap.routeLineTracksTraversal, "Traversal setting did not reach the native map")
        let labels = visibleText(controller.view)
        try capture(name + "-navigation")
        let traveled = service.routeProgress.distanceTraveled
        if arrival {
            try await wait("native final-arrival sheet", seconds: 180) { controller.presentedViewController is ArrivedViewController }
            let sheet = try XCTUnwrap(controller.presentedViewController as? ArrivedViewController)
            try await Task.sleep(nanoseconds: 5_000_000_000)
            try require(host.presentedViewController === controller && controller.presentedViewController === sheet, "Arrival sheet closed without user confirmation")
            try require((try await js("window.__e2eNavigationSettled") as? Bool) == false, "Bridge resolved before user confirmation")
            try capture(name + "-awaiting-user")
            let done = try XCTUnwrap(actionButton(in: sheet.view, target: sheet, action: "doneButtonTapped:"))
            done.sendActions(for: .touchUpInside)
        } else { controller.didTapCancel(self) }
        try await waitJS("!document.getElementById('start').disabled && document.getElementById('status').textContent.includes('\(arrival ? "Trip completed" : "Navigation closed")')", seconds: arrival ? 180 : 30)
        try await wait("guidance dismissal and preview recovery", seconds: 8) {
            guard self.host.presentedViewController == nil else { return false }
            return (try? self.preview()) != nil
        }
        _ = try preview()
        try await Task.sleep(nanoseconds: 300_000_000)
        try capture(name + "-returned")
        return ["distanceTraveled": traveled, "visibleText": labels, "routeLineTracksTraversal": true, "nativeMapTracksTraversal": navigationMap.routeLineTracksTraversal, "returned": true, "arrival": arrival, "userConfirmedArrival": arrival]
    }
    private func run(_ name: String, _ options: [String: Any] = [:], guidance: Bool = false, border: Bool = false, arrival: Bool = false) async {
        if let only = ProcessInfo.processInfo.environment["NB_E2E_CASE"], !only.isEmpty, only != name { return }
        var record: [String: Any] = ["name": name, "options": options]
        let began = Date()
        do {
            try await trip(border, short: arrival);try await configure(options)
            let started = Int(Date().timeIntervalSince1970)
            try await js("document.getElementById('plan').click(); true")
            try await waitJS("!document.getElementById('plan').disabled", seconds: 60)
            let rawTrace = try await js("window.__e2ePlan")
            let trace = try XCTUnwrap(rawTrace as? [String: Any])
            let status = try await text("document.getElementById('status').textContent")
            record["bridge"] = trace; record["statusText"] = status
            if !status.contains("Route ready") {
                throw Failure(message: status)
            }
            let routes = try nativeRoutes();let route = try XCTUnwrap(routes.first)
            let request = try body(route.routeOptions);record["requestOptions"] = request;record["routeCount"] = routes.count
            try check(options, request: request, bridge: trace, native: route.routeOptions, began: started)
            _ = try preview()
            record["instructions"] = route.legs.flatMap { $0.steps }.prefix(5).map { $0.instructions }
            let cards = try await text("document.getElementById('routes').textContent");record["routeCards"] = cards
            try require(cards.contains(options["unit"] as? String == "imperial" ? " mi" : " km"), "Route cards ignore units")
            if guidance || arrival { record["navigation"] = try await navigate(name, arrival: arrival) }
            record["status"] = "passed"
        } catch {
            let message = error.localizedDescription.replacingOccurrences(of: key, with: "[REDACTED]")
            record["status"] = "failed";record["failure"] = message;failures.append(name + ": " + message)
            try? capture(name + "-failure")
            record["statusAtFailure"] = try? await text("document.getElementById('status').textContent")
            record["presentedController"] = host.presentedViewController.map { String(describing: type(of: $0)) } ?? "none"
            if let nav = host.presentedViewController as? NavigationViewController { nav.didTapCancel(self);try? await Task.sleep(nanoseconds: 800_000_000) }
            _ = try? await js("if(!document.getElementById('settings-modal').hidden)document.getElementById('settings-cancel').click(); true")
        }
        record["elapsedSeconds"] = Date().timeIntervalSince(began);cases.append(record);try? save()
        print("E2E \(name): \(record["status"]!)")
    }
    private func invalid(_ name: String, _ edits: String) async {
        if let only = ProcessInfo.processInfo.environment["NB_E2E_CASE"], !only.isEmpty { return }
        var record: [String: Any] = ["name": name]
        do {
            let count = try await text("window.__e2ePlanCount")
            try await open();try await js(edits + ";document.getElementById('settings-save').click(); true")
            try require((try await js("document.getElementById('settings-modal').hidden") as? Bool) == false, "Invalid input was applied")
            let message = try await text("document.getElementById('settings-error').textContent")
            try require(!message.isEmpty, "No validation message")
            try require(try await text("window.__e2ePlanCount") == count, "Invalid form triggered native routing")
            try require((try await js("document.getElementById('start').disabled") as? Bool) == false, "Invalid draft discarded the applied plan")
            record["status"] = "passed";record["message"] = message
        } catch { record["status"] = "failed";record["failure"] = error.localizedDescription;failures.append(name + ": " + error.localizedDescription) }
        _ = try? await js("document.getElementById('settings-cancel').click(); true");cases.append(record);try? save()
    }
    func testLiveRouteOptionsMatrix() async throws {
        key = ProcessInfo.processInfo.environment["NB_E2E_API_KEY"] ?? ""
        try XCTSkipIf(key.isEmpty, "Set NB_E2E_API_KEY in the test configuration to enable real API requests.")
        continueAfterFailure = false
        output = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("route-e2e")
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        try await wait("Capacitor host") {
            self.host = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).compactMap { $0.rootViewController as? CAPBridgeViewController }.first
            self.web = self.host?.webView
            return self.web != nil
        }
        try await waitJS("document.querySelectorAll('#stops .stop').length===2")
        plugin = try XCTUnwrap(host.bridge?.plugin(withName: "NativeNavigation"))
        UIApplication.shared.isIdleTimerDisabled = true
        defer { UIApplication.shared.isIdleTimerDisabled = false }
        try await js("""
        (()=>{const original=window.Capacitor.nativePromise;window.__e2ePlanCount=0;window.__e2eErrors=[];
        window.Capacitor.nativePromise=function(plugin,method,options){if(plugin==='NativeNavigation'&&method==='planRoute'){window.__e2ePlan=JSON.parse(JSON.stringify(options));delete window.__e2ePlan.apiKey;window.__e2ePlanCount++;}const promise=original.apply(this,arguments);if(plugin==='NativeNavigation'&&method==='startNavigation'){window.__e2eNavigationSettled=false;return promise.finally(()=>{window.__e2eNavigationSettled=true;});}return promise;};
        window.addEventListener('error',e=>window.__e2eErrors.push(e.message));window.addEventListener('unhandledrejection',e=>window.__e2eErrors.push(String(e.reason?.message||e.reason)));return true;})()
        """)
        try await open();try await js("document.getElementById('key').value=\(try json(key));document.getElementById('settings-save').click(); true")
        try await waitJS("document.getElementById('map-status').textContent.includes('NATIVE MAP')", seconds: 60)
        let future = Int(Date().timeIntervalSince1970) + 3600
        await run("01-defaults", guidance: true)
        await run("02-custom-fr-imperial", ["departureTime": future,"crossBorder": false,"avoid": ["sharp_turn"],"turnAngleRange": 120,"approaches": ["unrestricted","curb"],"language": "fr","unit": "imperial"], guidance: true)
        await run("03-custom-zh-metric-minimum", ["crossBorder": true,"avoid": ["sharp_turn"],"turnAngleRange": 1,"approaches": ["curb","curb"],"language": "zh-Hans","unit": "metric"], guidance: true)
        await run("04-reset-defaults")
        for lang in ["en","es","de","ca","zh"] { await run("language-" + lang,["language": lang]) }
        for profile in ["bike","motorcycle"] { await run("profile-" + profile,["profile": profile]) }
        for avoid in ["toll","highway","ferry","sharp_turn","uturn","service_road","left_turn","right_turn","tunnel"] { await run("avoid-" + avoid,["avoid": [avoid]]) }
        let truck: [String: Any] = ["size": ["heightCm":350,"widthCm":250,"lengthCm":1000],"weightKg":20000,"axleLoadTonnes":8.5,"hazmatTypes":["general"],"preferTruckRoutes":true,"types":["rigid_truck"]]
        await run("truck-full",["profile":"truck","truck":truck,"avoid":["toll","ferry","uturn"],"departureTime":future,"language":"fr","unit":"imperial","approaches":["curb","unrestricted"]],guidance:true)
        for type in ["semi_trailer","b_double","road_train","generic_truck"] { await run("truck-type-"+type,["profile":"truck","truck":["preferTruckRoutes":true,"types":[type]]]) }
        for cargo in ["circumstantial","explosive","harmful_to_water"] { await run("hazmat-"+cargo,["profile":"truck","truck":["hazmatTypes":[cargo]]]) }
        await run("approaches-unrestricted",["approaches":["unrestricted","unrestricted"]])
        await run("border-expected",["crossBorder":true],border:true)
        await run("final-reset",guidance:true)
        await invalid("invalid-departure-empty","document.getElementById('route-departure-mode').value='custom';document.getElementById('route-departure-time').value=''")
        await invalid("invalid-departure-overflow","document.getElementById('route-departure-mode').value='custom';document.getElementById('route-departure-time').value='2040-01-01T12:00:00'")
        await invalid("invalid-language","document.getElementById('route-language').value='en-US'")
        let sharp="document.querySelector('#avoid-options input[value=sharp_turn]').click();document.getElementById('route-turn-angle').value="
        await invalid("invalid-angle-zero",sharp+"'0'");await invalid("invalid-angle-overflow",sharp+"'181'")
        await invalid("invalid-truck-incomplete","document.querySelector('[data-profile=truck]').click();document.getElementById('truck-clear').click();document.getElementById('truck-height').value='350'")
        await run("final-arrival",arrival:true)
        let errors = try await js("window.__e2eErrors") as? [String] ?? []
        cases.append(["name":"javascript-errors","errors":errors]);if !errors.isEmpty { failures.append("Unhandled JavaScript errors: \(errors)") };try save()
        XCTAssertTrue(failures.isEmpty, "E2E failures: \(failures)")
    }
}
