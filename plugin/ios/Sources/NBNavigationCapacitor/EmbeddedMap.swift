import UIKit
import CoreFoundation
import WebKit
import Capacitor
import Nbmap
import NbmapCoreNavigation
import NbmapNavigation

/// A native sibling of the WebView; frame coordinates come from the HTML placeholder.
final class EmbeddedMap: NSObject, NGLMapViewDelegate, NavigationMapViewDelegate {
    private weak var webView: WKWebView?
    private let holder = UIView()
    private let map: NavigationMapView
    private var pending: CAPPluginCall?
    private var timeout: DispatchWorkItem?
    private var ready = false
    private var active = true
    private var visible = true
    private var navigationActive = false
    private var bounds = CGRect.zero
    private var routes: [Route] = []
    private var selected = -1
    private let onSelection: (Int) -> Void

    init(webView: WKWebView, bounds: [String: Any], call: CAPPluginCall, onSelection: @escaping (Int) -> Void) throws {
        let frame = try Self.frame(bounds)
        guard let parent = webView.superview else { throw Self.error("The host WebView is not attached.") }
        self.webView = webView
        self.onSelection = onSelection
        self.pending = call
        self.map = NavigationMapView(frame: CGRect(origin: .zero, size: frame.size), styleURL: NGLStyle.navigationPreviewDayStyleURL)
        super.init()
        holder.clipsToBounds = true
        holder.layer.cornerRadius = 14
        holder.accessibilityIdentifier = "nb-native-map-container"
        map.accessibilityIdentifier = "nb-native-preview-map"
        holder.addSubview(map)
        parent.insertSubview(holder, aboveSubview: webView)
        map.delegate = self
        map.navigationMapDelegate = self
        map.showsUserLocation = false
        self.bounds = frame
        layout()
        let timeout = DispatchWorkItem { [weak self] in self?.fail("Map timed out. Check network and Maps access.") }
        self.timeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 30, execute: timeout)
    }
    static func error(_ message: String) -> NSError { NSError(domain: "NBNavigation.Map", code: 1, userInfo: [NSLocalizedDescriptionKey: message]) }
    static func frame(_ values: [String: Any]) throws -> CGRect {
        var numbers: [Double] = []
        for key in ["x", "y", "width", "height"] {
            guard let raw = values[key] as? NSNumber, CFGetTypeID(raw) != CFBooleanGetTypeID(), raw.doubleValue.isFinite, abs(raw.doubleValue) <= 100000 else { throw error("Invalid map bounds: \(key)") }
            numbers.append(raw.doubleValue)
        }
        guard numbers[2] > 0, numbers[3] > 0 else { throw error("Map width and height must be positive.") }
        return CGRect(x: numbers[0], y: numbers[1], width: numbers[2], height: numbers[3])
    }
    func update(bounds: [String: Any]?, visible: Bool?) throws {
        if let bounds { self.bounds = try Self.frame(bounds) }
        if let visible { self.visible = visible }
        layout()
    }
    func setNavigationActive(_ active: Bool) {
        navigationActive = active
        layout()
    }
    private func layout() {
        guard active, let webView, let parent = webView.superview else {
            holder.isHidden = true
            map.isHidden = true
            return
        }
        // Capacitor uses the WebView as its root view. Full-screen presentation can
        // move it between UIKit containers, so the overlay must follow its current parent.
        parent.insertSubview(holder, aboveSubview: webView)
        let clipped = bounds.intersection(webView.bounds)
        let hidden = !visible || navigationActive || clipped.isNull
        holder.isHidden = hidden
        // Set the map's own visibility too: the SDK uses it to pause/resume rendering.
        map.isHidden = hidden
        guard !clipped.isNull else { return }
        holder.frame = webView.convert(clipped, to: parent)
        map.frame = CGRect(x: bounds.minX-clipped.minX, y: bounds.minY-clipped.minY, width: bounds.width, height: bounds.height)
        if !hidden {
            map.setNeedsLayout()
            map.setNeedsDisplay()
        }
    }
    func mapView(_ mapView: NGLMapView, didFinishLoading style: NGLStyle) {
        guard active, !ready else { return }
        ready = true
        map.setCenter(CLLocationCoordinate2D(latitude: 1.29, longitude: 103.85), zoomLevel: 11, animated: false)
        timeout?.cancel(); timeout = nil
        pending?.resolve(); pending = nil
    }
    func mapViewDidFailLoadingMap(_ mapView: NGLMapView, withError error: Error) { fail("Map failed to load. Check network and Maps access.") }
    private func fail(_ message: String) {
        guard pending != nil else { return }
        pending?.reject(message); pending = nil; destroy()
    }
    func stops(_ stops: [Waypoint]) throws {
        try requireReady()
        routes = []; selected = -1; map.removeRoutes(); map.removeWaypoints()
        if let annotations = map.annotations { map.removeAnnotations(annotations) }
        let annotations = stops.enumerated().map { index, stop -> NGLPointAnnotation in
            let annotation = NGLPointAnnotation(); annotation.coordinate = stop.coordinate; annotation.title = "\(index+1). \(stop.name ?? "Stop")"; return annotation
        }
        map.addAnnotations(annotations)
        guard !stops.isEmpty else { return }
        var coordinates = stops.map(\.coordinate)
        map.setVisibleCoordinates(&coordinates, count: UInt(coordinates.count), edgePadding: UIEdgeInsets(top: 32, left: 32, bottom: 32, right: 32), animated: false)
    }
    func route(_ values: [Route], index: Int, fit: Bool) throws {
        try requireReady()
        routes = values; selected = index
        if let annotations = map.annotations { map.removeAnnotations(annotations) }
        var ordered = values; let route = ordered.remove(at: index); ordered.insert(route, at: 0)
        if fit { map.showcase(ordered, padding: UIEdgeInsets(top: 32, left: 32, bottom: 32, right: 32), animated: false) }
        else { map.showRoutes(ordered) }
        map.showWaypoints(route)
    }
    func navigationMapView(_ mapView: NavigationMapView, didSelect route: Route) {
        guard let index = routes.firstIndex(where: { $0 === route }), index != selected else { return }
        selected = index
        try? self.route(routes, index: index, fit: false)
        onSelection(index)
    }
    private func requireReady() throws { guard ready, active else { throw Self.error("Create the map and wait for it to load first.") } }
    func destroy() {
        guard active else { return }
        active = false; ready = false; timeout?.cancel(); timeout = nil
        pending?.reject("Map creation was cancelled."); pending = nil
        map.delegate = nil; map.navigationMapDelegate = nil
        map.removeFromSuperview(); holder.removeFromSuperview()
    }
}
