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
      form.ts             Form values, presets and routing validation
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

Generate routes, select a result by distance/time, then start native navigation. Simulation is on by default. Exit returns `cancelled`; final arrival returns `arrived`. Intermediate stops retain the SDK continue UI.

## Map and settings

The planning page embeds a real native NextBillion map. On larger screens it sits beside the trip panel; on phones it sits above an independently scrolling trip panel. Before connection, browsers and native apps show an explicit placeholder. The browser does not substitute a web map.

1. Open **Settings → Connection**, enter your Maps + Navigation key and apply.
2. Choose a sample trip or edit names/coordinates. Coordinate inputs expand on demand; stops remain ordered.
3. Generate routes, compare route cards, select a card or tap an alternative on the native map. Use the fit button to see the whole route.
4. Start native full-screen guidance; exit/arrival returns to the same planning page and preview.

**Settings** groups Connection, Vehicle, Avoid and Truck. Vehicle is a four-choice selector; avoidances are selectable chips. Truck settings include Rigid/Semi-trailer presets, dimensions with cm suffixes, loaded weight in kg, axle load in t, type and hazardous cargo. Truck type enables its required truck-road preference. Service defaults clears optional vehicle restrictions. See the [plugin API](../plugin/README.md) for exact units and limits.

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

Use `adb -s SERIAL ...` if more than one device is attached. There are Car and Truck test cases. The Truck case sets dimensions, weight, axle load, avoidances, hazardous cargo and truck type, checks that the native route retains its request options, launches guidance, exits, switches back to Car and replans. The map checks cover native style load, route geometry, actual drag gestures, portrait/landscape frame synchronization, complete route visibility, settings hide/restore and cancel-without-invalidating behavior. The test drives the package API through the actual web page, plans a three-stop route, waits for SDK guidance progress, exits and verifies `cancelled`, then replans a two-stop route and checks automatic final-arrival return. It grants location permissions and skips when no `apiKey` argument is supplied. `verifyArrival` is optional; allow up to 3 minutes when enabled.

To check request mapping and invalid configurations without a key:

```sh
adb shell am instrument -w -e class com.cargix.navigationdemo.RoutingConfigurationTest \
  com.cargix.navigationdemo.test/androidx.test.runner.AndroidJUnitRunner
```

This inspects the real native SDK request objects, including dimension order, unchanged kg/tonne values, multiple enum selections, truck-to-car defaults, and rejected inputs.

## Standalone customer integration

A customer app only needs the packaged plugin and its normal Capacitor dependencies, then `npx cap sync`. The [plugin guide](../plugin/README.md) provides the public API and required host configuration. It does not need this example's `com.cargix.navigationdemo` package or any of its app resources.
