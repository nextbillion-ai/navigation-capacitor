import { Capacitor } from '@capacitor/core';
import { MapPreview } from './features/map/preview-map';
import { SettingsController } from './features/settings/controller';
import { TripController } from './features/trip/controller';
import { el, errorMessage } from './shared/dom';
import { createAppState } from './state';
import { mountPage } from './views/mount';

/** Compose the feature modules; no module imports this entry point. */
export function startApp(): void {
  mountPage();
  const state = createAppState();
  const native = Capacitor.isNativePlatform();
  el('platform').textContent = native ? Capacitor.getPlatform().toUpperCase() : 'WEB PREVIEW';

  const map = new MapPreview(state, native);
  const settings = new SettingsController(state, map, routingChanged => {
    if (routingChanged) trip.invalidate();
    trip.renderSettingsSummary();
    trip.setStatus(state.settings.apiKey
      ? 'Settings applied. Generate routes when ready.'
      : 'Settings applied. Add your API key to connect the map.');
    if (state.settings.apiKey) void map.ensure().catch(error => trip.setStatus(errorMessage(error)));
    else map.disconnect();
  });
  const trip = new TripController(state, map, tab => settings.open(tab));

  settings.mount();
  trip.mount();
  map.mount(id => trip.selectRoute(id));
  el('connect-map').onclick = () => {
    if (state.settings.apiKey && native) void map.ensure().catch(map.fail);
    else void settings.open('connection');
  };
}
