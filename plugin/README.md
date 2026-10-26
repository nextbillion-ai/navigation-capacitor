# @nextbillion-ai/navigation-capacitor

Open the full NextBillion native navigation screen from a Capacitor app. Plan a route from an origin, optional intermediate stops and a destination; select a native route; start simulated or GPS guidance; keep the native arrival sheet open until the user chooses to end navigation, then return to your app. The map and guidance use the native SDK. An optional native map can be positioned inside your planning page; guidance opens in a separate full-screen native screen.

## Supported versions

| Dependency | Version / requirement |
| --- | --- |
| Capacitor | 7.x |
| iOS | 14+, **Swift Package Manager** |
| iOS Navigation SDK | 4.1.1, exact SPM dependency |
| Android | API 23+, compile SDK 35, JDK 21 |
| Android Navigation SDK | 3.1.0, Maven dependency |

This release provides an SPM package for iOS. CocoaPods host projects must migrate to SPM before installing this release; a CocoaPods integration is not provided. No separate NextBillion dependency should be added to the host app.

## Installation

After publication, install from npm in your Capacitor app:

```sh
npm install @nextbillion-ai/navigation-capacitor
npx cap sync
```

Alternatively, install the delivered archive:

```sh
npm install /path/to/nextbillion-ai-navigation-capacitor-1.0.0.tgz
npx cap sync
```

During development in this repository, the example uses a workspace dependency on `file:../plugin`. Consumers of the archive do not need the example or this repository.

For a new iOS platform, select SPM:

```sh
npx cap add ios --packagemanager SPM
npx cap sync
```

