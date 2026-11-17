import { Component, computed, input, signal } from '@angular/core';
import { ComunaDto } from '../../auth/modelos';

// Encabezado de las pantallas de una comuna: el nombre es lo más grande y "MiComuna" una marca chica.
@Component({
  selector: 'app-cabecera-comuna',
  template: `
    <header class="cab">
      <div class="plataforma">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 22s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <circle cx="12" cy="10" r="2.5" stroke="currentColor" stroke-width="2" />
        </svg>
        MiComuna
      </div>
      <div class="comuna" [class.sin-logo]="!mostrarLogo()">
        @if (mostrarLogo()) {
          <div class="logo"><img [src]="urlLogo()" alt="" (error)="logoFallo.set(true)" /></div>
        }
        <div class="nombre">
          <span class="de">Comuna de</span>
          <span class="grande">{{ comuna().nombre }}</span>
          <span class="prov">{{ ubicacion() }}</span>
        </div>
      </div>
    </header>
  `,
  styles: `
    :host { display: flex; flex-direction: column; }
    .cab { flex: 1; background: var(--p); color: #fff; padding: 52px 24px 56px; display: flex; flex-direction: column; gap: 18px; }
    @media (min-width: 900px) { :host-context(.pantalla) .cab { justify-content: center; padding: 48px 56px; } }
    .plataforma { font-size: 17px; font-weight: 500; display: flex; gap: 8px; align-items: center; }
    .comuna { display: flex; gap: 16px; align-items: center; }
    .logo { flex: none; width: 76px; height: 76px; background: #fff; border-radius: 18px; padding: 8px; display: flex; align-items: center; justify-content: center; }
    .logo img { max-width: 100%; max-height: 100%; display: block; object-fit: contain; }
    .nombre { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .de, .prov { font-size: 18px; font-weight: 500; }
    .grande { font-size: 34px; line-height: 1.08; font-weight: 700; overflow-wrap: anywhere; }
    .sin-logo .grande { font-size: 40px; }
  `,
})
export class CabeceraComuna {
  readonly comuna = input.required<ComunaDto>();

  // Para la vista previa del alta: un logo todavía no subido (blob local) que se muestra en lugar del guardado.
  readonly logoLocal = input<string | null>(null);

  protected readonly logoFallo = signal(false);
  protected readonly mostrarLogo = computed(() => !!this.logoLocal() || (this.comuna().tieneLogo && !this.logoFallo()));
  protected readonly urlLogo = computed(() => this.logoLocal() ?? `/api/comunas/${encodeURIComponent(this.comuna().slug)}/logo`);
  protected readonly ubicacion = computed(() => {
    const { departamento, provincia } = this.comuna();
    return departamento ? `${departamento}, ${provincia}` : provincia;
  });
}
