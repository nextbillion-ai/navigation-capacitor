import { WebPlugin } from '@capacitor/core';
import type { NativeNavigationPlugin, PlanRouteOptions, PlanRouteResult, StartNavigationOptions, NavigationResult, CreateMapOptions, UpdateMapOptions, MapStopsOptions, MapRouteOptions } from './definitions.js';

/** Browser previews are supported by the example; the native SDK requires iOS or Android. */
export class NativeNavigationWeb extends WebPlugin implements NativeNavigationPlugin {
  async createMap(_options: CreateMapOptions): Promise<void> { throw this.unavailable('Native maps require iOS or Android.'); }
  async updateMap(_options: UpdateMapOptions): Promise<void> { throw this.unavailable('Native maps require iOS or Android.'); }
  async setMapStops(_options: MapStopsOptions): Promise<void> { throw this.unavailable('Native maps require iOS or Android.'); }
  async showMapRoute(_options: MapRouteOptions): Promise<void> { throw this.unavailable('Native maps require iOS or Android.'); }
  async destroyMap(): Promise<void> { throw this.unavailable('Native maps require iOS or Android.'); }
  async planRoute(_options: PlanRouteOptions): Promise<PlanRouteResult> {
    throw this.unavailable('Native navigation requires an installed iOS or Android Capacitor app.');
  }

  async startNavigation(_options: StartNavigationOptions): Promise<NavigationResult> {
    throw this.unavailable('Native navigation requires an installed iOS or Android Capacitor app.');
  }
}
