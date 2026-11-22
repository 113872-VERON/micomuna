import { Component, computed, input } from '@angular/core';
import { EstadoReclamo } from '../../reclamos/modelos';
import { nombreDeEstado } from '../../reclamos/seguimiento';

// El estado de un reclamo en una pastilla: la palabra siempre va escrita, el color solo acompaña.
@Component({
  selector: 'app-chip-estado',
  template: `<span class="chip" [attr.data-estado]="estado()">{{ nombre() }}</span>`,
  styles: `
    :host { display: inline-flex; }
    .chip { display: inline-block; padding: 3px 12px; border-radius: 999px; font-size: 15px; font-weight: 700; line-height: 1.3; white-space: nowrap; background: #e4e7eb; color: #323f4b; }
    [data-estado='ASIGNADO'] { background: #dbeafe; color: #1e3a8a; }
    [data-estado='EN_REPARACION'] { background: #fde68a; color: #5b3a00; }
    [data-estado='RESUELTO'] { background: #cdeed8; color: #0f4d2a; }
    [data-estado='DESCARTADO'] { background: #fde2e0; color: #8a1c13; }
  `,
})
export class ChipEstado {
  readonly estado = input.required<EstadoReclamo>();
  protected readonly nombre = computed(() => nombreDeEstado(this.estado()));
}
