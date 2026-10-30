# First integration with the published Capacitor plugin

This guide provides a complete Plan → Select route → Start example for a Capacitor 7 app. Install `@nextbillion-ai/navigation-capacitor@1.0.1` and complete the [platform setup and permissions](../README.md#2-configure-navigation-permissions) first. Use SPM for iOS and JDK 21 for Android.

## Complete web UI example

This example uses plain TypeScript and DOM controls so it can be adapted to your framework. It provides two-point/multi-stop trips, all four profiles, sample truck restrictions, alternative selection, a simulation checkbox, disabled controls during operations and error/result handling. The API key stays in memory and is entered by the user for this demo.

Create `src/navigation-demo.ts`:

```ts
import { Capacitor } from '@capacitor/core';
import {
  NBNavigation,
  type PlanRouteOptions,
  type RouteSummary,
  type RoutingProfile,
  type Stop,
} from '@nextbillion-ai/navigation-capacitor';

export function mountNavigationDemo(root: HTMLElement) {
  root.innerHTML = `
    <fieldset id="navigation-controls">
      <label>API key <input id="navigation-key" type="password" autocomplete="off"></label>
      <label>Trip
        <select id="navigation-trip">
          <option value="direct">Origin and destination</option>
          <option value="delivery">Origin, pickup and destination</option>
        </select>
      </label>
      <label>Vehicle
        <select id="navigation-profile">
          <option value="car">Car</option>
          <option value="truck">Truck</option>
          <option value="bike">Bicycle</option>
          <option value="motorcycle">Motorcycle</option>
        </select>
      </label>
      <label><input id="navigation-simulate" type="checkbox" checked> Simulate drive</label>
      <button id="navigation-plan" type="button">Plan route</button>
      <label>Route <select id="navigation-route"></select></label>
      <button id="navigation-start" type="button">Start navigation</button>
    </fieldset>
    <p id="navigation-status" role="status"></p>
  `;
  const element = <T extends HTMLElement>(id: string) => {
    const found = root.querySelector<T>(`#${id}`);
    if (!found) throw new Error(`Missing control: ${id}`);
    return found;
  };
  const controls = element<HTMLFieldSetElement>('navigation-controls');
  const key = element<HTMLInputElement>('navigation-key');
  const trip = element<HTMLSelectElement>('navigation-trip');
  const profile = element<HTMLSelectElement>('navigation-profile');
  const simulate = element<HTMLInputElement>('navigation-simulate');
  const plan = element<HTMLButtonElement>('navigation-plan');
  const selection = element<HTMLSelectElement>('navigation-route');
  const start = element<HTMLButtonElement>('navigation-start');
  const status = element<HTMLParagraphElement>('navigation-status');
  const native = Capacitor.isNativePlatform();
  let routes: RouteSummary[] = [];
  let busy = false;

  const showError = (error: unknown) => {
    status.textContent = error instanceof Error ? error.message : String(error);
  };
  const refresh = () => {
    controls.disabled = busy || !native;
    plan.disabled = busy || !native || !key.value.trim();
    selection.disabled = busy || routes.length === 0;
    start.disabled = busy || !native || routes.length === 0;
  };
  const invalidate = () => {
    routes = [];
    selection.replaceChildren();
    status.textContent = 'Trip changed. Plan a new route.';
    refresh();
  };
  key.addEventListener('input', invalidate);
  trip.addEventListener('change', invalidate);
  profile.addEventListener('change', invalidate);

  const stopsForTrip = (): Stop[] => {
    const origin: Stop = { name: 'Start', latitude: 1.2834, longitude: 103.8607 };
    const destination: Stop = { name: 'Destination', latitude: 1.3048, longitude: 103.8318 };
    return trip.value === 'delivery'
      ? [origin, { name: 'Pickup', latitude: 1.2845, longitude: 103.8441 }, destination]
      : [origin, destination];
  };

  plan.addEventListener('click', async () => {
    if (busy) return;
    busy = true;
    routes = [];
    selection.replaceChildren();
    refresh();
    status.textContent = 'Requesting routes…';
    try {
      const options: PlanRouteOptions = {
        apiKey: key.value.trim(),
        stops: stopsForTrip(),
        profile: profile.value as RoutingProfile,
      };
      if (options.profile === 'truck') {
        options.avoid = ['toll', 'ferry'];
        options.truck = {
          size: { heightCm: 350, widthCm: 250, lengthCm: 1000 },
          weightKg: 20000,
          axleLoadTonnes: 8.5,
          hazmatTypes: ['general'],
          preferTruckRoutes: true,
          types: ['rigid_truck'],
        };
      }
      // Add user-selected optional preferences here; leave unset fields absent.
      const result = await NBNavigation.planRoute(options);
      routes = result.routes;
      for (const route of routes) {
        const label = `Route ${route.id + 1}: ${(route.distance / 1000).toFixed(1)} km, ${Math.ceil(route.duration / 60)} min`;
        selection.add(new Option(label, String(route.id)));
      }
      status.textContent = routes.length ? 'Choose a route, then press Start.' : 'No routes returned.';
    } catch (error) {
      showError(error);
    } finally {
      busy = false;
      refresh();
    }
  });

  start.addEventListener('click', async () => {
    if (busy) return;
    const selected = routes.find(route => route.id === Number(selection.value));
    if (!selected) return;
    busy = true;
    refresh();
    status.textContent = 'Opening navigation. Allow location access if prompted.';
    try {
      const result = await NBNavigation.startNavigation({
        routeId: selected.id,
        simulate: simulate.checked,
      });
      status.textContent = result.reason === 'arrived' ? 'Trip completed.' : 'Navigation closed.';
    } catch (error) {
      showError(error);
    } finally {
      busy = false;
      refresh();
    }
  });

  status.textContent = native
    ? 'Enter your API key and plan a route.'
    : 'Build and run the native iOS or Android app to use navigation.';
  refresh();
}
```

Add a container to your page:

```html
<div id="navigation-demo"></div>
```

Import and mount the module from your existing web entry point, after the container is available:

```ts
import { mountNavigationDemo } from './navigation-demo';

