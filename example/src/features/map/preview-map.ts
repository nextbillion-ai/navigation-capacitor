import { NativeNavigation, type MapBounds } from '@nextbillion/navigation-capacitor';
import type { AppState } from '../../state';
import { validStops } from '../../state';
import { el, errorMessage } from '../../shared/dom';

/** Owns native map creation, viewport synchronization, visibility and page lifecycle. */
export class MapPreview {
  private ready = false;
  private loading = false;
  private key = '';
  private epoch = 0;
  private task: Promise<void> | null = null;
  private frame = 0;
  private readonly observer = new ResizeObserver(() => this.scheduleLayout());

  constructor(private readonly state: AppState, readonly native: boolean) {}

  get isReady(): boolean {
    return this.ready;
  }

  mount(onRouteSelected: (routeId: number) => Promise<void>): void {
    this.observer.observe(el('map-stage'));
    window.addEventListener('resize', this.scheduleLayout);
    window.addEventListener('scroll', this.scheduleLayout, true);
    window.visualViewport?.addEventListener('resize', this.scheduleLayout);
    window.visualViewport?.addEventListener('scroll', this.scheduleLayout);
    document.addEventListener('visibilitychange', this.scheduleLayout);
    el('fit-map').onclick = () => { void this.refresh().catch(this.fail); };

    if (this.native) {
      void NativeNavigation.addListener('mapRouteSelected', event => {
        void onRouteSelected(event.routeId).catch(this.fail);
      });
    } else {
      el('map-message').textContent = 'Native maps are available in the installed iOS and Android app. Explore the interface and settings here.';
      el('connect-map').textContent = 'Explore settings ↗';
      el('map-status').textContent = 'BROWSER PREVIEW';
    }

    window.addEventListener('pagehide', () => {
      if (this.native) void NativeNavigation.destroyMap();
      this.epoch++;
      this.ready = false;
      this.loading = false;
    });
    window.addEventListener('pageshow', event => {
      if (event.persisted && this.state.settings.apiKey) void this.ensure().catch(this.fail);
    });
  }

  private bounds(): MapBounds {
    const rect = el('map-stage').getBoundingClientRect();
    return { x: rect.left, y: rect.top, width: Math.max(1, rect.width), height: Math.max(1, rect.height) };
  }

  readonly fail = (error: unknown): void => {
    this.ready = false;
    this.loading = false;
    this.key = '';
    if (this.native) void NativeNavigation.destroyMap().catch(() => {});
    el('map-placeholder').hidden = false;
    el('map-status').textContent = 'RETRY AVAILABLE';
    el('map-message').textContent = errorMessage(error);
    el('connect-map').textContent = 'Retry map ↗';
    el<HTMLButtonElement>('fit-map').disabled = true;
  };

  async syncLayout(): Promise<void> {
    if (!this.native || (!this.ready && !this.loading)) return;
    try {
      await NativeNavigation.updateMap({
        bounds: this.bounds(),
        visible: !this.state.modalOpen && !this.state.navigating && !document.hidden,
      });
    } catch {
      // Resize can race map creation/destruction; ensure() owns startup errors.
    }
  }

  readonly scheduleLayout = (): void => {
    cancelAnimationFrame(this.frame);
    this.frame = requestAnimationFrame(() => { void this.syncLayout(); });
  };

  async refresh(): Promise<void> {
    if (!this.ready) return;
    if (this.state.routes.length) await this.showRoute(this.state.selectedRouteId, true);
    else await this.showStops();
  }

  async showStops(): Promise<void> {
    await NativeNavigation.setMapStops({ stops: validStops(this.state.stops) ? this.state.stops : [] });
  }

  async showRoute(routeId: number, fit = false): Promise<void> {
    if (this.ready) await NativeNavigation.showMapRoute({ routeId, fit });
  }

  async ensure(): Promise<void> {
    const { apiKey } = this.state.settings;
    if (!this.native || !apiKey) return;
    if (this.ready && this.key === apiKey) return;
    if (this.loading && this.key === apiKey && this.task) return this.task;
    const epoch = ++this.epoch;
    this.key = apiKey;
    this.ready = false;
    this.loading = true;
    el<HTMLButtonElement>('fit-map').disabled = true;
    el('map-status').textContent = 'CONNECTING';
    el('map-message').textContent = 'Loading your native map…';

    this.task = (async () => {
      try {
        await NativeNavigation.createMap({ apiKey, bounds: this.bounds() });
        if (epoch !== this.epoch) return;
        this.ready = true;
        el('map-placeholder').hidden = true;
        el('map-status').textContent = 'NATIVE MAP';
        el<HTMLButtonElement>('fit-map').disabled = false;
        await this.syncLayout();
        await this.refresh();
      } catch (error) {
        if (epoch === this.epoch) {
          this.fail(error);
          throw error;
        }
      } finally {
        if (epoch === this.epoch) this.loading = false;
      }
    })();
    return this.task;
  }

  disconnect(): void {
    if (!this.native) return;
    this.epoch++;
    this.ready = false;
    this.loading = false;
    el<HTMLButtonElement>('fit-map').disabled = true;
    el('connect-map').textContent = 'Connect map ↗';
    void NativeNavigation.destroyMap();
    el('map-placeholder').hidden = false;
    el('map-status').textContent = 'NOT CONNECTED';
    el('map-message').textContent = 'Connect your NextBillion key to explore routes on a native map.';
  }
}
