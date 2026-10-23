import type { HazmatType, PlanRouteOptions, RouteSummary, Stop } from '@nextbillion/navigation-capacitor';
import { presets } from './data/trips';

export type RoutingSettings = Pick<PlanRouteOptions, 'profile' | 'avoid' | 'truck'>;
export type SettingsTab = 'connection' | 'vehicle' | 'avoid' | 'truck';

/** Preserve optional truck inputs when switching to another vehicle profile. */
export interface TruckFormValues {
  height: string;
  width: string;
  length: string;
  weight: string;
  axle: string;
  prefer: boolean;
  type: string;
  hazmats: HazmatType[];
}

export interface DemoSettings {
  apiKey: string;
  routing: RoutingSettings;
  simulate: boolean;
  truck: TruckFormValues;
}

/** Shared application state. Native map resources are owned by MapPreview. */
export interface AppState {
  stops: Stop[];
  routes: RouteSummary[];
  selectedRouteId: number;
  generation: number;
  busy: boolean;
  navigating: boolean;
  modalOpen: boolean;
  settings: DemoSettings;
}

export function createAppState(): AppState {
  return {
    stops: Object.values(presets)[0].map(stop => ({ ...stop })),
    routes: [],
    selectedRouteId: 0,
    generation: 0,
    busy: false,
    navigating: false,
    modalOpen: false,
    settings: {
      apiKey: '',
      routing: { profile: 'car', avoid: [] },
      simulate: true,
      truck: {
        height: '350', width: '250', length: '1000', weight: '20000', axle: '8.5',
        prefer: false, type: '', hazmats: [],
      },
    },
  };
}

export function validStops(stops: Stop[]): boolean {
  return stops.every(stop =>
    Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude) &&
    Math.abs(stop.latitude) <= 90 && Math.abs(stop.longitude) <= 180,
  );
}
