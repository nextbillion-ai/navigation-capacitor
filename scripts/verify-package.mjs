import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const artifacts = path.join(root, 'artifacts');
mkdirSync(artifacts, { recursive: true });
const env = { ...process.env, npm_config_update_notifier: 'false', npm_config_audit: 'false' };
const run = (args, cwd = root, capture = false) => execFileSync('npm', args, {
  cwd, env, encoding: 'utf8', stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit',
});

run(['run', 'build']);
const [archive] = JSON.parse(run(['pack', '--workspace', 'plugin', '--ignore-scripts', '--json', '--pack-destination', artifacts], root, true));
const packed = new Set(archive.files.map(file => file.path));
for (const required of [
  'package.json', 'Package.swift', 'README.md', 'LICENSE', 'NOTICE', 'dist/esm/index.js', 'dist/esm/index.d.ts',
  'dist/esm/definitions.d.ts', 'dist/esm/web.js', 'android/build.gradle',
  'android/consumer-rules.pro', 'android/src/main/AndroidManifest.xml',
  'android/src/main/java/ai/nextbillion/navigation/capacitor/NBNavigationPlugin.java',
  'android/src/main/java/ai/nextbillion/navigation/capacitor/NBNavigationActivity.java',
  'ios/Sources/NBNavigationCapacitor/NBNavigationPlugin.swift',
  'ios/Sources/NBNavigationCapacitor/RouteConfiguration.swift',
  'ios/Sources/NBNavigationCapacitor/EmbeddedMap.swift',
  'android/src/main/java/ai/nextbillion/navigation/capacitor/EmbeddedMap.java',
  'android/src/main/java/ai/nextbillion/navigation/capacitor/RouteConfiguration.java',
]) assert(packed.has(required), `Archive missing ${required}`);
assert(![...packed].some(file => /(^|\/)(example|node_modules|build|local.properties)(\/|$)/.test(file)), 'Archive contains host/build files');

const consumer = mkdtempSync(path.join(tmpdir(), 'nextbillion-ai-navigation-capacitor-consumer-'));
const example = path.join(root, 'example');
const manifest = JSON.parse(readFileSync(path.join(example, 'package.json'), 'utf8'));
manifest.name = 'standalone-capacitor-navigation-consumer';
manifest.dependencies['@nextbillion-ai/navigation-capacitor'] = `file:${path.join(artifacts, archive.filename)}`;
writeFileSync(path.join(consumer, 'package.json'), `${JSON.stringify(manifest, null, 2)}\n`);
for (const item of ['src', 'index.html', 'tsconfig.json', 'vite.config.ts', 'capacitor.config.json', 'android', 'ios']) {
  const source = path.join(example, item);
  cpSync(source, path.join(consumer, item), {
    recursive: true,
    filter: filename => !/(^|\/)(node_modules|build|\.gradle|Pods|DerivedData|local.properties)(\/|$)/.test(path.relative(source, filename)),
  });
}
run(['install', '--offline', '--ignore-scripts'], consumer);
run(['run', 'build'], consumer);
run(['run', 'sync'], consumer);

const installed = path.join(consumer, 'node_modules/@nextbillion-ai/navigation-capacitor');
const installedManifest = JSON.parse(readFileSync(path.join(installed, 'package.json'), 'utf8'));
assert.equal(installedManifest.license, 'BSD-3-Clause', 'Installed license metadata is incorrect');
for (const file of ['LICENSE', 'NOTICE']) {
  assert.equal(readFileSync(path.join(installed, file), 'utf8'), readFileSync(path.join(root, file), 'utf8'), `Installed ${file} differs from the repository notice`);
}
assert(readFileSync(path.join(installed, 'NOTICE'), 'utf8').includes('https://nextbillion.ai/terms-of-service'), 'Installed notice omits the service terms');
assert(existsSync(path.join(installed, 'Package.swift')), 'Installed archive missing Swift package');
const androidPlugins = JSON.parse(readFileSync(path.join(consumer, 'android/app/src/main/assets/capacitor.plugins.json')));
assert(androidPlugins.some(plugin => plugin.classpath === 'ai.nextbillion.navigation.capacitor.NBNavigationPlugin'), 'Android auto registration failed');
const iosConfig = JSON.parse(readFileSync(path.join(consumer, 'ios/App/App/capacitor.config.json')));
assert(iosConfig.packageClassList.includes('NBNavigationPlugin'), 'iOS auto registration failed');
const spm = readFileSync(path.join(consumer, 'ios/App/CapApp-SPM/Package.swift'), 'utf8');
assert(spm.includes('NextbillionAiNavigationCapacitor'), 'SPM product not integrated');
assert(spm.includes('node_modules/@nextbillion-ai/navigation-capacitor'), 'SPM still uses a workspace source path');
assert(!readFileSync(path.join(consumer, 'android/app/build.gradle'), 'utf8').includes('nb-navigation-android'), 'Host declares a direct navigation SDK dependency');
assert(!readFileSync(path.join(consumer, 'ios/App/App.xcodeproj/project.pbxproj'), 'utf8').includes('navigation-distribution'), 'Host declares a direct navigation SDK dependency');

const { NBNavigation, NativeNavigation } = await import(pathToFileURL(path.join(installed, 'dist/esm/index.js')));
assert.equal(NBNavigation, NativeNavigation);
for (const request of [
  () => NBNavigation.planRoute({ apiKey: 'not-used-on-web', stops: [] }),
  () => NBNavigation.startNavigation({ routeId: 0 }),
  () => NBNavigation.createMap({ apiKey: 'not-used-on-web', bounds: { x: 0, y: 0, width: 300, height: 200 } }),
  () => NBNavigation.updateMap({ visible: false }),
  () => NBNavigation.setMapStops({ stops: [] }),
  () => NBNavigation.showMapRoute({ routeId: 0 }),
  () => NBNavigation.destroyMap(),
]) await assert.rejects(request, error => error.code === 'UNAVAILABLE');

const result = {
  package: archive.name, version: archive.version, archive: archive.filename,
  integrity: archive.integrity, size: archive.size, unpackedSize: archive.unpackedSize,
  consumerDirectory: consumer,
  checks: ['archive allowlist', 'license metadata and notices', 'standalone archive install', 'consumer types/web build', 'Android auto registration', 'iOS auto registration', 'SPM package dependency', 'no direct host SDK dependency', 'web unavailable behavior'],
};
writeFileSync(path.join(artifacts, 'package-verification.json'), `${JSON.stringify(result, null, 2)}\n`);
console.log(`\nPackage verification passed: ${archive.filename}\nStandalone consumer: ${consumer}`);
