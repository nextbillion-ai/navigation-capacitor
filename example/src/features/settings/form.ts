import type { Avoid, HazmatType, Stop, TruckType } from '@nextbillion-ai/navigation-capacitor';
import { avoidChoices, hazmatChoices } from '../../data/routing';
import { RouteSettingsForm } from './route-form';
import { el } from '../../shared/dom';
import type { DemoSettings, RoutingSettings, TruckFormValues } from '../../state';

const truckFields = ['height', 'width', 'length', 'weight', 'axle'] as const;

/** Settings inputs, presets and validation. Reading the form does not apply changes. */
export class SettingsForm {
  private readonly routeForm = new RouteSettingsForm();
  mount(): void {
    this.renderChoices('avoid-options', avoidChoices);
    this.renderChoices('hazmat-options', hazmatChoices);
    this.routeForm.mount();

    document.querySelectorAll<HTMLButtonElement>('[data-profile]').forEach(button => {
      button.onclick = () => {
        el<HTMLInputElement>('profile').value = button.dataset.profile!;
        this.updateProfileChoices();
      };
    });
    el('profile').onchange = () => this.updateProfileChoices();
    el('avoid-clear').onclick = () => { this.setChecked('avoid-options', []); this.routeForm.updateVisibility(); };
    el('truck-type').onchange = () => {
      if (el<HTMLSelectElement>('truck-type').value) el<HTMLInputElement>('truck-prefer').checked = true;
    };
    el('truck-prefer').onchange = () => {
      if (!el<HTMLInputElement>('truck-prefer').checked) el<HTMLSelectElement>('truck-type').value = '';
    };
    el('truck-clear').onclick = () => {
      for (const name of truckFields) el<HTMLInputElement>(`truck-${name}`).value = '';
      el<HTMLInputElement>('truck-prefer').checked = false;
      el<HTMLSelectElement>('truck-type').value = '';
      this.setChecked('hazmat-options', []);
    };
    document.querySelectorAll<HTMLButtonElement>('[data-truck-preset]').forEach(button => {
      button.onclick = () => this.applyTruckPreset(button.dataset.truckPreset === 'rigid');
    });
    el('key-reveal').onclick = () => {
      const input = el<HTMLInputElement>('key');
      input.type = input.type === 'password' ? 'text' : 'password';
      const hidden = input.type === 'password';
      el('key-reveal').textContent = hidden ? 'Show' : 'Hide';
      el('key-reveal').setAttribute('aria-label', hidden ? 'Show API key' : 'Hide API key');
    };
  }

  get isTruck(): boolean {
    return el<HTMLInputElement>('profile').value === 'truck';
  }

  fill(settings: DemoSettings, stops: Stop[]): void {
    el<HTMLInputElement>('key').type = 'password';
    el('key-reveal').textContent = 'Show';
    el<HTMLInputElement>('key').value = settings.apiKey;
    el<HTMLInputElement>('profile').value = settings.routing.profile || 'car';
    el<HTMLInputElement>('simulate').checked = settings.simulate;
    this.setChecked('avoid-options', settings.routing.avoid || []);
    for (const name of truckFields) el<HTMLInputElement>(`truck-${name}`).value = settings.truck[name];
    el<HTMLInputElement>('truck-prefer').checked = settings.truck.prefer;
    el<HTMLSelectElement>('truck-type').value = settings.truck.type;
    this.setChecked('hazmat-options', settings.truck.hazmats);
    this.routeForm.fill(settings.routing, stops);
    this.updateProfileChoices();
    el('settings-error').textContent = '';
  }

  read(): DemoSettings {
    const routing = this.routingOptions();
    const truck: TruckFormValues = {
      height: el<HTMLInputElement>('truck-height').value,
      width: el<HTMLInputElement>('truck-width').value,
      length: el<HTMLInputElement>('truck-length').value,
      weight: el<HTMLInputElement>('truck-weight').value,
      axle: el<HTMLInputElement>('truck-axle').value,
      prefer: el<HTMLInputElement>('truck-prefer').checked,
      type: el<HTMLSelectElement>('truck-type').value,
      hazmats: this.checked<HazmatType>('hazmat-options'),
    };
    return {
      apiKey: el<HTMLInputElement>('key').value.trim(),
      // Drop omitted optional numbers, keeping comparison and native requests stable.
      routing: JSON.parse(JSON.stringify(routing)) as RoutingSettings,
      simulate: el<HTMLInputElement>('simulate').checked,
      truck,
    };
  }

