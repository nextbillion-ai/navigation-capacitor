import type { Approach, RoutingUnit, Stop } from '@nextbillion/navigation-capacitor';
import { el } from '../../shared/dom';
import type { RoutingSettings } from '../../state';

type RouteOptions = Pick<RoutingSettings, 'departureTime' | 'crossBorder' | 'turnAngleRange' | 'approaches' | 'language' | 'unit'>;
const maxDeparture = 2147483647;

/** Optional route preferences: blank controls never replace a native SDK default. */
export class RouteSettingsForm {
  mount(): void {
    el('route-departure-mode').onchange = () => this.updateVisibility();
    el('route-approaches-mode').onchange = () => this.updateVisibility();
    el('avoid-options').addEventListener('change', () => this.updateVisibility());
    el('route-defaults').onclick = () => this.fill({}, []);
    el<HTMLInputElement>('route-departure-time').max = this.localDateTime(maxDeparture);
  }

  fill(routing: RoutingSettings, stops: Stop[]): void {
    el<HTMLSelectElement>('route-departure-mode').value = routing.departureTime === undefined ? '' : 'custom';
    el<HTMLInputElement>('route-departure-time').value = routing.departureTime === undefined ? '' : this.localDateTime(routing.departureTime);
    el<HTMLSelectElement>('route-cross-border').value = routing.crossBorder === undefined ? '' : String(routing.crossBorder);
    el<HTMLInputElement>('route-language').value = routing.language ?? '';
    el<HTMLSelectElement>('route-unit').value = routing.unit ?? '';
    el<HTMLInputElement>('route-turn-angle').value = routing.turnAngleRange === undefined ? '' : String(routing.turnAngleRange);
    el<HTMLSelectElement>('route-approaches-mode').value = routing.approaches ? 'custom' : '';
    // Keep the same stop labels when resetting other preferences in the open sheet.
    if (stops.length) this.renderApproaches(stops, routing.approaches);
    else el('route-approaches').querySelectorAll<HTMLSelectElement>('select').forEach(input => { input.value = 'unrestricted'; });
    this.updateVisibility();
  }

  updateVisibility(): void {
    el('route-departure-field').hidden = el<HTMLSelectElement>('route-departure-mode').value !== 'custom';
    el('route-approaches').hidden = el<HTMLSelectElement>('route-approaches-mode').value !== 'custom';
    const sharp = el('avoid-options').querySelector<HTMLInputElement>('input[value="sharp_turn"]')?.checked;
    el<HTMLInputElement>('route-turn-angle').disabled = !sharp;
  }

  read(): RouteOptions {
    const options: RouteOptions = {};
    if (el<HTMLSelectElement>('route-departure-mode').value === 'custom') {
      const raw = el<HTMLInputElement>('route-departure-time').value;
      const departureTime = raw ? new Date(raw).getTime() / 1000 : NaN;
      if (!Number.isInteger(departureTime) || departureTime < 1 || departureTime > maxDeparture) {
        throw new Error('Choose a valid departure date and time before 19 January 2038 (the Android SDK time limit).');
      }
      options.departureTime = departureTime;
    }
    const border = el<HTMLSelectElement>('route-cross-border').value;
    if (border) options.crossBorder = border === 'true';
    const language = el<HTMLInputElement>('route-language').value.trim();
    if (language) {
      if (!/^(?:[a-z]{2}|zh-Hans)$/.test(language)) throw new Error('Language: use a two-letter code such as en/fr/zh, or zh-Hans.');
      options.language = language;
    }
    const unit = el<HTMLSelectElement>('route-unit').value as RoutingUnit | '';
    if (unit) options.unit = unit;
    const angle = el<HTMLInputElement>('route-turn-angle');
    if (!angle.disabled && angle.value.trim()) {
      const value = Number(angle.value);
      if (!Number.isInteger(value) || value < 1 || value > 180) throw new Error('Minimum turn angle must be a whole number from 1 to 180 degrees.');
      options.turnAngleRange = value;
    }
    if (el<HTMLSelectElement>('route-approaches-mode').value === 'custom') {
      options.approaches = Array.from(el('route-approaches').querySelectorAll<HTMLSelectElement>('select')).map(input => input.value as Approach);
    }
    return options;
  }

  private renderApproaches(stops: Stop[], approaches?: Approach[]): void {
    const host = el('route-approaches');
    host.textContent = '';
    stops.slice(1).forEach((stop, index) => {
      const label = document.createElement('label');
      label.className = 'field';
      label.append(document.createTextNode(`${index + 1}. ${stop.name || 'Stop'}`));
      const select = document.createElement('select');
      select.setAttribute('aria-label', `Arrival side for ${stop.name || `stop ${index + 1}`}`);
      select.add(new Option('Either side', 'unrestricted'));
      select.add(new Option('Curb / driving side', 'curb'));
      select.value = approaches?.[index] ?? 'unrestricted';
      label.append(select);
      host.append(label);
    });
  }

  private localDateTime(seconds: number): string {
    const date = new Date(seconds * 1000);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }
}
