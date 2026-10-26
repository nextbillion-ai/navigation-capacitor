# nb.ai — Capacitor navigation example

This is a consumer of `@nextbillion/navigation-capacitor`, not the plugin implementation. Its web UI imports the plugin package by name; its Android `MainActivity` is an ordinary `BridgeActivity`; its iOS storyboard uses the standard `CAPBridgeViewController`.

The example is displayed as **nb.ai** on iOS and Android. The original app icon is stored in `resources/icon.png`; platform-specific icons use the same artwork.

## Source structure

```text
src/
  main.ts                 Entry point: styles and app startup
  app.ts                  Composes the features and their callbacks
  state.ts                Shared trip/settings state and initial values
  data/                   Sample trips and routing choices
  views/                  Static planner/settings HTML and page mounting
  features/
    map/preview-map.ts    Native map lifecycle, bounds, visibility and rendering
    settings/
      controller.ts       Modal, tabs, keyboard focus, Apply/Cancel
      form.ts             Vehicle/cargo form values, presets and validation
      route-form.ts       Optional departure, border, language, units and arrival-side inputs
    trip/
      controller.ts       Planning, invalidation, selection and navigation
      stops-editor.ts     Ordered stop editing and sample selection
      route-results.ts    Route cards and settings/selection summaries
  shared/dom.ts           Required element lookup and error text
  style.css               Existing page styles
```

`app.ts` connects the feature controllers; features do not import the app entry point. Settings inputs remain drafts until Apply updates the shared state. Routing changes invalidate the trip and refresh the preview; Cancel leaves the applied trip intact. Native SDK calls are concentrated in the map and trip controllers. The HTML templates retain the same element IDs used by the device integration tests.

## Run from the repository root

```sh
npm ci
npm run sync
npm run android
# or npm run ios
```

The root uses npm workspaces. Do not install the example separately while developing this repository; the lockfile and dependency installation are managed at the root. Host-specific iOS location explanations and background modes are already set in this example.

`npm run dev` serves a browser preview. Route calculation and navigation require the installed iOS/Android app. Use **Settings → Connection → Apply settings** to enter your Maps + Navigation key; no key is committed to the source.

## Trips

- Singapore city drive: origin/destination.
- Singapore delivery: origin, two intermediate stops, destination.
- San Francisco short trip: origin, intermediate stop, destination.
- Dubai downtown: origin/destination.
- Edit the coordinates/names or add/remove stops for a custom trip; visit order is preserved.

Generate routes, select a result by distance/time, then start native navigation. Simulation is on by default. Arrival keeps the SDK sheet and native navigation open until the user chooses to finish. Exiting before final arrival returns `cancelled`; closing navigation after reaching the final destination returns `arrived`. Intermediate stops retain the SDK continue UI.

## Map and settings

The planning page embeds a real native NextBillion map. On larger screens it sits beside the trip panel; on phones it sits above an independently scrolling trip panel. Before connection, browsers and native apps show an explicit placeholder. The browser does not substitute a web map.

1. Open **Settings → Connection**, enter your Maps + Navigation key and apply.
2. Choose a sample trip or edit names/coordinates. Coordinate inputs expand on demand; stops remain ordered.
3. Generate routes, compare route cards, select a card or tap an alternative on the native map. Use the fit button to see the whole route.
4. Start native full-screen guidance; explicitly ending navigation returns to the same planning page and preview, including after arrival.

**Settings** groups Connection, Vehicle, Route, Avoid and Truck. Vehicle is a four-choice selector; avoidances are selectable chips. Truck settings include Rigid/Semi-trailer presets, dimensions with cm suffixes, loaded weight in kg, axle load in t, type and hazardous cargo. Truck type enables its required truck-road preference. Service defaults clears optional vehicle restrictions. See the [plugin API](../plugin/README.md) for exact units and limits.

**Route** provides an optional local departure date/time, border-crossing preference, instruction language, distance units and arrival side for each intermediate stop/destination. Leave fields at **SDK default** to omit them from the request. In **Avoid**, selecting **Sharp turns** enables an optional minimum-turn-angle field. The demo does not create a fallback departure timestamp. Custom dates use the device time zone and the native Android timestamp range, through January 2038. Route cards respect an explicitly selected metric/imperial unit.

Arrival-side selections follow existing stops when another stop is inserted or removed; new stops use unrestricted arrival. Choosing a different sample trip restores SDK-default approaches. Route preferences can be reset independently of vehicle/cargo selections.

Edits are drafts until **Apply settings**. **Cancel** discards changes and keeps the existing route. Applying changes to the key/profile/avoid/truck settings clears route selection and requires replanning; simulation can change without replanning. Keys stay only in memory.

The native preview is an overlay above the WebView. The example synchronizes its CSS viewport bounds on resize/scroll/keyboard changes, hides it while the HTML settings sheet is open, and restores it after closing or returning from guidance. Leaving the page destroys the map. SDK dependencies remain inside the plugin.

## Native build

Android requires SDK 35 and JDK 21. Configure `ANDROID_HOME` or create the ignored `android/local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`.

```sh
cd example/android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

The APK is `example/android/app/build/outputs/apk/debug/app-debug.apk`, relative to the repository root.

```sh
xcodebuild -project example/ios/App/App.xcodeproj -scheme App \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/nb-capacitor-plugin-ios-build CODE_SIGNING_ALLOWED=NO build
```

The example does not directly declare NextBillion SDK versions. They come from the installed plugin. `cap sync` generates both platform registration lists and the SPM/Gradle plugin references.

## Device integration test

After root `npm run sync`, with an Android emulator/device connected:

```sh
cd example/android
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e apiKey YOUR_KEY -e verifyArrival true \
  -e class com.cargix.navigationdemo.NavigationSmokeTest \
  com.cargix.navigationdemo.test/androidx.test.runner.AndroidJUnitRunner
