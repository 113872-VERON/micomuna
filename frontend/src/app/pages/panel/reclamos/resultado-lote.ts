import { Component, computed, input } from '@angular/core';
import { ResultadoEnLoteDto } from '../../../reclamos/modelos-panel';

// Qué pasó con cada reclamo después de un cambio de estado o un descarte: los que se aplicaron y los
// omitidos con el texto del servidor. Todo es texto plano.
@Component({
  selector: 'app-resultado-lote',
  template: `
    @if (resultado().aplicados.length > 0) {
      <div class="aviso ok" role="status">
        <p>
          <b>{{ titulo() }}</b>&ngsp;<span class="numeros">{{ aplicados() }}</span>
        </p>
      </div>
    }
    @if (resultado().omitidos.length > 0) {
      <div class="aviso alerta" role="status">
        <div>
          <p>
            <b>{{ resultado().omitidos.length === 1 ? 'No se pudo en 1 reclamo' : 'No se pudo en ' + resultado().omitidos.length + ' reclamos' }}</b>
          </p>
          <ul>
            @for (o of resultado().omitidos; track o.numero) {
              <li>{{ o.motivo }}</li>
            }
          </ul>
        </div>
      </div>
    }
    @if (resultado().aplicados.length === 0 && resultado().omitidos.length === 0) {
      <p>No hubo nada para cambiar.</p>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 10px; }
    p { margin: 0; }
    ul { margin: 6px 0 0; padding-left: 20px; overflow-wrap: anywhere; }
    .numeros { display: block; overflow-wrap: anywhere; }
    .aviso { display: flex; gap: 10px; padding: 12px 14px; border-radius: 14px; font-size: 17px; }
    .ok { background: var(--ayuda); }
    .alerta { background: #fff4d6; border: 2px solid #e0a81b; color: #3d2a00; }
  `,
})
export class ResultadoLote {
  readonly resultado = input.required<ResultadoEnLoteDto>();
  readonly modo = input<'cambio' | 'descarte'>('cambio');

  protected readonly titulo = computed(() => {
    const n = this.resultado().aplicados.length;
    if (this.modo() === 'descarte') {
      return n === 1 ? 'Se descartó 1 reclamo:' : `Se descartaron ${n} reclamos:`;
    }
    return n === 1 ? 'Cambió 1 reclamo:' : `Cambiaron ${n} reclamos:`;
  });
  protected readonly aplicados = computed(() => this.resultado().aplicados.map((n) => `n.º ${n}`).join(', '));
}
