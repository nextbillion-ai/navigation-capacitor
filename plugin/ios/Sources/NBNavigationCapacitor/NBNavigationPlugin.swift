import Capacitor
import CoreLocation
import Nbmap
import NbmapCoreNavigation
import NbmapNavigation

@objc(NBNavigationPlugin)
public final class NBNavigationPlugin: CAPPlugin, CAPBridgedPlugin, NavigationViewControllerDelegate, CLLocationManagerDelegate {
    public let identifier = "NBNavigationPlugin"
    public let jsName = "NativeNavigation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "createMap", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateMap", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setMapStops", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showMapRoute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "destroyMap", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "planRoute", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startNavigation", returnType: CAPPluginReturnPromise)
    ]
    private var routes: [Route] = []
    private var preview: EmbeddedMap?
    private var planning = false
    private var navigationCall: CAPPluginCall?
    private var navigator: NavigationViewController?
    private var service: NBNavigationService?
    private let locationManager = CLLocationManager()
    private var pendingIndex = 0
    private var pendingSimulation = true
    private var closing = false

    deinit { preview?.destroy() }

    @objc public func createMap(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard !self.planning, self.navigationCall == nil else { call.reject("Wait for planning/navigation before creating a map."); return }
            guard let key = call.getString("apiKey")?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty,
                  let bounds = call.getObject("bounds"), let webView = self.webView else { call.reject("API key, bounds and an attached WebView are required."); return }
            do {
                _ = try EmbeddedMap.frame(bounds)
                NGLAccountManager.accessToken = key
                self.preview?.destroy()
                self.preview = try EmbeddedMap(webView: webView, bounds: bounds, call: call) { [weak self] index in
                    self?.notifyListeners("mapRouteSelected", data: ["routeId": index])
                }
            } catch { call.reject(error.localizedDescription) }
        }
    }
    @objc public func updateMap(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let preview = self.preview else { call.reject("Create the map first."); return }
            if call.options["bounds"] != nil, call.getObject("bounds") == nil { call.reject("Invalid map bounds."); return }
            do { try preview.update(bounds: call.getObject("bounds"), visible: call.getBool("visible")); call.resolve() }
            catch { call.reject(error.localizedDescription) }
        }
    }
    @objc public func setMapStops(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let preview = self.preview else { call.reject("Create the map first."); return }
            guard let stops = call.getArray("stops", JSObject.self), stops.isEmpty || (2...12).contains(stops.count) else { call.reject("Use zero or 2–12 stops."); return }
            var points: [Waypoint] = []
            for stop in stops {
                guard let lat = stop["latitude"] as? Double, let lng = stop["longitude"] as? Double,
                      lat.isFinite, lng.isFinite, abs(lat)<=90, abs(lng)<=180 else { call.reject("Invalid coordinates."); return }
                points.append(Waypoint(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng), name: stop["name"] as? String))
            }
            do { try preview.stops(points); call.resolve() } catch { call.reject(error.localizedDescription) }
        }
    }
    @objc public func showMapRoute(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let preview = self.preview else { call.reject("Create the map first."); return }
            guard let index = call.getInt("routeId"), self.routes.indices.contains(index) else { call.reject("Generate routes before showing a route."); return }
            do { try preview.route(self.routes, index: index, fit: call.getBool("fit") ?? true); call.resolve() }
            catch { call.reject(error.localizedDescription) }
        }
    }
    @objc public func destroyMap(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.preview?.destroy(); self.preview = nil; call.resolve() }
    }
    @objc public func planRoute(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard !self.planning, self.navigationCall == nil else { call.reject("Another operation is active."); return }
            self.routes = []
            guard let key = call.getString("apiKey")?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty,
                  let stops = call.getArray("stops", JSObject.self), (2...12).contains(stops.count) else {
                call.reject("API key and 2–12 stops are required."); return
            }
            var waypoints: [Waypoint] = []
            for stop in stops {
                guard let lat = stop["latitude"] as? Double, let lng = stop["longitude"] as? Double,
                      lat.isFinite, lng.isFinite, abs(lat) <= 90, abs(lng) <= 180 else { call.reject("Invalid coordinates."); return }
                waypoints.append(Waypoint(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng), name: stop["name"] as? String))
            }
            let options: NavigationRouteOptions
            guard let configuration = call.options as? [String: Any] else { call.reject("Invalid route configuration."); return }
            do { options = try RouteConfiguration.makeOptions(configuration, waypoints: waypoints) }
            catch { call.reject(error.localizedDescription); return }
            NGLAccountManager.accessToken = key
            options.includesAlternativeRoutes = true
            options.routeShapeResolution = .full
            self.planning = true
            Directions.shared.calculate(options) { [weak self] routes, error in
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.planning = false
                    guard let routes, !routes.isEmpty, error == nil else { call.reject("Route request failed. Check network, key, coverage and stops."); return }
                    self.routes = routes
                    call.resolve(["routes": routes.enumerated().map { ["id": $0.offset, "distance": $0.element.distance, "duration": $0.element.expectedTravelTime] }])
                }
            }
        }
    }
    @objc public func startNavigation(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard !self.planning, self.navigationCall == nil else { call.reject("Another operation is active."); return }
            guard let index = call.getInt("routeId"), self.routes.indices.contains(index) else { call.reject("Generate routes before starting navigation."); return }
            self.pendingIndex = index
            self.pendingSimulation = call.getBool("simulate") ?? true
            self.navigationCall = call
            self.locationManager.delegate = self
            switch self.locationManager.authorizationStatus {
            case .notDetermined: self.locationManager.requestWhenInUseAuthorization()
            case .authorizedWhenInUse, .authorizedAlways: self.presentNavigation()
            default: self.rejectPermission()
            }
        }
    }
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard navigationCall != nil, navigator == nil else { return }
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: presentNavigation()
        case .denied, .restricted: rejectPermission()
        default: break
        }
    }
    private func rejectPermission() {
        navigationCall?.reject("Location permission is required. Enable location in Settings.")
        navigationCall = nil
    }
    private func presentNavigation() {
        guard navigator == nil, let presenter = bridge?.viewController, presenter.presentedViewController == nil else {
            navigationCall?.reject("A native screen is already open."); navigationCall = nil; return
        }
        closing = false
        preview?.setNavigationActive(true)
        // Keep the selected route first, so both the service and UI navigate the same route.
        var ordered = routes
        let selected = ordered.remove(at: pendingIndex)
        ordered.insert(selected, at: 0)
        let service = NBNavigationService(routes: ordered, routeIndex: 0, simulating: pendingSimulation ? .always : .never)
        self.service = service
        let controller = NavigationViewController(for: ordered, navigationOptions: NavigationOptions(navigationService: service))
        // Update the traversed route styling as navigation progresses; the SDK defaults to false.
        controller.routeLineTracksTraversal = true
        controller.delegate = self
        controller.modalPresentationStyle = .fullScreen
        navigator = controller
        presenter.present(controller, animated: true)
    }
    public func navigationViewControllerDidDismiss(_ navigationViewController: NavigationViewController, byCanceling canceled: Bool) {
        finish(reason: canceled ? "cancelled" : "arrived")
    }
    public func navigationViewController(_ navigationViewController: NavigationViewController, didArriveAt waypoint: Waypoint) {
        // A stop arrival must not close a multi-leg trip.
        guard let progress = service?.routeProgress, progress.legIndex == progress.route.legs.count - 1 else { return }
        DispatchQueue.main.async { self.finish(reason: "arrived") }
    }
    private func finish(reason: String) {
        guard !closing, let call = navigationCall else { return }
        closing = true
        service?.stop()
        let complete = {
            self.navigator = nil
            self.service = nil
            self.navigationCall = nil
            // Reattach the existing preview after UIKit has restored the WebView.
            self.preview?.setNavigationActive(false)
            call.resolve(["reason": reason])
        }
        if let navigator, navigator.presentingViewController != nil { navigator.dismiss(animated: true, completion: complete) }
        else { complete() }
    }
}