```

Use `adb -s SERIAL ...` if more than one device is attached. There are Car and Truck test cases. The Truck case sets dimensions, weight, axle load, avoidances, hazardous cargo and truck type, checks that the native route retains its request options, launches guidance, exits, switches back to Car and replans. The map checks cover native style load, route geometry, actual drag gestures, portrait/landscape frame synchronization, complete route visibility, settings hide/restore and cancel-without-invalidating behavior. The test drives the package API through the actual web page, plans a three-stop route, waits for SDK guidance progress, exits and verifies `cancelled`, then replans a two-stop route and verifies that the final arrival sheet stays visible and the bridge promise remains pending, then taps the native Done control and checks return to the preview. It grants location permissions and skips when no `apiKey` argument is supplied. `verifyArrival` is optional; allow up to 3 minutes when enabled.

To check request mapping and invalid configurations without a key:

```sh
adb shell am instrument -w -e class com.cargix.navigationdemo.RoutingConfigurationTest \
  com.cargix.navigationdemo.test/androidx.test.runner.AndroidJUnitRunner
```

This runs eight tests against the real Android SDK. It checks both request objects and the Retrofit/OkHttp JSON body, including all nine avoid choices, all hazmat/truck types, dimension limits/order, unchanged kg/tonne values, native route cache serialization, truck-to-car defaults, and rejected inputs. Optional time/border/turn-angle/language/unit/approach values and untouched SDK defaults are also covered. No network route request or API key is needed.

To exercise the actual example settings form (custom values, Apply/Cancel, default reset and approaches after stop edits), run:

```sh
adb shell am instrument -w -e class com.cargix.navigationdemo.RouteSettingsTest \
  com.cargix.navigationdemo.test/androidx.test.runner.AndroidJUnitRunner
```

For a live route-options matrix on a connected Android device, supply a key at runtime:

```sh
adb shell am instrument -w -e apiKey "$NB_API_KEY" \
  -e class com.cargix.navigationdemo.RoutePreferencesE2ETest \
  com.cargix.navigationdemo.test/androidx.test.runner.AndroidJUnitRunner
```

Set `NB_API_KEY` in your local shell; do not put it in source control. The suite makes real Navigation API requests through the example form and native bridge. It checks every exposed profile, avoid choice, truck type and hazardous-cargo choice, custom departure time, border preference, turn angle, approaches, languages, units, reset behavior and invalid form inputs. Representative car/truck configurations also start native simulated guidance, receive progress updates, exit and restore the embedded preview. SDK request metadata is inspected with credentials removed; the native SDK and routing responses are not mocked.

The suite saves `files/route-e2e/results.json` and navigation screenshots in the debug app's private storage. They can be retrieved with `adb exec-out run-as com.cargix.navigationdemo ...`; keep verification output local. A Seattle-to-Vancouver route with restrictive border settings can legitimately return HTTP 422. That case checks the error message, discarded stale routes and successful subsequent replanning.

The standalone iOS test package uses the plugin directly and the released SDK serializer:

```sh
cd tests/ios
xcodebuild -scheme NavigationCapacitorRoutingTests-Package \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath /tmp/capacitor-routing-ios-build \
  CODE_SIGNING_ALLOWED=NO test
```

Choose an installed simulator name or use its `id` from `xcrun simctl list devices`. Its nine tests check all profiles, ordered stops, actual SDK request bodies, fixed routing defaults, truck constraints, rerouting copies, optional route settings, untouched SDK clock/locale/unit defaults, and invalid inputs without making network requests. See the [routing audit](../docs/routing-configuration.md) for the supported/unsupported parameter boundary.

## iOS live E2E

Open `ios/App/App.xcodeproj` and select the shared **AppE2E** scheme. Choose a connected, unlocked iPhone and set your development signing team for the App and AppE2ETests targets. In Edit Scheme → Run → Arguments, add the environment variable `NB_E2E_API_KEY`; the Test action inherits these launch environment variables. Run Product → Test. Without a key, the network suite is skipped. Do not put an actual credential in the shared scheme or source control.

The hosted XCTest drives the real Capacitor example form and bridge, checks request options on returned native SDK routes, and starts simulated native guidance with actual location progress. It covers every exposed profile, avoid, truck/hazmat choice, optional route preferences, reset, invalid form inputs, manual exit, final arrival and embedded-map recovery. It checks traversal styling on both the controller and its actual native map. It does not mock route responses or movement callbacks. Requests use live services; movement is simulated on the phone.

Credential-free `Documents/route-e2e/results.json` and screenshots are saved in the debug app container; they can be retrieved with Xcode's Devices and Simulators window or `xcrun devicectl device copy from`. XCTest also attaches screenshots to its local result bundle. Keep test artifacts local: debug bridge logs and raw Xcode result bundles can contain runtime credentials. To reproduce one routing scenario, optionally set `NB_E2E_CASE` to its name, such as `final-arrival`; omit it for the complete matrix.

## Standalone customer integration

A customer app only needs the packaged plugin and its normal Capacitor dependencies, then `npx cap sync`. The [plugin guide](../plugin/README.md) provides the public API and required host configuration. It does not need this example's `com.cargix.navigationdemo` package or any of its app resources.

## License

The example source code is licensed under the [BSD 3-Clause License](../LICENSE). See [NOTICE](../NOTICE) for the licensing scope. Use of NextBillion.ai native SDKs, APIs, map data, and services remains subject to their applicable licenses, the [NextBillion.ai Terms of Service](https://nextbillion.ai/terms-of-service), and applicable customer agreements.
