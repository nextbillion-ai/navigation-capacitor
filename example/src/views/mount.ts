import planner from './planner.html?raw';
import settings from './settings.html?raw';
import { el } from '../shared/dom';

export function mountPage(): void {
  el('app').innerHTML = planner + settings;
}
