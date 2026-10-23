import type { Avoid, HazmatType } from '@nextbillion/navigation-capacitor';

export const avoidChoices: [Avoid, string][] = [
  ['toll', 'Tolls'], ['highway', 'Highways'], ['ferry', 'Ferries'],
  ['sharp_turn', 'Sharp turns'], ['uturn', 'U-turns'], ['service_road', 'Service roads'],
  ['left_turn', 'Left turns'], ['right_turn', 'Right turns'], ['tunnel', 'Tunnels']
];
export const hazmatChoices: [HazmatType, string][] = [
  ['general', 'General'], ['circumstantial', 'Circumstantial'],
  ['explosive', 'Explosive'], ['harmful_to_water', 'Harmful to water']
];

export const profileNames: Record<string, string> = {
  car: 'Car', truck: 'Truck', bike: 'Bicycle', motorcycle: 'Motorcycle',
};
