# Maintainer guide

Customers install the published npm package using the [main installation guide](../README.md). This document covers repository development, local archives and release maintenance.

## Repository layout

```text
plugin/       Distributable TypeScript API, iOS/Android bridge and native dependencies
example/      Development app using the local plugin
scripts/      Standalone archive-install verification
artifacts/    Generated local archives and verification metadata; ignored by Git
docs/         Integration, architecture and maintenance guides
```

Both `plugin/` and `example/` are npm workspaces. The example depends on `file:../plugin`, so npm links the package name to local plugin sources. The root lockfile records this workspace link. Customer apps install the package from npm as described in the integration guide.

## Build

Run from the repository root:

```sh
npm ci
npm run build
npm run sync
```

`npm run build` compiles the local plugin and then the example. `npm run build:plugin` builds only the plugin. `npm run dev` builds the plugin once before serving the browser preview; rebuild the plugin after changing its TypeScript sources.

`npm run sync` builds both workspaces and synchronizes Android/iOS. Generated native references point to `plugin/`. After changing native plugin code, synchronize and rebuild the app. `npm run android` and `npm run ios` synchronize and open the corresponding IDE. See the [example guide](../example/README.md) for native builds and device tests.

## Build and verify a local archive

```sh
mkdir -p artifacts
npm run pack:plugin
npm run verify:package
```

Packing runs the plugin's `prepack` build and creates `artifacts/nextbillion-ai-navigation-capacitor-<version>.tgz`. It includes built JS/types, Swift sources/package metadata, Android sources/manifest/resources and licensing documents. The package allowlist excludes example code, device-test output and credentials.

`verify:package` builds the local plugin and example, packs the plugin, installs the actual archive in a temporary standalone consumer, builds that consumer and synchronizes Android/iOS. It checks license metadata/notices, registration, Swift package integration, absence of direct host SDK declarations and unsupported-browser behavior. The repository example keeps its local plugin dependency. Results are saved to the ignored `artifacts/package-verification.json`.

Consumers can also install a delivered archive from their Capacitor app directory:

```sh
npm install /path/to/nextbillion-ai-navigation-capacitor-VERSION.tgz
npx cap sync
```

Replace `VERSION` with the archive's actual version.

## Publish a release

The npm package is `@nextbillion-ai/navigation-capacitor`, owned by the `nextbillion-ai` organization. Publishing requires an account with access to that organization and completion of npm's authentication challenge. The plugin's `publishConfig` selects public access and the public npm registry.

1. Set an unused version in `plugin/package.json`, update `plugin/CHANGELOG.md`, and run `npm install` at the repository root to refresh the lockfile. Already published name/version combinations cannot be overwritten.
2. Run `npm run verify:package` and the appropriate native/device checks. Review the generated archive contents and retain credentials only in local runtime configuration.
3. Commit the release sources, then publish the verified archive:

   ```sh
   npm login --auth-type=web --registry=https://registry.npmjs.org/
   npm publish ./artifacts/nextbillion-ai-navigation-capacitor-VERSION.tgz --access public --tag latest --registry=https://registry.npmjs.org/
   ```

   Replace `VERSION` with the verified release version. Complete the browser or terminal challenge when prompted. Building, packing and verification do not publish a package.

4. Confirm the version can be fetched from npm:

   ```sh
   npm view @nextbillion-ai/navigation-capacitor@VERSION version dist.integrity --registry=https://registry.npmjs.org/
   ```

   A newly uploaded release may take time to become available. Check availability before updating consumers. See [npm publish-time scanning](https://github.blog/changelog/2026-07-28-npm-publish-time-malware-scanning-and-dual-use-metadata/).

5. In a separate Capacitor app outside this repository's workspaces, install the published version and synchronize its native projects:

   ```sh
   npm install @nextbillion-ai/navigation-capacitor@VERSION --save-exact --registry=https://registry.npmjs.org/
   npm run build
   npx cap sync
   ```

   Replace `VERSION` with the release version. Follow the [integration guide](getting-started.md) for host permissions and a runnable planning/navigation UI, then verify routing and navigation on a device. Keep `example/` linked to the local plugin for repository development.

6. Update installation/version references in the customer documentation and commit the release metadata and lockfile changes.

API keys are supplied at runtime. Local reports, logs and screenshots belong under the ignored `docs/verification/` directory. See the [example test instructions](../example/README.md#device-integration-test) for Android and [iOS E2E](../example/README.md#ios-live-e2e).
