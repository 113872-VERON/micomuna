import { Component, computed, input } from '@angular/core';
import { COLOR_ESTADO } from '../../reclamos/estilo-estados';
import { EstadoReclamo } from '../../reclamos/modelos';
import { nombreDeEstado } from '../../reclamos/seguimiento';

// El estado de un reclamo en una pastilla: ícono, palabra y color (guía de diseño, sección 6).
// La palabra siempre va escrita; el color y el dibujo solo acompañan.
@Component({
  selector: 'app-chip-estado',
  template: `
    <span class="chip" [style.background]="colores().fondo" [style.color]="colores().texto" [attr.data-estado]="estado()">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
        @switch (estado()) {
          @case ('RECIBIDO') { <rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="M3.5 7l8.5 6 8.5-6" /> }
          @case ('ASIGNADO') { <circle cx="12" cy="8" r="4" /><path d="M4 21c1-4.5 4-6 8-6s7 1.5 8 6" /> }
          @case ('EN_REPARACION') { <path d="M14.5 6.5a4 4 0 0 0-5 5L3 18v3h3l1-1v-2h2l1-1v-2l1.5-1.5a4 4 0 0 0 5-5l-3 3-2-2z" /> }
          @case ('RESUELTO') { <circle cx="12" cy="12" r="9.5" /><path d="M7.5 12.5l3 3 6-6.5" /> }
          @case ('DESCARTADO') { <circle cx="12" cy="12" r="9" /><path d="M6 6l12 12" /> }
        }
      </svg>
      {{ nombre() }}
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .chip { display: inline-flex; align-items: center; gap: 6px; padding: 3px 12px 3px 8px; border-radius: 999px; font-size: 15px; font-weight: 700; line-height: 1.3; white-space: nowrap; }
    svg { flex: none; }
  `,
})
export class ChipEstado {
  readonly estado = input.required<EstadoReclamo>();
  protected readonly nombre = computed(() => nombreDeEstado(this.estado()));
  protected readonly colores = computed(() => COLOR_ESTADO[this.estado()]);
}
