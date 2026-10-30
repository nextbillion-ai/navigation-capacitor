# Native routing configuration audit

Rechecked on 2026-10-30 against the plugin's pinned iOS Navigation SDK **4.1.4** and Android Navigation SDK **3.1.2**. The local `navigation-ios` and `navigation-android` sources were used to trace serialization and rerouting. Assertions run against the released native SDK binaries, since local development branches can differ from published versions.

## Supported configuration

The example applies settings through `NativeNavigation.planRoute({ apiKey, stops, ...routing })`. Settings changes invalidate the previous plan. Navigation uses the selected native route, rather than issuing another request with default vehicle settings.

| Wrapper field | Android native setting | iOS native setting | Serialized request / behavior |
| --- | --- | --- | --- |
| `profile` | `RouteRequestParams.Builder.mode` | `NavigationRouteOptions.profileIdentifier` | `mode=car/truck/bike/motorcycle`; both set `option=flexible`. Swift's `.motocycle` spelling serializes correctly as `motorcycle`. |
| `stops` | Origin, intermediate `waypoints`, destination | Ordered `Waypoint` array | Original visit order; coordinates serialize as latitude,longitude. Plugin limit: 2–12 points. |
| `avoid` | `Builder.avoid` | `RouteOptions.avoid` | Pipe-separated values. All nine exposed choices survive serialization; duplicates are removed. |
| `truck.size.heightCm/widthCm/lengthCm` | `Builder.truckSize` | `RouteOptions.truckSize` | `truck_size=height,width,length`, whole centimeters. Maximums 1000/5000/5000. |
| `truck.weightKg` | `Builder.truckWeight` | `RouteOptions.truckWeight` | `truck_weight`, whole kilograms, 1–100000. No conversion to tonnes. |
| `truck.axleLoadTonnes` | `Builder.truckAxleLoad` | `RouteOptions.truckAxleLoad` | `truck_axle_load`, metric tonnes. Android Float / iOS Double; decimals retained within Float precision. |
| `truck.hazmatTypes` | `Builder.hazmatType` | `NavigationHazmatTypes` | Pipe-separated `hazmat_type`; all four values verified. Order may differ without changing the selections. |
| `truck.preferTruckRoutes` | `Builder.prefer` | `RouteOptions.prefer` | `true` produces `prefer=truck_route`; false/omitted does not set a preference. |
| `truck.types` | `Builder.truckType` | `TruckTypes` | Pipe-separated `truck_type`; all five types verified. Requires `preferTruckRoutes=true`. |

Supported avoids: `toll`, `highway`, `ferry`, `sharp_turn`, `uturn`, `service_road`, `left_turn`, `right_turn`, `tunnel`.

Truck fields require `profile=truck`. Both bridges reject unsupported enum values, malformed objects/lists, nonnumeric/boolean numeric inputs, nonpositive/out-of-range dimensions/weights, incomplete dimensions, and truck types without truck-route preference. Omit optional restrictions to use service defaults. A fresh car plan does not inherit a previous truck plan's restrictions.

## Optional route preferences and SDK defaults

Both platforms now explicitly request **Flexible, fastest, full geometry, alternatives enabled, altcount=2**. Previously the iOS SDK's default altcount was 3 while Android requested 2. Shared configuration adapters own these defaults so request tests cover the settings actually used by planning.

The native geometry format remains `polyline6`. The following preferences are now optional parts of `PlanRouteOptions`:

| Wrapper field | Android SDK mapping | iOS SDK mapping | Contract |
| --- | --- | --- | --- |
| `departureTime` | `Builder.departureTime` | `RouteOptions.departureTime` | Whole Unix seconds, 1–2147483647. Omit to let the SDK handle current time; the wrapper never supplies its own clock. |
| `crossBorder` | `Builder.crossBorder` | `RouteOptions.crossBorder` | Optional boolean. False adds a crossing penalty rather than an absolute ban. |
| `turnAngleRange` | `Builder.turnAngleRange` | `RouteOptions.turnAngleRange` | Whole degrees 1–180; requires `sharp_turn` in avoid. Zero is rejected before any request. |
| `approaches` | `Builder.approaches` | `Waypoint.allowsArrivingOnOppositeSide` for each non-origin waypoint | Ordered `unrestricted`/`curb` array, length `stops.length - 1`. Repeated values are preserved. |
| `language` | `Builder.language` | `RouteOptions.locale` | Two-letter code or `zh-Hans`; only set when supplied. SDK normalization applies. |
| `unit` | `Builder.unit` | `RouteOptions.distanceMeasurementSystem` | Metric/imperial instruction units; only set when supplied. Truck inputs and numeric route summaries retain SI units. |

