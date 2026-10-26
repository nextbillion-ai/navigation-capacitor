import type { RouteSummary, RoutingUnit } from '@nextbillion-ai/navigation-capacitor';
import { profileNames } from '../../data/routing';
import { el } from '../../shared/dom';
import type { DemoSettings, RoutingSettings } from '../../state';

export function clearRoutes(): void {
  el('routes').innerHTML = '<p class="empty">Generate routes to compare distance and travel time.</p>';
  el('route-count').textContent = '—';
  el('route-settings').textContent = '';
  el('map-caption').textContent = 'Stops are visited in the order shown.';
}

export function renderRoutes(routes: RouteSummary[], onSelect: (id: number) => void, unit?: RoutingUnit): void {
  const host = el('routes');
  host.textContent = '';
  el('route-count').textContent = String(routes.length);
  routes.forEach((route, index) => {
    const label = document.createElement('label');
    label.className = `route-option${index === 0 ? ' selected' : ''}`;
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = 'route';
    input.value = String(route.id);
    input.checked = index === 0;
    const text = document.createElement('span');
    text.innerHTML = `<strong>${Math.max(1, Math.round(route.duration / 60))} <small>min</small></strong><span>${distance(route.distance, unit)} · Route ${index + 1}</span>`;
    input.onchange = () => onSelect(route.id);
    label.append(input, text);
    host.append(label);
  });
}

export function renderSelection(route: RouteSummary, unit?: RoutingUnit): void {
  el('routes').querySelectorAll<HTMLInputElement>('input').forEach(input => {
    input.checked = Number(input.value) === route.id;
    input.closest('label')!.classList.toggle('selected', input.checked);
  });
  el('map-caption').textContent = `Route ${route.id + 1} · ${distance(route.distance, unit)} · ${Math.max(1, Math.round(route.duration / 60))} min`;
}

export function renderRouteSettings(routing: RoutingSettings): void {
  const size = routing.truck?.size;
  const dimensions = size ? ` · ${size.heightCm} × ${size.widthCm} × ${size.lengthCm} cm` : '';
  const preferences = [routing.departureTime === undefined ? '' : new Date(routing.departureTime * 1000).toLocaleString(), routing.language, routing.unit, routing.crossBorder === undefined ? '' : `Border crossings: ${routing.crossBorder ? 'expected' : 'avoid'}`].filter(Boolean);
  el('route-settings').textContent = `${profileNames[routing.profile || 'car']} · Avoid: ${routing.avoid?.join(', ') || 'none'}${dimensions}${preferences.length ? ` · ${preferences.join(' · ')}` : ''}`;
}

export function renderSettingsSummary(settings: DemoSettings): void {
  const { routing, simulate } = settings;
  el('profile-summary').textContent = profileNames[routing.profile || 'car'];
  const count = routing.avoid?.length || 0;
  const avoid = count ? `${count} avoidance${count > 1 ? 's' : ''}` : 'No additional avoidances';
  const weight = routing.truck?.weightKg ? ` · ${routing.truck.weightKg / 1000} t truck` : '';
  el('config-summary').textContent = avoid + weight;
  el('mode-label').textContent = simulate ? 'Simulation on' : 'Live GPS';
  el('mode-toggle').setAttribute('aria-pressed', String(simulate));
}

function distance(meters: number, unit?: RoutingUnit): string {
  return unit === 'imperial' ? `${(meters / 1609.344).toFixed(1)} mi` : `${(meters / 1000).toFixed(1)} km`;
}
