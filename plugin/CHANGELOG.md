# Changelog

## Unreleased

- Upgrade the Android Navigation SDK from 3.1.0 to 3.1.2 and the iOS Navigation SDK from 4.1.1 to 4.1.4.
- Refresh the example and routing-test Swift package lockfiles, SDK version documentation and native routing configuration audit.
- Fix intermittent Android E2E preview assertions by waiting up to five seconds for asynchronous native map recovery after navigation, while retaining visibility, layout and route-geometry checks.
- Verify routing configuration, truck/avoid options, simulated guidance, user-confirmed arrival and preview recovery against the updated SDKs on physical devices and simulators.

- Use the company npm scope `@nextbillion-ai/navigation-capacitor`; align example imports, native package integration and distribution archives with the organization name.

- Keep the native arrival sheet open on Android and iOS until the user ends navigation. Resolve `arrived` only after user exit following final arrival; earlier exits remain `cancelled`.

- Show a red destination pin in the Android embedded route preview using the native SDK waypoint style, with padding to keep the pin visible.

- Dismiss the entire iOS navigation hierarchy from the host presenter after user-confirmed exit, including when the SDK has an arrival sheet. Add opt-in iPhone E2E coverage for route preferences, guidance, traversal styling and preview recovery.

- Add optional departure time, border-crossing preference, turn-angle range, per-stop approaches, instruction language and measurement units on both platforms. Omitted fields preserve native SDK defaults.
- Add example settings for these preferences, including local date/time entry, default reset and arrival-side alignment during stop edits.
- Align native fastest/alternative defaults and declare the iOS Cordova dependency for independent plugin builds.
- Extend released-SDK request, route-cache, option-copy and example-form verification, with an opt-in real-device route-options E2E suite.
- Reject zero turn angles before routing (the service accepts 1–180 degrees), and explain Android HTTP 422 no-route responses with actionable guidance.

## 1.0.0 — 2026-10-23

- License the Capacitor integration and example source code under BSD-3-Clause, and include LICENSE and NOTICE with SDK/service terms in the npm archive.
- Rename the npm package to `@nextbillion-ai/navigation-capacitor`, the archive to `nextbillion-ai-navigation-capacitor-1.0.0.tgz`, and the Swift package product to `NextbillionAiNavigationCapacitor`.
- Version the existing native navigation, embedded map, routing profiles, avoidances and truck configuration APIs as 1.0.0.
- Update the distribution archive references and package metadata.
- Enable iOS `routeLineTracksTraversal` before presenting native guidance so traversed route styling follows navigation progress.
- Fix the iOS embedded preview disappearing after guidance exits: reattach it to the WebView’s current container and restore the map renderer’s visibility after dismissal.

## 0.3.0 — 2026-10-23

- Add a real native route-preview map inside a Capacitor page on iOS and Android.
- Add map creation, CSS viewport bounds/visibility updates, ordered stop markers, alternative route display/selection, selection events and explicit cleanup.
- Forward Android map lifecycle and keep the existing full-screen guidance workflow.
- Redesign the example around the map and trip panel; add transactional settings for connection, vehicle, avoidances and truck parameters, with vehicle presets and clear unit labels.

## 0.2.0 — 2026-10-23

- Add car, truck, bike and motorcycle profiles using Flexible routing on both platforms.
- Add typed multi-select avoidances and truck dimensions, gross weight, axle load, hazardous cargo, road preference and vehicle types.
- Validate configurations at both native bridge boundaries, with explicit cm/kg/tonne units and related-field errors.
- Extend the code example with routing controls, configuration invalidation and Car/Truck integration coverage.

## 0.1.0 — 2026-10-23

- Extract the Capacitor demo's TypeScript contract and native implementations into an installable npm plugin.
- Automatically register the native plugin and merge the Android navigation Activity and permissions.
- Declare iOS Navigation SDK 4.1.1 through Swift Package Manager and Android Navigation SDK 3.1.0 through Maven.
- Export `NativeNavigation` and `NBNavigation`, typed route/navigation results, and an explicit unsupported-browser implementation.
- Preserve ordered stops, alternative route selection, simulation/GPS guidance, cancellation and automatic final-arrival return.
