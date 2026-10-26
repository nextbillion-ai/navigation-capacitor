import XCTest
import CoreLocation
import NbmapCoreNavigation
@testable import NBNavigationCapacitor

/// Uses the released SDK serializer without making network requests or requiring a key.
final class RouteConfigurationTests: XCTestCase {
    private let avoidValues = ["toll", "highway", "ferry", "sharp_turn", "uturn", "service_road", "left_turn", "right_turn", "tunnel"]
    private let truckTypes = ["rigid_truck", "semi_trailer", "b_double", "road_train", "generic_truck"]
    private let hazmats = ["general", "circumstantial", "explosive", "harmful_to_water"]
    private var waypoints: [Waypoint] {
        [(1.2834, 103.8607), (1.2845, 103.8441), (1.3048, 103.8318)].map {
            Waypoint(coordinate: CLLocationCoordinate2D(latitude: $0.0, longitude: $0.1))
        }
    }

    private func make(_ input: [String: Any] = [:]) throws -> NavigationRouteOptions {
        try RouteConfiguration.makeOptions(input, waypoints: waypoints)
    }

    private func body(_ options: RouteOptions) throws -> [String: Any] {
        let data = try XCTUnwrap(Directions.shared.requestBody(forCalculating: options))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func selections(_ body: [String: Any], _ field: String) -> Set<String> {
        Set((body[field] as? String ?? "").split(separator: "|").map(String.init))
    }

    private var truckInput: [String: Any] {
        ["profile": "truck", "avoid": avoidValues + ["toll"],
         "departureTime": 1893456000, "crossBorder": true, "turnAngleRange": 120,
         "language": "fr", "unit": "imperial", "approaches": ["curb", "curb"], "truck": [
            "size": ["heightCm": 350, "widthCm": 250, "lengthCm": 1000],
            "weightKg": 20000, "axleLoadTonnes": 8.5,
            "hazmatTypes": hazmats, "preferTruckRoutes": true, "types": truckTypes
        ]]
    }

    private func assertTruck(_ options: RouteOptions, file: StaticString = #filePath, line: UInt = #line) throws {
        let request = try body(options)
        XCTAssertEqual(request["mode"] as? String, "truck", file: file, line: line)
        XCTAssertEqual(request["departure_time"] as? Int, 1893456000, file: file, line: line)
        XCTAssertEqual(request["cross_border"] as? Bool, true, file: file, line: line)
        XCTAssertEqual(request["turn_angle_range"] as? Int, 120, file: file, line: line)
        XCTAssertEqual(request["lang"] as? String, "fr", file: file, line: line)
        XCTAssertEqual(request["unit"] as? String, "imperial", file: file, line: line)
        XCTAssertEqual(request["approaches"] as? String, "curb;curb", file: file, line: line)
        XCTAssertEqual(request["truck_size"] as? String, "350,250,1000", file: file, line: line)
        XCTAssertEqual(request["truck_weight"] as? Int, 20000, file: file, line: line)
        XCTAssertEqual(try XCTUnwrap(request["truck_axle_load"] as? Double), 8.5, accuracy: 0.000001, file: file, line: line)
        XCTAssertEqual(request["prefer"] as? String, "truck_route", file: file, line: line)
        XCTAssertEqual(selections(request, "avoid"), Set(avoidValues), file: file, line: line)
        XCTAssertEqual(selections(request, "hazmat_type"), Set(hazmats), file: file, line: line)
        XCTAssertEqual(selections(request, "truck_type"), Set(truckTypes), file: file, line: line)
    }

    func testProfilesAndOrderedStopsReachRequestBody() throws {
        XCTAssertEqual(try body(make())["mode"] as? String, "car")
        for profile in ["car", "truck", "bike", "motorcycle"] {
            let options = try make(["profile": profile])
            XCTAssertEqual(options.mapOption, .flexible)
            let request = try body(options)
            XCTAssertEqual(request["mode"] as? String, profile)
            XCTAssertEqual(request["origin"] as? String, "1.2834,103.8607")
            XCTAssertEqual(request["waypoints"] as? String, "1.2845,103.8441")
            XCTAssertEqual(request["destination"] as? String, "1.3048,103.8318")
            XCTAssertEqual(request["geometry"] as? String, "polyline6")
            XCTAssertEqual(request["overview"] as? String, "full")
            XCTAssertEqual(request["route_type"] as? String, "fastest")
            XCTAssertEqual(request["alternatives"] as? Bool, true)
            XCTAssertEqual(request["altcount"] as? Int, 2)
        }
    }

    func testEveryAvoidValueAndDeduplicationReachRequestBody() throws {
        for avoid in avoidValues {
            XCTAssertEqual(try body(make(["avoid": [avoid, avoid]]))["avoid"] as? String, avoid)
        }
        XCTAssertNil(try body(make(["avoid": []]))["avoid"])
    }

    func testTruckParametersReachRequestBody() throws {
        try assertTruck(make(truckInput))
        let request = try body(make(["profile": "truck", "truck": [
            "size": ["heightCm": 1000, "widthCm": 5000, "lengthCm": 5000], "weightKg": 100000
        ]]))
        XCTAssertEqual(request["truck_size"] as? String, "1000,5000,5000")
        XCTAssertEqual(request["truck_weight"] as? Int, 100000)
    }

    func testCopyPreservesRestrictionsForRerouting() throws {
        let options = try make(truckInput)
        try assertTruck(try XCTUnwrap(options.copy() as? RouteOptions))
    }

    func testCarPlanAfterTruckDoesNotInheritRestrictions() throws {
        _ = try make(truckInput)
        let request = try body(make(["profile": "car", "avoid": []]))
        for field in ["truck_size", "truck_weight", "truck_axle_load", "truck_type", "hazmat_type", "prefer", "avoid"] {
            XCTAssertNil(request[field], field)
        }
    }

    func testInvalidInputsAreRejectedBeforeRequest() {
        let invalid: [[String: Any]] = [
            ["profile": "horse"], ["profile": NSNull()], ["avoid": "toll"], ["avoid": ["bbox"]],
            ["profile": "car", "truck": [:]], ["profile": "truck", "truck": NSNull()],
            ["profile": "truck", "truck": ["weightKg": 0]],
            ["profile": "truck", "truck": ["weightKg": 100001]],
            ["profile": "truck", "truck": ["weightKg": 20.5]],
            ["profile": "truck", "truck": ["weightKg": "20000"]],
            ["profile": "truck", "truck": ["weightKg": true]],
            ["profile": "truck", "truck": ["size": ["heightCm": 350]]],
            ["profile": "truck", "truck": ["axleLoadTonnes": -1]],
            ["profile": "truck", "truck": ["preferTruckRoutes": 1]],
            ["profile": "truck", "truck": ["types": ["rigid_truck"]]],
            ["profile": "truck", "truck": ["hazmatTypes": ["invalid"]]]
        ]
        for input in invalid { XCTAssertThrowsError(try make(input), "Accepted invalid input: \(input)") }
    }
    func testOmittedOptionsUseSDKClockLocaleAndUnits() throws {
        let before = Int(Date().timeIntervalSince1970)
        let native = NavigationRouteOptions(waypoints: waypoints, profile: .car)
        let actual = try make()
        let after = Int(Date().timeIntervalSince1970)
        XCTAssertTrue((before...after).contains(actual.departureTime))
        XCTAssertEqual(actual.locale, native.locale)
        XCTAssertEqual(actual.distanceMeasurementSystem, native.distanceMeasurementSystem)
        XCTAssertEqual(actual.crossBorder, native.crossBorder)
        XCTAssertEqual(actual.turnAngleRange, native.turnAngleRange)
        XCTAssertEqual(try body(actual)["approaches"] as? String, try body(native)["approaches"] as? String)
    }

    func testApproachOrderAndMinimumAngleArePreserved() throws {
        let options = try make(["approaches": ["unrestricted", "curb"]])
        XCTAssertTrue(options.waypoints[0].allowsArrivingOnOppositeSide)
        XCTAssertTrue(options.waypoints[1].allowsArrivingOnOppositeSide)
        XCTAssertFalse(options.waypoints[2].allowsArrivingOnOppositeSide)
        XCTAssertEqual(try body(options)["approaches"] as? String, "unrestricted;curb")
        let unrestricted = try make(["approaches": ["unrestricted", "unrestricted"]])
        XCTAssertTrue(unrestricted.waypoints.allSatisfy { $0.allowsArrivingOnOppositeSide })
        // The iOS SDK omits the field when all arrivals are unrestricted.
        XCTAssertNil(try body(unrestricted)["approaches"])
        let zero = try body(make(["avoid": ["sharp_turn"], "turnAngleRange": 1, "crossBorder": false]))
        XCTAssertEqual(zero["turn_angle_range"] as? Int, 1)
        XCTAssertEqual(zero["cross_border"] as? Bool, false)
        XCTAssertEqual(try body(make(["language": "zh-Hans", "unit": "metric"]))["lang"] as? String, "zh-Hans")
        XCTAssertEqual(try body(make(["unit": "imperial"]))["unit"] as? String, "imperial")
    }

    func testInvalidOptionalRouteSettingsAreRejected() {
        let invalid: [[String: Any]] = [
            ["departureTime": 0], ["departureTime": -1], ["departureTime": 2147483648],
            ["departureTime": 1893456000000], ["departureTime": 1.5], ["departureTime": true],
            ["departureTime": "1893456000"], ["departureTime": NSNull()],
            ["crossBorder": 1], ["crossBorder": "false"], ["crossBorder": NSNull()],
            ["turnAngleRange": 120], ["avoid": ["sharp_turn"], "turnAngleRange": 0], ["avoid": ["sharp_turn"], "turnAngleRange": -1],
            ["avoid": ["sharp_turn"], "turnAngleRange": 181], ["avoid": ["sharp_turn"], "turnAngleRange": 1.5],
            ["avoid": ["sharp_turn"], "turnAngleRange": true],
            ["language": ""], ["language": "en-US"], ["language": NSNull()],
            ["unit": "km"], ["unit": NSNull()],
            ["approaches": []], ["approaches": ["curb"]], ["approaches": ["curb", "curb", "curb"]],
            ["approaches": ["left", "curb"]], ["approaches": "curb"], ["approaches": NSNull()]
        ]
        for input in invalid { XCTAssertThrowsError(try make(input), "Accepted invalid input: \(input)") }
    }

}