  private renderChoices(id: string, choices: readonly (readonly [string, string])[]): void {
    for (const [value, title] of choices) {
      const label = document.createElement('label');
      label.className = 'choice';
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.value = value;
      const span = document.createElement('span');
      span.textContent = title;
      label.append(input, span);
      el(id).append(label);
    }
  }

  private checked<T extends string>(id: string): T[] {
    return Array.from(el(id).querySelectorAll<HTMLInputElement>('input:checked')).map(input => input.value as T);
  }

  private setChecked(id: string, values: string[]): void {
    el(id).querySelectorAll<HTMLInputElement>('input').forEach(input => {
      input.checked = values.includes(input.value);
    });
  }

  private updateProfileChoices(): void {
    const profile = el<HTMLInputElement>('profile').value;
    document.querySelectorAll<HTMLButtonElement>('[data-profile]').forEach(button => {
      const selected = button.dataset.profile === profile;
      button.classList.toggle('selected', selected);
      button.setAttribute('aria-pressed', String(selected));
    });
    el<HTMLButtonElement>('truck-tab').disabled = !this.isTruck;
    el('go-truck').hidden = !this.isTruck;
  }

  private applyTruckPreset(rigid: boolean): void {
    const values = {
      height: rigid ? 350 : 400, width: 250, length: rigid ? 1000 : 1650,
      weight: rigid ? 20000 : 36000, axle: rigid ? 8.5 : 9,
    };
    for (const [name, value] of Object.entries(values)) el<HTMLInputElement>(`truck-${name}`).value = String(value);
    el<HTMLSelectElement>('truck-type').value = rigid ? 'rigid_truck' : 'semi_trailer';
    el<HTMLInputElement>('truck-prefer').checked = true;
  }

  private optionalNumber(id: string, name: string, max: number, integer = true): number | undefined {
    const raw = el<HTMLInputElement>(id).value.trim();
    if (!raw) return undefined;
    const value = Number(raw);
    if (!Number.isFinite(value) || value <= 0 || value > max || (integer && !Number.isInteger(value))) {
      throw new Error(`${name}: enter ${integer ? 'a whole number' : 'a number'} greater than 0 and no more than ${max}.`);
    }
    return value;
  }

  private routingOptions(): RoutingSettings {
    const profile = el<HTMLInputElement>('profile').value as RoutingSettings['profile'];
    const options: RoutingSettings = { profile, avoid: this.checked<Avoid>('avoid-options'), ...this.routeForm.read() };
    if (profile !== 'truck') return options;

    const heightCm = this.optionalNumber('truck-height', 'Height (cm)', 1000);
    const widthCm = this.optionalNumber('truck-width', 'Width (cm)', 5000);
    const lengthCm = this.optionalNumber('truck-length', 'Length (cm)', 5000);
    const dimensions = [heightCm, widthCm, lengthCm];
    if (dimensions.some(value => value !== undefined) && dimensions.some(value => value === undefined)) {
      throw new Error('Provide all three truck dimensions, or clear all three to use service defaults.');
    }
    const type = el<HTMLSelectElement>('truck-type').value as TruckType | '';
    options.truck = {
      ...(heightCm !== undefined && widthCm !== undefined && lengthCm !== undefined
        ? { size: { heightCm, widthCm, lengthCm } } : {}),
      weightKg: this.optionalNumber('truck-weight', 'Weight (kg)', 100000),
      axleLoadTonnes: this.optionalNumber('truck-axle', 'Axle load (tonnes)', 100000, false),
      hazmatTypes: this.checked<HazmatType>('hazmat-options'),
      preferTruckRoutes: el<HTMLInputElement>('truck-prefer').checked,
      types: type ? [type] : [],
    };
    return options;
  }
}
