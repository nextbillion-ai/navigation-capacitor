import { presets } from '../../data/trips';
import { el } from '../../shared/dom';
import type { AppState } from '../../state';

/** Ordered stop editing and sample selection; changes invalidate the current plan. */
export class StopsEditor {
  constructor(private readonly state: AppState, private readonly onChange: () => void) {}

  mount(): void {
    const preset = el<HTMLSelectElement>('preset');
    for (const name of Object.keys(presets)) preset.add(new Option(name, name));
    preset.onchange = () => {
      this.state.stops = presets[preset.value].map(stop => ({ ...stop }));
      this.onChange();
      this.render();
    };
    el('add').onclick = () => {
      const { stops } = this.state;
      if (stops.length >= 12) return;
      stops.splice(stops.length - 1, 0, {
        name: 'New stop', latitude: stops[0].latitude, longitude: stops[0].longitude,
      });
      this.onChange();
      this.render();
    };
    this.render();
  }

  render(): void {
    const host = el('stops');
    host.textContent = '';
    const { stops } = this.state;
    stops.forEach((stop, index) => {
      const row = document.createElement('div');
      row.className = 'stop';
      const destination = index === stops.length - 1;
      row.innerHTML = `
        <span class="stop-marker ${destination ? 'destination' : ''}">${index === 0 ? 'A' : destination ? 'B' : index}</span>
        <div class="stop-fields">
          <input class="stop-name" aria-label="Stop name">
          <details>
            <summary><span class="coordinate-label"></span><span>Edit coordinates</span></summary>
            <div class="coordinate-inputs">
              <label>Latitude<input type="number" step="any" min="-90" max="90" aria-label="Latitude"></label>
              <label>Longitude<input type="number" step="any" min="-180" max="180" aria-label="Longitude"></label>
            </div>
          </details>
        </div>
        <button class="remove icon-button" aria-label="Remove stop">×</button>`;
      const inputs = row.querySelectorAll('input');
      inputs[0].value = stop.name;
      inputs[1].value = String(stop.latitude);
      inputs[2].value = String(stop.longitude);
      const coordinateLabel = row.querySelector('.coordinate-label')!;
      const showCoordinates = () => {
        const lat = Number.isFinite(stop.latitude) ? stop.latitude.toFixed(4) : '—';
        const lng = Number.isFinite(stop.longitude) ? stop.longitude.toFixed(4) : '—';
        coordinateLabel.textContent = `${lat}, ${lng}`;
      };
      showCoordinates();
      inputs.forEach((input, field) => input.addEventListener('input', () => {
        if (field === 0) stop.name = input.value;
        else if (field === 1) stop.latitude = input.value.trim() ? Number(input.value) : NaN;
        else stop.longitude = input.value.trim() ? Number(input.value) : NaN;
        showCoordinates();
        this.onChange();
      }));
      const remove = row.querySelector<HTMLButtonElement>('.remove')!;
      remove.disabled = stops.length <= 2;
      remove.onclick = () => {
        stops.splice(index, 1);
        this.onChange();
        this.render();
      };
      host.append(row);
    });
    el<HTMLButtonElement>('add').disabled = this.state.busy || stops.length >= 12;
  }
}
