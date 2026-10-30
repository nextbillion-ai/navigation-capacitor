# NextBillion Navigation for Capacitor

Add native turn-by-turn navigation to a Capacitor app on iOS and Android. Request routes from an origin, optional stops and a destination, let the driver select a route, and open the NextBillion native navigation screen. When the driver closes navigation, your app resumes. A native map preview inside your page is optional.

The npm package is [@nextbillion-ai/navigation-capacitor](https://www.npmjs.com/package/@nextbillion-ai/navigation-capacitor). This source release is **1.0.1**.

Follow steps 1–5 below for a first integration. The [complete button-based example](docs/getting-started.md) adds a working Plan → Select route → Start flow and an optional Android notification-permission implementation.

## Requirements

| Component | Requirement |
| --- | --- |
| Capacitor | 7.x; iOS SPM requires 7.6.9 or later within 7.x |
| Node.js | 20+ |
| iOS | iOS 14+, macOS with Xcode, Swift Package Manager (SPM) |
| Android | API 23+, compile SDK 35, JDK 21 and Android Studio |
| NextBillion API key | Maps and Navigation access enabled |

Keep Capacitor Core, CLI and native platform versions aligned. See [Capacitor 7 environment setup](https://capacitorjs.com/docs/v7/getting-started/environment-setup). The plugin supplies iOS Navigation SDK **4.1.4** and Android Navigation SDK **3.1.2**, and Capacitor registers the bridge during synchronization.

## 1. Install from npm

In **your existing Capacitor app directory**:

```sh
npm install @nextbillion-ai/navigation-capacitor@1.0.1
npm run build
npx cap sync
```

Use your own web build command if different. If your iOS project uses CocoaPods, migrate to SPM as described below before synchronization. If a native platform has not been added yet, use the platform setup commands below. After installing or upgrading a native plugin, build the native app again. See the [Capacitor workflow](https://capacitorjs.com/docs/v7/basics/workflow).

## 2. Configure navigation permissions

Route calculation from supplied coordinates does not request device location permission. `startNavigation` requests location permission automatically on both platforms, **including simulated navigation**. Call it from your Start button while the app is visible. Version 1.0.1 has no separate `NBNavigation.requestPermissions()` API.

### iOS setup

For an app without an iOS platform:

```sh
npm install @capacitor/ios@7
npm run build
npx cap add ios --packagemanager SPM
npx cap sync ios
```

Align the installed iOS version with the app's other Capacitor packages, using **7.6.9 or later within 7.x**. Existing CocoaPods projects must follow the [Capacitor SPM migration guide](https://capacitorjs.com/docs/v7/ios/spm). `CapApp-SPM` manages the plugin and SDK dependencies.

Add this entry inside the existing `<dict>` in `ios/App/App/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Your location is used to guide you along your selected route.</string>
```

| Setting | When needed | Who handles it |
| --- | --- | --- |
| `NSLocationWhenInUseUsageDescription` | Starting native navigation | Your app supplies the purpose text; the plugin requests When In Use authorization |
| Precise Location | Recommended for accurate GPS guidance | The user enables it in the system permission/settings UI |
| Background Modes → Location updates | Continuing active guidance while the app is in the background | Your app enables the capability; the native SDK manages location updates |
| Background Modes → Audio | Spoken guidance while the app is in the background | Your app enables the capability; the SDK manages speech |

For background guidance, select the app target in Xcode → **Signing & Capabilities → Background Modes**, enable **Location updates** and **Audio**, and merge these values with any existing background modes:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>location</string>
    <string>audio</string>
</array>
```

On first Start, choose **Allow While Using App**. For the standard foreground-started navigation flow, When In Use authorization is the plugin's permission flow; it does not request Always authorization. Adding an Always usage-description key alone does not request that permission. Background capability configuration is described in [Apple's location documentation](https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates).

If location access was denied or restricted, navigation rejects. Ask the user to enable location for your app in system Settings, then retry from the app. Location Services must also be enabled on the device.

Open Xcode, set your signing team and run the app:

```sh
npx cap open ios
```

### Android setup

For an app without an Android platform:

```sh
npm install @capacitor/android@7
npm run build
npx cap add android
npx cap sync android
```

Align Android with the app's other Capacitor versions. Use JDK 21 for Gradle and ensure the host repositories include `google()` and `mavenCentral()`.

These declarations are **already merged from the plugin** into your app's manifest. You can inspect them in Android Studio → **Merged Manifest**; copying them into your own manifest is normally unnecessary:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

| Permission / declaration | Purpose | Runtime handling |
| --- | --- | --- |
| `INTERNET` | Route and map requests | Manifest only |
| `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | Device position for guidance | Requested together by the plugin at Start; choose **Precise** and **While using the app** |
| `FOREGROUND_SERVICE` | Active navigation service | Manifest only; managed by the SDK |
| `FOREGROUND_SERVICE_LOCATION` | Location foreground-service type on Android 14+ | Manifest only; granted location access is still required |
| `POST_NOTIFICATIONS` | Show navigation notifications on Android 13+ | Declared by the plugin; your host app requests it |
| Service type `location` | Identify the SDK's navigation foreground service | Supplied by the native SDK manifest |

Version 1.0.1 requires fine location for Android guidance. If only approximate access is granted or permission is denied, Start rejects. The user can enable **Location → Precise location** in your app's system settings and retry. See [Android location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).

For notification visibility on Android 13+, use your app's notification-permission flow or the [complete Java host example](docs/getting-started.md#android-notification-permission-example). `POST_NOTIFICATIONS` controls notification visibility; denial does not itself prohibit starting a foreground service. See [Android notification permissions](https://developer.android.com/develop/ui/views/notifications/notification-permission).

Start navigation from a visible Activity. The SDK supplies the location foreground service, but Android restricts starting it from the background. This plugin does not declare/request `ACCESS_BACKGROUND_LOCATION` or implement navigation launch from background jobs. See [foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

```sh
npx cap open android
```

## 3. Request a route

Pass your API key at runtime. The following module supports both an origin/destination trip and an ordered multi-stop trip:

```ts
import { NBNavigation, type Stop } from '@nextbillion-ai/navigation-capacitor';

const origin: Stop = { name: 'Start', latitude: 1.2834, longitude: 103.8607 };
const destination: Stop = { name: 'Destination', latitude: 1.3048, longitude: 103.8318 };

export const originDestinationStops: Stop[] = [origin, destination];

export const deliveryStops: Stop[] = [
  origin,
  { name: 'Pickup', latitude: 1.2845, longitude: 103.8441 },
  destination,
];

export async function requestRoutes(apiKey: string, stops: Stop[]) {
  const { routes } = await NBNavigation.planRoute({
    apiKey,
    stops,
    profile: 'car',
  });
  return routes;
}
```

Call `requestRoutes(apiKey, originDestinationStops)` for two points, or `requestRoutes(apiKey, deliveryStops)` for a trip with an intermediate stop. Replace the Singapore sample coordinates with your trip. Use **2–12 WGS84 points**, in visit order; route optimization is not performed.

Each returned summary is `{ id, distance, duration }`. Distance is in **meters** and duration in **seconds**. Present the summaries in your UI and retain the driver's chosen `id`. Alternatives are requested, but some trips have only one route. Geometry and guidance data remain native.

## 4. Configure routing

### Profiles, avoids and truck settings

This function shows a complete truck request. Change `profile` to `car`, `bike` or `motorcycle` for those vehicle types and omit the `truck` object:

```ts
import {
  NBNavigation,
  type PlanRouteOptions,
  type Stop,
} from '@nextbillion-ai/navigation-capacitor';

export async function requestTruckRoutes(apiKey: string, stops: Stop[]) {
  const options: PlanRouteOptions = {
    apiKey,
    stops,
    profile: 'truck',
    avoid: ['toll', 'ferry', 'sharp_turn'],
    turnAngleRange: 120,
    truck: {
      size: { heightCm: 350, widthCm: 250, lengthCm: 1000 },
      weightKg: 20000,
      axleLoadTonnes: 8.5,
      hazmatTypes: ['general'],
      preferTruckRoutes: true,
      types: ['rigid_truck'],
    },
  };
  return NBNavigation.planRoute(options);
}
```

| Field | Values / units |
| --- | --- |
| `profile` | `car` (default), `truck`, `bike`, `motorcycle` |
| `avoid` | `toll`, `highway`, `ferry`, `sharp_turn`, `uturn`, `service_road`, `left_turn`, `right_turn`, `tunnel` |
| `truck.size` | Supply all three dimensions in whole **centimeters** |
| `truck.weightKg` | Loaded weight in whole **kilograms**, 1–100000 |
| `truck.axleLoadTonnes` | Positive metric **tonnes per axle**, including decimals |
| `truck.hazmatTypes` | `general`, `circumstantial`, `explosive`, `harmful_to_water` |
| `truck.types` | `rigid_truck`, `semi_trailer`, `b_double`, `road_train`, `generic_truck`; requires `preferTruckRoutes: true` |
| `turnAngleRange` | Whole degrees 1–180; requires `sharp_turn` in `avoid` |

Use the vehicle's actual loaded dimensions and weight. All truck restrictions are optional and require `profile: 'truck'`. An empty `avoid` array clears additional avoidances for a new plan.

### Departure time, borders, approaches, language and units

Optional fields are supplied on the same `planRoute` call:

```ts
import { NBNavigation, type Stop } from '@nextbillion-ai/navigation-capacitor';

export async function requestConfiguredRoutes(
  apiKey: string,
  stops: Stop[],
  departureTime?: number,
) {
  return NBNavigation.planRoute({
    apiKey,
    stops,
    profile: 'car',
    crossBorder: true,
    approaches: stops.slice(1).map(() => 'curb'),
    language: 'fr',
    unit: 'imperial',
    ...(departureTime === undefined ? {} : { departureTime }),
  });
}
```

- `departureTime` is a **whole Unix timestamp in seconds**, 1–2147483647. Convert a user-selected date with `Math.floor(selectedDate.getTime() / 1000)`. Omit it when the user has not selected a date; the wrapper leaves current-time handling to the SDK.
- `crossBorder` is optional. `true` removes the crossing penalty; `false` prefers fewer crossings and is not an absolute ban. A route may be unavailable under restrictive settings.
- `approaches[i]` applies to `stops[i + 1]`: intermediate stops, then destination. Its length must be `stops.length - 1`. Use `curb` for arrival on the driving side, or `unrestricted` for either side. Preserve order and duplicates when editing stops.
- `language` accepts lowercase two-letter instruction codes, such as `en`, `fr`, `zh`, or `zh-Hans`. `unit` accepts `metric` or `imperial`. Omit them for each SDK's own defaults. Truck input units and numeric route summaries do not change.

The samples demonstrate explicit user preferences; omit fields that the user leaves at default. Avoid sending `null`, empty strings, milliseconds or zero-valued vehicle restrictions. See the [full API reference](plugin/README.md#api) for exact limits and native defaults.

## 5. Select a route and start navigation

Call this helper from your Start button with a summary returned by the latest route request. Pass `true` for a first simulated test or `false` for device-GPS guidance:

```ts
import { Capacitor } from '@capacitor/core';
import {
  NBNavigation,
  type NavigationResult,
  type RouteSummary,
} from '@nextbillion-ai/navigation-capacitor';

export async function startSelectedRoute(
  route: RouteSummary,
  simulate: boolean,
  showStatus: (message: string) => void,
): Promise<NavigationResult | null> {
  try {
    if (!Capacitor.isNativePlatform()) {
      throw new Error('Run the installed iOS or Android app to navigate.');
    }
    showStatus('Opening navigation. Allow location access if prompted.');
    const result = await NBNavigation.startNavigation({
      routeId: route.id,
      simulate,
    });
    showStatus(result.reason === 'arrived' ? 'Trip completed.' : 'Navigation closed.');
    return result;
  } catch (error) {
    showStatus(error instanceof Error ? error.message : String(error));
    return null;
  }
}
```

The plugin opens a separate full-screen native navigation UI. Arrival keeps the SDK confirmation sheet open until the user decides to end navigation. `startNavigation` remains pending until the native screen closes, then resolves with `arrived` after final arrival or `cancelled` after an earlier exit. Intermediate stops retain the SDK's Continue UI.

Route ids belong to the latest plan in the current process. Disable conflicting actions during planning/navigation, clear your route selection when inputs change, and replan before starting. `startNavigation` uses the already planned native route; it does not accept a second set of route settings.

For actual buttons, alternative selection and a simulation checkbox, copy the [complete integration example](docs/getting-started.md#complete-web-ui-example).

## Optional native map preview

Full-screen guidance works without an embedded map. For a preview, use `createMap`, `setMapStops`, `showMapRoute` and `updateMap` with the same API key. Keep its bounds synchronized, hide it before overlapping HTML modals, and call `destroyMap` on unmount. See the [native map integration guide](plugin/README.md#native-map-inside-your-page).

## First-run checks and common errors

After changing web code or native configuration, run your web build and `npx cap sync`, then rebuild/run in Xcode or Android Studio. Enter a valid key, request the sample route, select a result, start with simulation enabled, grant location access and exit the native screen. Repeat with `simulate: false` and your own trip coordinates for GPS guidance.

| Symptom | Action |
| --- | --- |
| `UNAVAILABLE` | Run the installed native app; browser/PWA navigation is unsupported |
| Location permission error | Enable device Location Services and your app's location access; Android also needs Precise access |
| `Generate routes before starting navigation` | Request a new route and use an id from the latest response |
| `Another operation is active` | Await the current request/navigation result and disable conflicting buttons |
| Route request failure / HTTP 422 | Check key access, coordinates and route restrictions; clear infeasible restrictions and replan |
| No Android navigation notification | Check Android 13+ notification permission and notification-channel settings |
| iOS dependency installation fails | Confirm SPM, aligned Capacitor versions and the iOS setup above |

## Run the example app

The repository example uses the **local plugin in `plugin/`** for development. It includes editable stops, native route preview, route selection and grouped routing settings. To integrate the published package into your own app, follow [Install from npm](#1-install-from-npm).

```sh
git clone https://github.com/nextbillion-ai/navigation-capacitor.git
cd navigation-capacitor
npm ci
npm run android
# Or: npm run ios
```

These scripts build/synchronize the example and open the native IDE. Choose a device and run. In **Settings → Connection**, enter your Maps + Navigation key and apply it. Select a sample trip, generate routes and start navigation. Simulation is enabled by default.

See the [example guide](example/README.md) for signing, native builds and E2E tests. `npm run verify:package` separately verifies a distributable archive in a standalone consumer.

## Documentation and development

- [Complete integration example and notification permissions](docs/getting-started.md)
- [Plugin API and native map guide](plugin/README.md)
- [Native routing configuration mappings](docs/routing-configuration.md)
- [Plugin architecture](docs/plugin-architecture.md)
- [Maintainer guide](docs/maintaining.md)
- [Changelog](plugin/CHANGELOG.md)

Plugin sources live in `plugin/` and the example app in `example/`. Both are npm workspaces; the example links to `file:../plugin`. Root build and sync scripts build the local plugin before the example. Customers install the published npm package as shown above.

## License

The Capacitor integration and example source code are licensed under the [BSD 3-Clause License](LICENSE), copyright (c) 2026, NextBillion.ai Pte. Ltd.

NextBillion.ai native SDKs, APIs, map data, and services remain subject to their applicable licenses, the [NextBillion.ai Terms of Service](https://nextbillion.ai/terms-of-service), and applicable customer agreements. This repository's license does not grant access to or relicense those components or services. See [NOTICE](NOTICE) for the licensing scope and third-party notice.
