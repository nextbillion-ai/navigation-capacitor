import type { AppState, SettingsTab } from '../../state';
import { el, errorMessage } from '../../shared/dom';
import type { MapPreview } from '../map/preview-map';
import { SettingsForm } from './form';

/** Owns the settings modal, focus handling and the draft/apply/cancel boundary. */
export class SettingsController {
  private readonly form = new SettingsForm();
  private previousFocus: HTMLElement | null = null;

  constructor(
    private readonly state: AppState,
    private readonly map: MapPreview,
    private readonly onApply: (routingChanged: boolean) => void,
  ) {}

  mount(): void {
    this.form.mount();
    el('settings-open').onclick = () => { void this.open(); };
    el('routing-settings').onclick = () => { void this.open('vehicle'); };
    for (const id of ['settings-close', 'settings-cancel', 'settings-backdrop']) {
      el(id).onclick = () => this.close();
    }
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(button => {
      button.onclick = () => this.showTab(button.dataset.tab as SettingsTab);
    });
    el('go-truck').onclick = () => this.showTab('truck');
    el('settings-save').onclick = () => this.apply();
    document.addEventListener('keydown', event => this.handleKeyboard(event));
  }

  async open(tab: SettingsTab = this.state.settings.apiKey ? 'vehicle' : 'connection'): Promise<void> {
    if (this.state.busy || this.state.modalOpen) return;
    this.previousFocus = document.activeElement as HTMLElement;
    this.state.modalOpen = true;
    await this.map.syncLayout();
    this.form.fill(this.state.settings);
    this.showTab(tab);
    el('settings-modal').hidden = false;
    el('planner').setAttribute('aria-hidden', 'true');
    el<HTMLButtonElement>('settings-close').focus();
  }

  private close(): void {
    (document.activeElement as HTMLElement)?.blur();
    el('settings-modal').hidden = true;
    this.state.modalOpen = false;
    el('planner').removeAttribute('aria-hidden');
    this.previousFocus?.focus();
    this.map.scheduleLayout();
  }

  private showTab(tab: SettingsTab): void {
    if (tab === 'truck' && !this.form.isTruck) tab = 'vehicle';
    document.querySelectorAll<HTMLElement>('[data-section]').forEach(section => {
      section.hidden = section.dataset.section !== tab;
    });
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(button => {
      const selected = button.dataset.tab === tab;
      button.classList.toggle('active', selected);
      button.setAttribute('aria-current', String(selected));
    });
    el('settings-error').textContent = '';
  }

  private apply(): void {
    try {
      const next = this.form.read();
      const previous = this.state.settings;
      const changed = JSON.stringify(next.routing) !== JSON.stringify(previous.routing) || next.apiKey !== previous.apiKey;
      this.state.settings = next;
      this.close();
      this.onApply(changed);
    } catch (error) {
      el('settings-error').textContent = errorMessage(error);
    }
  }

  private handleKeyboard(event: KeyboardEvent): void {
    if (!this.state.modalOpen) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    }
    if (event.key !== 'Tab') return;
    const controls = Array.from(el('settings-modal').querySelectorAll<HTMLElement>(
      'button:not(:disabled),input:not([type=hidden]),select,summary',
    )).filter(control => control.getClientRects().length > 0);
    const first = controls[0], last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