const container = document.getElementById('navigation-demo');
if (container) mountNavigationDemo(container);
```

For a component framework, mount after its view is ready and destroy/remove the component's controls on unmount. Keep one active planning/navigation operation per plugin. If you add other trip/configuration inputs, invalidate the selected route when they change. Switching simulation mode alone does not require replanning.

Replace the sample truck values with the actual vehicle configuration for production. The [main routing examples](../README.md#4-configure-routing) show avoids, departure time, border preference, approaches, language and units. The native SDK handles defaults when optional fields are omitted.

### Optional user-selected departure time

If your settings form has `<input type="datetime-local">`, convert its nonempty value to Unix seconds before planning. A local date/time is interpreted in the device's time zone:

```ts
export function departureTimeFromInput(value: string): number | undefined {
  if (!value) return undefined;
  const seconds = Math.floor(new Date(value).getTime() / 1000);
  if (!Number.isFinite(seconds) || seconds < 1 || seconds > 2147483647) {
    throw new Error('Choose a valid departure date within the supported range.');
  }
  return seconds;
}
```

Set `options.departureTime` only when the helper returns a number. For a blank input, omit the property and let the SDK handle current time. Do not substitute `Date.now()` or send JavaScript milliseconds.

## Android notification permission example

The navigation plugin already declares `POST_NOTIFICATIONS`, but it does not request that runtime permission. On Android 13+, your app can request it to show the SDK's navigation notifications in the notification drawer. This permission does not replace location permission and its denial alone does not block foreground navigation.

If your app already has a notification-permission flow, reuse it. Otherwise this optional **host Activity example** offers an explanation once, before requesting the Android permission. Use your own package name and merge the code with your existing `MainActivity`; navigation plugin registration remains automatic. The sample asks during app onboarding. In a production app, place the explanation at the point where the user enables navigation notifications.

```java
package com.example.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private final ActivityResultLauncher<String> notifications =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            // Notification visibility follows the user's choice. Location is requested at Start.
        });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 33 || savedInstanceState != null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        if (getPreferences(MODE_PRIVATE).getBoolean("navigationNotificationsOffered", false)) return;
        getPreferences(MODE_PRIVATE).edit().putBoolean("navigationNotificationsOffered", true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Navigation notifications")
            .setMessage("Allow notifications to see navigation guidance while using other apps.")
            .setPositiveButton("Continue", (dialog, which) ->
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS))
            .setNegativeButton("Not now", null)
            .show();
    }
}
```

After adding native code, rebuild the Android app. If notifications or the SDK's notification channel are disabled, the user can enable them in system Settings. See [Android notification permissions](https://developer.android.com/develop/ui/views/notifications/notification-permission).

Basic foreground-started guidance does not require adding `ACCESS_BACKGROUND_LOCATION`. The plugin does not implement launching navigation from background jobs or an Always/background permission escalation flow. It also exposes no JavaScript notification-permission method. Use [the platform permission table](../README.md#2-configure-navigation-permissions) to distinguish manifest declarations from runtime requests.

## Build and try the complete flow

1. Build the web app with your usual command, then run `npx cap sync`.
2. Open `npx cap open android` or `npx cap open ios`, select a target device, configure signing if needed and run.
3. Enter a NextBillion key with Maps and Navigation access. Leave **Simulate drive** selected for the first test.
4. Select the two-point or delivery trip and a profile. Press **Plan route** and inspect the returned alternatives.
5. Select a route and press **Start navigation**. Grant location access. On Android choose Precise; on iOS choose Allow While Using App.
6. Exit before final arrival to receive `cancelled`, or reach the destination and press the native Done control to receive `arrived`. Arrival alone leaves the confirmation UI and promise open.
7. Replace the sample stops with your own trip, turn simulation off and test with device GPS. Keep device Location Services enabled.

The SDK owns GPS updates during guidance. The wrapper does not expose a device-position query; if your planner needs the current position as an origin before routing, obtain it through your app's existing location integration and pass it as the first stop.

## Common integration mistakes

| Issue | Correction |
| --- | --- |
| Calling from a web-only preview | Run a native build; the sample disables navigation controls in browsers |
| Assuming `planRoute` grants location access | Device location is requested at `startNavigation`, including simulation |
| Treating manifest entries as granted runtime permissions | Grant location at Start and handle notification permission in the host |
| Requesting Always/background access for every user | Use the standard foreground-started flow; separate additional host location features |
| Using old route ids after editing settings | Clear the selection and call `planRoute` again |
| Sending truck fields with `profile: 'car'` | Supply truck restrictions only with `profile: 'truck'` |
| Providing meters/tonnes for truck dimensions/weight | Use whole centimeters and whole kilograms; axle load alone uses tonnes |
| Sending an approach for the origin | Supply exactly one entry per non-origin stop |
| Starting navigation repeatedly while its promise is pending | Await explicit user exit before allowing another navigation operation |

For an embedded map, use the [native preview guide](../plugin/README.md#native-map-inside-your-page) and the repository's [map controller](../example/src/features/map/preview-map.ts). The larger [example app](../example/README.md) provides editable coordinates and all routing settings.
