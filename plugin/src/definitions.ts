import type { PluginListenerHandle } from '@capacitor/core';

/** WGS84 coordinates. All route points are visited in the supplied order. */
export interface Stop {
  name: string;
  latitude: number;
  longitude: number;
}

export type RoutingProfile = 'car' | 'truck' | 'bike' | 'motorcycle';
/** Use [] (or omit) for no additional avoidances. */
export type Avoid = 'toll' | 'highway' | 'ferry' | 'sharp_turn' | 'uturn'
  | 'service_road' | 'left_turn' | 'right_turn' | 'tunnel';
export type HazmatType = 'general' | 'circumstantial' | 'explosive' | 'harmful_to_water';
export type TruckType = 'rigid_truck' | 'semi_trailer' | 'b_double' | 'road_train' | 'generic_truck';

export interface TruckOptions {
  /** Whole centimeters. Supply all three dimensions, including trailer/load. */
  size?: { heightCm: number; widthCm: number; lengthCm: number };
  /** Whole kilograms, including trailer/load; 1–100000. */
  weightKg?: number;
  /** Total load per axle, in metric tonnes; positive, decimals allowed. */
  axleLoadTonnes?: number;
  hazmatTypes?: HazmatType[];
  /** Maps to prefer=truck_route. Default false. */
  preferTruckRoutes?: boolean;
  /** Requires preferTruckRoutes=true. */
  types?: TruckType[];
}

export interface PlanRouteOptions {
  /** NextBillion access key with Maps and Navigation enabled. Not persisted by the wrapper. */
  apiKey: string;
  /** Origin, optional intermediate stops, destination. Between 2 and 12 points. */
  stops: Stop[];
  /** Default car. All profiles use the native SDK's Flexible routing endpoint. */
  profile?: RoutingProfile;
  avoid?: Avoid[];
  /** Only accepted with profile=truck. Omit fields to use service defaults. */
  truck?: TruckOptions;
}

/** Metadata for a native SDK route. Geometry and guidance steps remain on the native side. */
export interface RouteSummary {
  /** Index into the most recent native plan; valid only in the current process. */
  id: number;
  /** Total route distance in meters. */
  distance: number;
  /** Estimated travel time in seconds. */
  duration: number;
}

export interface PlanRouteResult {
  routes: RouteSummary[];
}

export interface StartNavigationOptions {
  /** The id returned by the most recent planRoute call. */
  routeId: number;
  /** True uses the SDK's simulated drive; false uses device GPS. Default: true. */
  simulate?: boolean;
}

export interface NavigationResult {
  reason: 'arrived' | 'cancelled';
}

/** CSS pixels relative to the WebView viewport, from getBoundingClientRect(). */
export interface MapBounds { x: number; y: number; width: number; height: number }
export interface CreateMapOptions { apiKey: string; bounds: MapBounds }
export interface UpdateMapOptions { bounds?: MapBounds; visible?: boolean }
export interface MapStopsOptions { stops: Stop[] }
export interface MapRouteOptions { routeId: number; fit?: boolean }

export interface NativeNavigationPlugin {
  /** One native preview map per plugin. Resolves after its style loads. Replaces any existing map. */
  createMap(options: CreateMapOptions): Promise<void>;
  /** Sync layout/visibility. Hide before displaying HTML overlays above the map region. */
  updateMap(options: UpdateMapOptions): Promise<void>;
  /** Clear preview routes and show 2–12 ordered stop markers; [] clears everything. Does not calculate a route. */
  setMapStops(options: MapStopsOptions): Promise<void>;
  /** Show cached alternatives and highlight a route. fit defaults to true. */
  showMapRoute(options: MapRouteOptions): Promise<void>;
  destroyMap(): Promise<void>;
  addListener(eventName: 'mapRouteSelected', listener: (event: { routeId: number }) => void): Promise<PluginListenerHandle>;
  /** Calculate navigation-ready routes using the native SDK. A new plan replaces the previous plan. */
  planRoute(options: PlanRouteOptions): Promise<PlanRouteResult>;
  /** Open a separate native screen. Resolves after arrival or user exit; startup errors reject. */
  startNavigation(options: StartNavigationOptions): Promise<NavigationResult>;
}
