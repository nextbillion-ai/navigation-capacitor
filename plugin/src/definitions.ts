import type { PluginListenerHandle } from '@capacitor/core';

/** WGS84 coordinates. All route points are visited in the supplied order. */
export interface Stop {
  name: string;
  latitude: number;
  longitude: number;
}

export type Approach = 'unrestricted' | 'curb';
export type RoutingUnit = 'metric' | 'imperial';

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

/** Both platforms request Flexible/fastest routes, full polyline6 geometry and up to two alternatives.
 * This is a focused wrapper contract, not a passthrough for arbitrary native SDK parameters.
 */
export interface PlanRouteOptions {
  /** NextBillion access key with Maps and Navigation enabled. Not persisted by the wrapper. */
  apiKey: string;
  /** Origin, optional intermediate stops, destination. Between 2 and 12 points. */
  stops: Stop[];
  /** Default car. All profiles use the native SDK's Flexible routing endpoint. */
  profile?: RoutingProfile;
  avoid?: Avoid[];
  /** Unix epoch seconds, whole number 1–2147483647 (Android SDK Int limit).
   * Omit to let the native SDK choose its current time. Do not pass milliseconds.
   */
  departureTime?: number;
  /** Whether crossing international borders is expected. Omit for the native SDK default. */
  crossBorder?: boolean;
  /** Whole degrees 1–180. Requires avoid to include sharp_turn.
   * Permitted turn angles are [turnAngleRange, 360 - turnAngleRange]. Omit for service defaults.
   */
  turnAngleRange?: number;
  /** One value per non-origin stop: stops[1], ..., destination. Preserve order and duplicates.
   * Omit for native defaults; curb requests arrival on the driving side of the road.
   */
  approaches?: Approach[];
  /** Lowercase two-letter instruction language (for example en, fr, zh), or zh-Hans.
   * Native SDK language normalization applies. Omit for native SDK locale behavior.
   */
  language?: string;
  /** Instruction/display units. Truck inputs remain cm/kg/tonnes; route summaries remain meters/seconds.
   * Omit for the native SDK default measurement system.
   */
  unit?: RoutingUnit;
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
  /** Final destination reached before the user closed navigation, or an earlier user exit. */
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
  /** Open a separate native screen. Arrival keeps the SDK sheet open; resolves only after user exit. Startup errors reject. */
  startNavigation(options: StartNavigationOptions): Promise<NavigationResult>;
}
