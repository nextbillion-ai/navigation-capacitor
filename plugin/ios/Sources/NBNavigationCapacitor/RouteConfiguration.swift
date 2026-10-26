import Foundation
import CoreFoundation
import NbmapCoreNavigation

/// Validates the bridge boundary and maps the shared contract to iOS SDK options.
enum RouteConfiguration {
    private static let profiles: [String: NBNavigationMode] = ["car": .car, "truck": .truck, "bike": .bike, "motorcycle": .motocycle]
    private static let avoids: Set<String> = ["toll", "highway", "ferry", "sharp_turn", "uturn", "service_road", "left_turn", "right_turn", "tunnel"]
    private static let hazmats: Set<String> = ["general", "circumstantial", "explosive", "harmful_to_water"]
    private static let truckTypes: Set<String> = ["rigid_truck", "semi_trailer", "b_double", "road_train", "generic_truck"]

    static func makeOptions(_ input: [String: Any], waypoints: [Waypoint]) throws -> NavigationRouteOptions {
        let profile: String
        if let raw = input["profile"] {
            guard let value = raw as? String, profiles[value] != nil else { throw invalid("Unsupported profile: \(raw)") }
            profile = value
        } else { profile = "car" }
        let options = NavigationRouteOptions(waypoints: waypoints, profile: profiles[profile]!)
        options.mapOption = .flexible
        options.routeType = .fastest
        options.includesAlternativeRoutes = true
        options.alternativeCount = 2
        options.routeShapeResolution = .full
        options.avoid = try list(input, "avoid", allowed: avoids)
        try applyRouteOptions(input, to: options)
        guard let rawTruck = input["truck"] else { return options }
        guard profile == "truck" else { throw invalid("truck options require profile=truck.") }
        guard let truck = rawTruck as? [String: Any] else { throw invalid("truck must be an object.") }
        if let rawSize = truck["size"] {
            guard let size = rawSize as? [String: Any] else { throw invalid("truck.size must contain heightCm, widthCm and lengthCm.") }
            options.truckSize = try [
                Int(number(size, "heightCm", maximum: 1000, integer: true)),
                Int(number(size, "widthCm", maximum: 5000, integer: true)),
                Int(number(size, "lengthCm", maximum: 5000, integer: true))
            ]
        }
        if truck["weightKg"] != nil { options.truckWeight = Int(try number(truck, "weightKg", maximum: 100000, integer: true)) }
        if truck["axleLoadTonnes"] != nil {
            let axle = try number(truck, "axleLoadTonnes", maximum: Double(Float.greatestFiniteMagnitude))
            guard Float(axle) > 0 else { throw invalid("axleLoadTonnes is too small.") }
            options.truckAxleLoad = axle
        }
        let hazmatValues = try list(truck, "hazmatTypes", allowed: hazmats)
        if !hazmatValues.isEmpty { options.hazmatTypes = NavigationHazmatTypes(descriptions: hazmatValues)! }
        var prefer = false
        if let raw = truck["preferTruckRoutes"] {
            guard let value = raw as? NSNumber, CFGetTypeID(value) == CFBooleanGetTypeID() else { throw invalid("preferTruckRoutes must be a boolean.") }
            prefer = value.boolValue
        }
        let types = try list(truck, "types", allowed: truckTypes)
        guard types.isEmpty || prefer else { throw invalid("truck.types requires preferTruckRoutes=true.") }
        if prefer { options.prefer = .truckRoute }
        if !types.isEmpty { options.truckTypes = TruckTypes(descriptions: types)! }
        return options
    }

    private static func applyRouteOptions(_ input: [String: Any], to options: NavigationRouteOptions) throws {
        // Do not substitute an external clock, locale or measurement system for omitted fields.
        if input["departureTime"] != nil {
            options.departureTime = Int(try number(input, "departureTime", maximum: Double(Int32.max), integer: true))
        }
        if let raw = input["crossBorder"] {
            guard let value = raw as? NSNumber, CFGetTypeID(value) == CFBooleanGetTypeID() else { throw invalid("crossBorder must be a boolean.") }
            options.crossBorder = value.boolValue
        }
        if input["turnAngleRange"] != nil {
            guard options.avoid.contains("sharp_turn") else { throw invalid("turnAngleRange requires avoid to include sharp_turn.") }
            options.turnAngleRange = Int(try number(input, "turnAngleRange", maximum: 180, integer: true))
        }
        if let raw = input["language"] {
            guard let language = raw as? String,
                  let match = language.range(of: "^(?:[a-z]{2}|zh-Hans)$", options: .regularExpression),
                  match == language.startIndex..<language.endIndex else {
                throw invalid("language must be a two-letter code such as en/fr/zh, or zh-Hans.")
            }
            options.locale = Locale(identifier: language)
        }
        if let raw = input["unit"] {
            guard let unit = raw as? String, let system = MeasurementSystem(description: unit) else {
                throw invalid("unit must be metric or imperial.")
            }
            options.distanceMeasurementSystem = system
        }
        if input["approaches"] != nil {
            let approaches = try list(input, "approaches", allowed: ["unrestricted", "curb"], deduplicate: false)
            guard approaches.count == options.waypoints.count - 1 else {
                throw invalid("approaches must contain one value per non-origin stop (stops.length - 1).")
            }
            for (index, approach) in approaches.enumerated() {
                options.waypoints[index + 1].allowsArrivingOnOppositeSide = approach == "unrestricted"
            }
        }
    }

    private static func invalid(_ message: String) -> NSError { NSError(domain: "NBNavigation.Configuration", code: 1, userInfo: [NSLocalizedDescriptionKey: message]) }
    private static func list(_ input: [String: Any], _ name: String, allowed: Set<String>, deduplicate: Bool = true) throws -> [String] {
        guard let raw = input[name] else { return [] }
        guard let values = raw as? [String] else { throw invalid("\(name) must be an array of strings.") }
        var result: [String] = []
        for value in values {
            guard allowed.contains(value) else { throw invalid("Unsupported \(name): \(value)") }
            if !deduplicate || !result.contains(value) { result.append(value) }
        }
        return result
    }
    private static func number(_ input: [String: Any], _ name: String, maximum: Double, integer: Bool = false) throws -> Double {
        guard let value = input[name] as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() else { throw invalid("\(name) must be a number.") }
        let number = value.doubleValue
        guard number.isFinite, number > 0, number <= maximum, !integer || number.rounded() == number else {
            throw invalid("\(name) must be \(integer ? "a whole number" : "a number") > 0 and <= \(maximum).")
        }
        return number
    }
}