Both bridges reject malformed explicit values, timestamps in milliseconds/outside the Android signed 32-bit range, mismatched approach counts, zero angles, and angles without sharp-turn avoidance. The demo's blank/default controls omit optional fields. It provides local date/time entry, border/language/unit selection, a turn-angle control and one arrival-side selector per non-origin stop. Applying changes requires replanning; Cancel preserves the applied settings.

Omitted language/unit preferences remain native SDK choices. Android currently defaults to English/metric; iOS uses its SDK locale and associated measurement system. The wrapper removes its previous explicit Android English override. iOS omits the approaches wire field when all stops are unrestricted, with the same semantics as unrestricted arrival.

The wrapper still does not expose custom `route_type`, `allow`, `road_info`, `bearings`, original-shape routing, or structured bbox/geofence avoidances. It is not a generic native-parameter passthrough. Use the documented camelCase fields.

## Retaining configuration during navigation

Android `RouteFetcher` attaches request options to the returned native route. The plugin stores that route with `DirectionsRoute.toJson()` and restores it with `fromJson()` when opening its Activity. Tests verify all truck/avoid fields after this exact route JSON round-trip, and after the SDK builder copy used by rerouting.

iOS retains native `Route` objects in memory and passes them to `NBNavigationService`. The SDK's rerouting path copies the selected route's options before replacing waypoints. Tests verify all restrictions in the copied options' serialized request body. The bridge does not archive iOS route options or persist route ids across process termination. Secure NSKeyedArchiver archiving is outside this in-memory workflow.

## Arrival confirmation

Android and iOS keep the native SDK arrival sheet open at the final destination. Arrival does not resolve the bridge promise or close the native screen. Users decide whether to dismiss/continue in the SDK UI or end navigation. Closing after final arrival returns `arrived`; an earlier exit returns `cancelled`. Android records the SDK-configured arrival zone as well as arrival callbacks, since some SDK paths pause for the sheet before the callback. iOS records its final-arrival delegate and waits for explicit user dismissal. The host restores its embedded preview only after native navigation closes.

## Verification

- Android: **8 native request tests and 1 example settings test passed** on the connected Samsung SM-E5260. Tests inspect the released SDK's request objects and actual Retrofit/OkHttp request JSON without sending a route request.
- iOS: **9 XCTest cases passed** on the already running iPhone 16 Pro / iOS 18.4 simulator. Tests call the released SDK's public `Directions.requestBody(forCalculating:)` serializer and exercise native option copies. They compare omitted preferences with newly constructed SDK defaults, including the native clock, locale and measurement system.
- Android live E2E on the same phone: **32 routing scenarios passed** (31 route responses and one expected HTTP 422 for restrictive international routing), plus **6 invalid-form cases** and **zero unhandled JavaScript errors**. All exposed profiles, avoids, truck/hazmat choices and optional route fields were checked in actual SDK requests. Five representative configurations received native simulated-guidance progress and restored their preview after exit; Chinese/French instructions and metric/imperial display were observed. Separate car/truck smoke tests also passed, including map gestures, rotation and settings overlays. The later arrival-confirmation regression checks that the final sheet stays open until the user taps Done.
- iOS live E2E on a USB-connected **iPhone 12 mini / iOS 26.6.1**: **32 route scenarios and 6 invalid-form scenarios passed**, with zero captured unhandled JavaScript errors. Returned native route options matched the form and bridge, including SDK-derived defaults. Six native simulated-guidance sessions verified movement and return; the final-arrival case exposed and verified a fix for an SDK arrival sheet preventing full navigation dismissal. The current behavior waits for the user to tap Done, then dismisses the whole guidance hierarchy from its presenter. Per-case evidence remains local under the ignored verification directory.
- The standalone iOS build exposed a missing direct Cordova framework dependency in the plugin's Swift package. The package now declares Cordova alongside Capacitor, so it can compile without relying on a host target to provide the framework.

Serializer and live E2E tests establish bridge-to-request mapping and configuration retention. Initial custom departure times are passed to the SDK, while automatic rerouting follows the SDK's current-time policy. Approach ordering is verified for initial requests and unchanged option copies; the SDK owns subsequent in-navigation waypoint advancement. The live routes cover representative combinations, rather than every possible combination or real-world GPS driving. They do not establish that a particular road's vehicle restrictions exist in a region's map data. SDK map-style and camera-transition error-level messages were observed without crashes or failed guidance; these remain upstream follow-up items. Commands are in the [example guide](../example/README.md).

Sources: [Navigation API](https://docs.nextbillion.ai/routing/navigation-api), [Android SDK request parameters](https://docs.nextbillion.ai/routing/android-navigation-sdk/map-display-and-navigation), and the local native SDK implementations of `NBNavigationService`, `RouteFetcher`, `SimpleRouteFetcher`, `NBRouteOption`, and `RouteProgress.reroutingOptions`.
