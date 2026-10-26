import { NativeNavigation } from '@nextbillion/navigation-capacitor';
import { el, errorMessage } from '../../shared/dom';
import { validStops, type AppState, type SettingsTab } from '../../state';
import type { MapPreview } from '../map/preview-map';
import { clearRoutes, renderRoutes, renderSelection, renderRouteSettings, renderSettingsSummary } from './route-results';
import { StopsEditor } from './stops-editor';

/** Coordinates stop edits, planning, route selection and the native guidance round trip. */
export class TripController {
  private readonly editor: StopsEditor;
  private previewTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly state: AppState,
    private readonly map: MapPreview,
    private readonly openSettings: (tab: SettingsTab) => Promise<void>,
  ) {
    this.editor = new StopsEditor(state, () => this.invalidate());
  }

  mount(): void {
    this.editor.mount();
    this.renderSettingsSummary();
    el('mode-toggle').onclick = () => {
      this.state.settings.simulate = !this.state.settings.simulate;
      this.renderSettingsSummary();
    };
    el('plan').onclick = () => { void this.plan(); };
    el('start').onclick = () => { void this.startNavigation(); };
  }

  setStatus(message: string): void {
    el('status').textContent = message;
  }

  renderSettingsSummary(): void {
    renderSettingsSummary(this.state.settings);
  }

  invalidate(): void {
    this.state.generation++;
    this.state.routes = [];
    el<HTMLButtonElement>('start').disabled = true;
    clearRoutes();
    clearTimeout(this.previewTimer);
    this.previewTimer = setTimeout(() => { void this.map.refresh().catch(this.map.fail); }, 350);
  }

  async selectRoute(routeId: number, fit = false): Promise<void> {
    const route = this.state.routes.find(candidate => candidate.id === routeId);
    if (!route) return;
    this.state.selectedRouteId = routeId;
    renderSelection(route, this.state.settings.routing.unit);
    await this.map.showRoute(routeId, fit);
  }

  private lock(value: boolean): void {
    this.state.busy = value;
    document.querySelectorAll<HTMLInputElement | HTMLButtonElement | HTMLSelectElement>(
      '#planner input,#planner select,#planner button,#settings-open',
    ).forEach(control => { control.disabled = value; });
    if (!value) {
      this.editor.render();
      el<HTMLButtonElement>('start').disabled = !this.state.routes.length;
      el<HTMLButtonElement>('fit-map').disabled = !this.map.isReady;
    }
  }

  private async plan(): Promise<void> {
    if (this.state.busy) return;
    if (!this.map.native) {
      this.setStatus('Install the iOS or Android app to use the native map and navigation.');
      return;
    }
    if (!this.state.settings.apiKey) {
      this.setStatus('Add your API key in Settings.');
      void this.openSettings('connection');
      return;
    }
    if (!validStops(this.state.stops)) {
      this.setStatus('Check all coordinates: latitude −90…90; longitude −180…180.');
      return;
    }
    this.invalidate();
    clearTimeout(this.previewTimer);
    const request = this.state.generation;
    this.lock(true);
    this.setStatus('Calculating routes…');
    try {
      await this.map.ensure();
      await this.map.showStops();
      const { apiKey, routing } = this.state.settings;
      const result = await NativeNavigation.planRoute({ apiKey, stops: this.state.stops, ...routing });
      if (request !== this.state.generation) return;
      if (!result.routes.length) throw new Error('No routes returned.');
      this.state.routes = result.routes;
      this.state.selectedRouteId = result.routes[0].id;
      renderRoutes(result.routes, id => { void this.selectRoute(id).catch(this.map.fail); }, routing.unit);
      await this.selectRoute(this.state.selectedRouteId, true);
      renderRouteSettings(routing);
      this.setStatus('Route ready. Choose a route and start navigation.');
    } catch (error) {
      this.state.routes = [];
      this.setStatus(errorMessage(error));
    } finally {
      this.lock(false);
    }
  }

  private async startNavigation(): Promise<void> {
    if (this.state.busy || !this.state.routes.length) return;
    this.lock(true);
    this.state.navigating = true;
    await this.map.syncLayout();
    this.setStatus('Opening native navigation…');
    try {
      const result = await NativeNavigation.startNavigation({
        routeId: this.state.selectedRouteId,
        simulate: this.state.settings.simulate,
      });
      this.setStatus(result.reason === 'arrived'
        ? 'Trip completed. Welcome back to nb.ai.'
        : 'Navigation closed. You are back in nb.ai.');
    } catch (error) {
      this.setStatus(errorMessage(error));
    } finally {
      this.state.navigating = false;
      this.lock(false);
      await this.map.syncLayout();
    }
  }
}
