# Capacitor plugin structure and Flutter reference

Reference inspected locally: `nextbillion-navigation-flutter`, the source repository behind the empty `navigation-flutter` directory on this workstation. No changes were made to the Flutter repository.

The reference has `lib/` public models/facade/platform channels, platform-specific `android/` and `ios/` plugin implementations, dependency metadata (`pubspec.yaml`, Gradle, Podspec/SPM), and a separate `example/`. This refactor applies the same boundaries using Capacitor and npm.

| Flutter reference | Capacitor package |
| --- | --- |
| `lib/model` | `plugin/src/definitions.ts` |
| `NBNavigation` facade and method channels | `plugin/src/index.ts`, Capacitor `registerPlugin` |
| `fetchRoute(RouteRequestParams)` | `planRoute({ apiKey, stops, profile, avoid, truck })` |
| `startNavigation(NavigationLauncherConfig)` | `startNavigation({ routeId, simulate })` |
| navigation exit callback | Resolved `NavigationResult` promise after the screen closes |
| Android plugin/handlers/Activity | `plugin/android/src/main/java/ai/nextbillion/navigation/capacitor/` |
| iOS plugin/handlers | `plugin/ios/Sources/NBNavigationCapacitor/` |
| `pubspec.yaml` plugin declaration | `plugin/package.json` Capacitor platform declaration |
| pub package distribution | npm archive / registry package |
| `example/` | `example/` workspace consuming the local plugin through `file:../plugin` |

The plugin provides embedded native route preview and the full-screen navigation workflow. It does not claim feature parity with every Flutter map/preview/offline API. The Capacitor wrapper exposes its own focused map lifecycle, bounds, stop/route rendering and selection API; it does not expose all Flutter map controls or snapshot APIs.

## Ownership

The distributable plugin owns route calculation, native route caching, navigation presentation, location permission requests, SDK resource authentication, navigation lifecycle and completion results. It owns its Android manifest/Activity, consumer ProGuard rules and SDK versions, plus the iOS SPM dependency and Swift implementation.

The example owns Cargix UI, sample coordinates, app identifiers/icons, iOS permission explanation strings, background capabilities and its integration test. Neither the package's implementation nor its manifest depends on the Cargix app namespace.

`RouteConfiguration.java` / `RouteConfiguration.swift` validate the public routing contract and map profile, avoidances and vehicle constraints to native SDK request objects. Both select Flexible routing. Named size fields map to height,width,length in cm; gross weight stays in kg and axle load in tonnes. Invalid or ineffective combinations reject before the request. The example clears route selection whenever configuration changes; consumers should also replan before starting with different settings.

## Automatic registration

`cap sync` discovers Android's annotated `NBNavigationPlugin` and writes `capacitor.plugins.json`. On iOS it scans the plugin's `@objc(NBNavigationPlugin)` / `CAPBridgedPlugin` implementation, writes `packageClassList` and adds the SPM plugin product. The example needs no manual registration or direct SDK reference.

## Packaging and version compatibility

Both `plugin/` and `example/` are npm workspaces. The example imports `@nextbillion-ai/navigation-capacitor` by package name and links to the local source package through `file:../plugin`. Root build and sync scripts compile the plugin before the example; Capacitor generates native references to `plugin/`. Customer integration instructions use the published npm package.

`plugin/package.json` allowlists the built JS/types and native library sources. The example and native build output never enter the archive. `scripts/verify-package.mjs` installs the actual archive outside this workspace, builds a copied consumer and checks generated registration/dependency metadata. This catches packages that only work through a source-tree link.

The first release targets Capacitor 7 and iOS SPM. SDK versions remain the previously verified iOS 4.1.1 / Android 3.1.0; older versions in the Flutter reference are not copied. CocoaPods support is not declared for this release.

## Embedded preview

`EmbeddedMap.java` / `EmbeddedMap.swift` own a single native map overlay aligned with an HTML placeholder using viewport CSS pixels. Android forwards MapView start/resume/pause/stop/destroy through Capacitor lifecycle hooks. iOS owns a NavigationMapView sibling of WKWebView. Both load a navigation-compatible style, render cached routes and return native route selection to JS.

The example isolates native map bounds from its scrollable planner. HTML sheets hide the native overlay; they are not expected to layer over it using CSS z-index. Connection, vehicle, avoid and truck settings are draft state until Apply. Cancel preserves the applied route; applying routing changes invalidates the selection and clears the drawn preview. Full-screen guidance continues to use the cached native route.
