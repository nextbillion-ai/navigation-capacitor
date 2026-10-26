import type { Stop } from '@nextbillion-ai/navigation-capacitor';

export const presets: Record<string, Stop[]> = {
  'Singapore · City drive': [{name:'Marina Bay Sands',latitude:1.2834,longitude:103.8607},{name:'Orchard Road',latitude:1.3048,longitude:103.8318}],
  'Singapore · Delivery stops': [{name:'Marina Bay Sands',latitude:1.2834,longitude:103.8607},{name:'Chinatown',latitude:1.2845,longitude:103.8441},{name:'Clarke Quay',latitude:1.2906,longitude:103.8465},{name:'Orchard Road',latitude:1.3048,longitude:103.8318}],
  'San Francisco · Short trip': [{name:'Mission start',latitude:37.761689,longitude:-122.423579},{name:'Delivery stop',latitude:37.761027,longitude:-122.424467},{name:'Destination',latitude:37.760872,longitude:-122.426183}],
  'Dubai · Downtown': [{name:'Dubai Mall',latitude:25.1972,longitude:55.2796},{name:'Business Bay',latitude:25.1852,longitude:55.2634}]
};
