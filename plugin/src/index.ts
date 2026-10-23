import { registerPlugin } from '@capacitor/core';
import type { NativeNavigationPlugin } from './definitions.js';

/** Native SDK implementation is discovered automatically by Capacitor after cap sync. */
export const NativeNavigation = registerPlugin<NativeNavigationPlugin>('NativeNavigation', {
  web: () => import('./web.js').then(module => new module.NativeNavigationWeb()),
});

/** SDK-style facade, equivalent to the Flutter package's NBNavigation entry point. */
export const NBNavigation = NativeNavigation;

export * from './definitions.js';