For an existing CocoaPods project, follow the [Capacitor SPM migration guide](https://capacitorjs.com/docs/v7/ios/spm). Capacitor automatically discovers `NBNavigationPlugin`. You do **not** need a custom `CAPBridgeViewController`, `registerPluginInstance`, or an Android `registerPlugin` call.

The Android manifest and SDK dependencies are merged from the plugin. The host's Gradle repositories must include `google()` and `mavenCentral()` (standard Capacitor projects already do).

## Host permissions

In the iOS host's `Info.plist`, add:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Your location is used for turn-by-turn navigation.</string>
```

If your app needs background navigation, also enable Location updates and Audio background capabilities in the host, equivalent to:

```xml
<key>UIBackgroundModes</key>
<array><string>location</string><string>audio</string></array>
```

App-specific permission explanations and background capabilities belong to the host app and are not silently modified by the plugin. Android foreground location permission is requested by the plugin at navigation start. SDK foreground services are provided by the native SDK's manifest. Verify background guidance and notification-permission behavior on your target OS; this wrapper does not implement a separate always/background-location escalation flow.

## Usage

```ts
import { NBNavigation, type Stop } from '@nextbillion-ai/navigation-capacitor';

const stops: Stop[] = [
  { name: 'Start', latitude: 1.2834, longitude: 103.8607 },
  { name: 'Pickup', latitude: 1.2845, longitude: 103.8441 },
  { name: 'Destination', latitude: 1.3048, longitude: 103.8318 },
];

const { routes } = await NBNavigation.planRoute({
  apiKey: 'YOUR_NEXTBILLION_API_KEY',
  stops,
});

// Render routes in your own UI, let the driver select one, then:
const result = await NBNavigation.startNavigation({
  routeId: routes[0].id,
  simulate: true,
});

// Resolves only when the native navigation screen has closed.
console.log(result.reason); // 'arrived' | 'cancelled'
```

`NativeNavigation` is an equivalent export of the same plugin for compatibility with the original demo. Both names use Capacitor's `NativeNavigation` bridge identifier.

## Native map inside your page

The optional preview uses Android `MapView` + `NavigationMapRoute` and iOS `NavigationMapView`, using the same native route cache as guidance. It supports panning/zooming, stop markers, alternatives, route selection and fitting the route. No Web Maps SDK is used, and the existing full-screen guidance API remains available without a preview.

Add an HTML placeholder with an explicit size, then pass its viewport rectangle:

```ts
const element = document.getElementById('route-map')!;
const bounds = () => {
  const r = element.getBoundingClientRect();
  return { x: r.left, y: r.top, width: r.width, height: r.height };
};
await NBNavigation.createMap({ apiKey, bounds: bounds() });
await NBNavigation.setMapStops({ stops });

const { routes } = await NBNavigation.planRoute({ apiKey, stops, profile: 'truck', truck });
await NBNavigation.showMapRoute({ routeId: routes[0].id });
const selection = await NBNavigation.addListener('mapRouteSelected', ({ routeId }) => {
  // Update your route cards / selected id for startNavigation.
});

const sync = () => {
  void NBNavigation.updateMap({ bounds: bounds() });
};
const observer = new ResizeObserver(sync);
observer.observe(element);
window.addEventListener('scroll', sync, true);
window.addEventListener('resize', sync);

// Before a settings sheet / HTML modal covers the map:
await NBNavigation.updateMap({ visible: false });
// After closing it:
await NBNavigation.updateMap({ bounds: bounds(), visible: true });

// When leaving/unmounting the page:
observer.disconnect();
window.removeEventListener('scroll', sync, true);
window.removeEventListener('resize', sync);
await selection.remove();
await NBNavigation.destroyMap();
```

The snippet assumes `apiKey`, `stops` and optional `truck` contain your application values. For keyboard resizing, also observe `window.visualViewport` where available, as the example does.

| Method | Options / result |
| --- | --- |
| `createMap` | `{ apiKey, bounds }`. One map per plugin; replaces the old map. Resolves after style load. Rejects on style/network failure or 30-second startup timeout. |
| `updateMap` | `{ bounds?, visible? }`. Bounds are finite **CSS pixels relative to the WebView viewport**, not document coordinates or physical pixels; width/height must be positive. |
| `setMapStops` | `{ stops }`. Show 2–12 ordered stop markers and clear drawn routes; `[]` clears the preview. Does not calculate routes or change the cached route ids. |
| `showMapRoute` | `{ routeId, fit?: boolean }`. Display cached alternatives with the selected route highlighted; `fit` defaults to true. |
| `addListener('mapRouteSelected', callback)` | Receives `{ routeId }` when a different route is selected on the native map. Remove the returned listener when unmounting. |
| `destroyMap` | Idempotently release the native preview. Guidance has a separate lifecycle. |

This is a native sibling overlay aligned with your HTML placeholder. It is not a DOM canvas and does **not** follow HTML `z-index`, `overflow`, transforms or CSS clipping. The native view clips to the host WebView and has rounded corners. Keep its region clear of HTML controls, explicitly synchronize bounds/visibility during layout changes, and hide it before showing overlapping HTML sheets. An opaque WebView background is supported; no transparent-page workaround is needed with this overlay implementation. On iOS, the preview follows the WebView when full-screen transitions change its UIKit parent. Native guidance temporarily suspends the preview and reattaches it after dismissal; the existing map, camera and route selection are retained.

The example uses a stable map area beside the trip panel (above it on phones), while the panel scrolls independently. It hides the native map for its settings sheet and full-screen navigation, then restores it. Coordinate editing clears old preview routes; the selected native route is reused for guidance. The supplied key is shared by the native SDK; do not use different keys simultaneously for preview and planning. Browsers explicitly reject native map calls with `UNAVAILABLE`.

## API

### `planRoute(options): Promise<PlanRouteResult>`

| Option | Type | Meaning |
| --- | --- | --- |
| `apiKey` | `string` | Key with Maps and Navigation access. |
| `stops` | `Stop[]` | 2–12 WGS84 points in visit order: origin, intermediate stops, destination. |
| `profile` | `RoutingProfile`, optional | `car` (default), `truck`, `bike`, `motorcycle`. |
| `avoid` | `Avoid[]`, optional | Multiple road/maneuver avoidances; omit or `[]` for no additional avoidances. |
| `truck` | `TruckOptions`, optional | Only accepted with `profile: 'truck'`; fields below. |
| `departureTime` | `number`, optional | Whole Unix epoch seconds, 1–2147483647. Omit to use the SDK's current-time handling. |
| `crossBorder` | `boolean`, optional | Whether an international crossing is expected. `false` adds an avoidance penalty; it does not prohibit crossings. Omit for SDK defaults. |
| `turnAngleRange` | `number`, optional | Whole degrees, 1–180; requires `avoid` to include `sharp_turn`. Omit for the service default. |
| `approaches` | `Approach[]`, optional | `unrestricted` or `curb`, one per non-origin stop, in order. Length must equal `stops.length - 1`. |
| `language` | `string`, optional | Two-letter instruction language such as `en`, `fr`, `zh`, or `zh-Hans`. SDK normalization/service language support applies. |
| `unit` | `RoutingUnit`, optional | `metric` or `imperial` for instruction/display distances. Omit for SDK defaults. |

`Stop`: `{ name: string; latitude: number; longitude: number }`.

Returns `{ routes: RouteSummary[] }`. Each summary contains `{ id: number; distance: number; duration: number }`, with meters and seconds. Both platforms request `alternatives=true`, `altcount=2`, `route_type=fastest`, `overview=full` and `geometry=polyline6`. Alternatives may not exist for every trip. All profiles use `option=flexible` on both platforms. Stop ordering is preserved and is not optimized.

Each new planning attempt clears the previous native route cache; failures do not leave an old route available. Treat route ids as temporary: they belong to the most recent plan in the current app process. Full geometry and turn instructions remain on the native side; the JS/native bridge does not transfer a large route JSON payload.

### Truck routing and avoidances

```ts
const { routes } = await NBNavigation.planRoute({
  apiKey: 'YOUR_NEXTBILLION_API_KEY',
  stops,
  profile: 'truck',
  avoid: ['toll', 'ferry', 'uturn'],
  truck: {
    size: { heightCm: 350, widthCm: 250, lengthCm: 1000 },
    weightKg: 20000,
    axleLoadTonnes: 8.5,
    hazmatTypes: ['general'],
    preferTruckRoutes: true,
    types: ['rigid_truck'],
  },
});
await NBNavigation.startNavigation({ routeId: routes[0].id, simulate: true });
```

| Truck field | Native API parameter | Units / rules |
| --- | --- | --- |
| `size` | `truck_size` | Whole centimeters, serialized **height,width,length**. All three required if `size` is provided. Height 1–1000, width/length 1–5000. |
| `weightKg` | `truck_weight` | Whole kilograms, 1–100000, including trailer and cargo. |
| `axleLoadTonnes` | `truck_axle_load` | Positive metric tonnes per axle, including trailer and cargo; decimals accepted. Android SDK stores this as Float. |
| `hazmatTypes` | `hazmat_type` | Array: `general`, `circumstantial`, `explosive`, `harmful_to_water`. |
| `preferTruckRoutes` | `prefer=truck_route` | Boolean, default false. Prefer truck-friendly roads. |
| `types` | `truck_type` | Array: `rigid_truck`, `semi_trailer`, `b_double`, `road_train`, `generic_truck`. Requires `preferTruckRoutes: true`. |

All truck fields are optional. Omitted fields use SDK/service defaults; they do not mean an unconstrained truck. Supply the actual loaded vehicle dimensions and weight. Clear a field by omitting it from a **new** plan; do not send 0 or `null`. Truck options with any other profile reject instead of being silently ignored.

`avoid` supports `toll`, `highway`, `ferry`, `sharp_turn`, `uturn`, `service_road`, `left_turn`, `right_turn`, `tunnel`. Duplicate values are removed. `bbox`, `geofence_id` and the literal `none` are not part of this wrapper's typed API; use `[]` to clear avoidances. Region support, service defaults and restriction behavior follow the [Navigation API](https://docs.nextbillion.ai/routing/navigation-api) and [SDK request parameters](https://docs.nextbillion.ai/routing/android-navigation-sdk/map-display-and-navigation).

Both native implementations validate enum values, numeric types, ranges and related fields before sending a request. Dimensions and weight use whole numbers because iOS SDK 4.1.1 exposes integer fields. The wrapper passes these settings into the native route's request options and starts navigation with that same route. Recalculate after changing a profile, avoidance or vehicle; `startNavigation` intentionally accepts only an existing route id and simulation flag.

### Optional route preferences and SDK defaults

```ts
const { routes } = await NBNavigation.planRoute({
  apiKey: 'YOUR_NEXTBILLION_API_KEY',
  stops, // The three-stop array from the usage example above.
  avoid: ['sharp_turn'],
  turnAngleRange: 120,
  departureTime: Date.parse('2030-01-01T10:00:00+08:00') / 1000, // Date/time chosen by the user.
  crossBorder: true,
  approaches: ['unrestricted', 'curb'], // Intermediate stop, then destination; no origin entry.
  language: 'fr',
  unit: 'imperial',
});
```

Every preference above is optional. Omit `departureTime`, `language` and `unit` to let each native SDK choose its own time, language and measurement system. The wrapper does not create a timestamp or force a locale/unit for missing fields. The demo's **SDK default** controls omit these fields rather than sending a fallback value. Clear a preference by omitting it from a fresh plan; explicit `null`, empty strings, invalid numbers and invalid enums reject.

`departureTime` uses **seconds**, not JavaScript milliseconds. Its maximum reflects Android SDK 3.1.0's signed 32-bit timestamp (19 January 2038); requests beyond it reject consistently on both platforms. Automatic in-navigation rerouting follows the SDK's time policy and is not a promise to keep a future planning timestamp frozen.

`crossBorder: true` removes the crossing penalty, while `false` prefers feasible routes with fewer crossings. Coverage follows the native service. A required crossing can be unavailable under restrictive settings; the service may return HTTP 422 with no feasible route. For `turnAngleRange = x`, the permitted turn angles are `[x, 360 - x]`; the option only takes effect with `avoid: ['sharp_turn', ...]`. The demo enables this field when **Sharp turns** is selected.

`approaches[i]` belongs to `stops[i + 1]`. Repeated values are preserved. `curb` requests arrival on the driving side of the road; `unrestricted` permits either side. Omission uses native defaults. The iOS SDK omits the wire parameter when all stops are unrestricted, which has equivalent routing semantics. When editing stops, keep this array aligned; the demo updates it on insertion/removal and clears it when changing sample trips.

Languages use the SDK's instruction-language mapping (including `zh` to `zh-Hans`); this does not translate every navigation UI label. Unit choices do not change truck input units (cm/kg/tonnes), and route summaries always use meters/seconds. Defaults can differ by platform: Android's SDK currently defaults to English/metric, while iOS uses its SDK locale and associated measurement system. The wrapper leaves these defaults untouched.

`planRoute` does not forward arbitrary SDK fields. Remaining settings outside this API include custom `route_type` (fixed `fastest`), alternative count (fixed `2`), geometry format (`polyline6`/`full`), `allow`, `road_info`, `bearings`, original-shape routing, and bbox/geofence avoidances. Use the documented camelCase wrapper fields rather than native request keys such as `departure_time` or `truck_size`.

Native tests cover explicit preferences, omission/default behavior, request JSON, iOS option copies, Android's route cache JSON, and invalid input. Route availability and geographic restrictions are evaluated by the routing service.

### `startNavigation(options): Promise<NavigationResult>`

| Option | Type | Meaning |
| --- | --- | --- |
| `routeId` | `number` | An id from the latest `planRoute` result. |
| `simulate` | `boolean`, optional | Default `true`; set `false` for device GPS navigation. |

Returns `{ reason: 'arrived' | 'cancelled' }` after the screen closes. Invalid route ids, route requests, permission failures and Android startup timeouts reject the promise. Keep only one planning/navigation operation active at a time. The navigation promise is the exit callback; no additional event subscription is required.

Both platforms retain the SDK arrival sheet at intermediate stops and the final destination. Arrival alone does not close the native screen or resolve `startNavigation`. The user can dismiss/continue in the SDK UI or explicitly end navigation. Closing after reaching the final destination returns `arrived`; exiting before final arrival returns `cancelled`. The embedded preview is restored only after the native navigation screen closes.

## Lifecycle and platform behavior

- Android hosts `NavigationView` in the plugin's private `NBNavigationActivity` and returns via Capacitor's Activity result callback. The private route cache file avoids Android Binder limits and is deleted on finish.
- iOS presents `NavigationViewController` full screen with `NBNavigationService`, and stops the service on return. The plugin enables `routeLineTracksTraversal` before presentation so the SDK updates the traversed portion of the route as navigation progresses.
- Android waits for the initial map style before initializing the navigation UI, authenticates HTTPS map resources only on `api.nextbillion.io`, and retains the transform for the plugin lifetime. It reports a startup error after 45 seconds without guidance progress.
- The wrapper does not store the API key in localStorage or plugin preferences. Native SDK/network resource caching is managed by the SDK. Debug Capacitor builds may log bridge parameters; use standard release logging settings in production.
- Backgrounding without process death preserves the pending navigation promise. OS process death discards route ids and the promise; production apps should persist their own trip id and replan after relaunch.
- Browsers/PWAs reject calls with `UNAVAILABLE`. They do not render a simulated native navigation screen.

## Build and distribute

From the repository root:

```sh
npm ci
npm run build
npm run pack:plugin
npm run verify:package
```

The archive contains the built JS/types, Swift package/source, Android library/source/manifest/consumer rules and documentation. It excludes the example, test credentials, app icons, app bundle identifiers and native build output.

The package uses the company npm organization `nextbillion-ai`. After archive verification, publish it with an npm account that has publishing access to that organization:

```sh
npm login --auth-type=web --registry=https://registry.npmjs.org/
npm publish ./artifacts/nextbillion-ai-navigation-capacitor-1.0.0.tgz --access public --tag latest --registry=https://registry.npmjs.org/
```

Run these commands from the repository root and complete the authentication challenge when prompted. The plugin's `publishConfig` selects the public npm registry and public access. Building and packing do not publish; use a new version for every subsequent release. The archive includes LICENSE and NOTICE.

## License

The Capacitor integration source code is licensed under the [BSD 3-Clause License](LICENSE), copyright (c) 2026, NextBillion.ai Pte. Ltd.

NextBillion.ai native SDKs, APIs, map data, and services remain subject to their applicable licenses, the [NextBillion.ai Terms of Service](https://nextbillion.ai/terms-of-service), and applicable customer agreements. This package's license does not grant access to or relicense those components or services. See [NOTICE](NOTICE) for the licensing scope and third-party notice.
