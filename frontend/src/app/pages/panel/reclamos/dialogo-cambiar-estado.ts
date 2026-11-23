import { Component, computed, inject, input, output, signal } from '@angular/core';
import { mensajeDeError } from '../../../auth/errores';
import { Dialogo } from '../../../compartido/dialogo/dialogo';
import { NOMBRE_ESTADO } from '../../../reclamos/seguimiento';
import { AgenteDto, ESTADOS_DE_CAMBIO, EstadoDeCambio, ReclamoElegido, ResultadoEnLoteDto } from '../../../reclamos/modelos-panel';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { ResultadoLote } from './resultado-lote';

// "Cambiar estado" de uno o de varios reclamos. Recibido, Asignado (pide el agente), En reparación y Resuelto:
// Descartado no está acá porque exige una observación (tiene su propia ventana). Al terminar muestra
// cuáles cambiaron y cuáles se omitieron, con el texto del servidor.
@Component({
  selector: 'app-dialogo-cambiar-estado',
  imports: [Dialogo, ResultadoLote],
  template: `
    <app-dialogo titulo="Cambiar estado" [bloqueado]="enviando()" (cerrar)="cerrar.emit()">
      @if (resultado(); as r) {
        <app-resultado-lote [resultado]="r" modo="cambio" />
        <div class="acciones">
          <button type="button" class="btn pri" (click)="cerrar.emit()">Cerrar</button>
        </div>
      } @else {
        <p class="quien">{{ quien() }}</p>

        <fieldset [attr.aria-describedby]="errorEstado() ? 'error-estado' : null">
          <legend>Estado nuevo</legend>
          @for (e of estados; track e) {
            <label class="opcion">
              <input type="radio" name="estado-nuevo" [value]="e" [checked]="estado() === e" (change)="elegirEstado(e)" />
              {{ nombres[e] }}
            </label>
          }
        </fieldset>
        @if (errorEstado(); as m) {
          <p id="error-estado" class="msg-error" role="alert">{{ m }}</p>
        }

        @if (estado() === 'ASIGNADO') {
          <div>
            <label for="agente-nuevo">Agente</label>
            @if (agentes().length === 0) {
              <p class="msg-error" role="alert">No hay agentes activos. Pedile al administrador que dé de alta uno en Usuarios.</p>
            } @else {
              <select id="agente-nuevo" [attr.aria-invalid]="errorAgente() ? 'true' : null" [attr.aria-describedby]="errorAgente() ? 'error-agente' : null" (change)="elegirAgente($event)">
                <option value="" [selected]="idAgente() === null">Elegí un agente</option>
                @for (a of agentes(); track a.id) {
                  <option [value]="a.id" [selected]="idAgente() === a.id">{{ a.nombre }}</option>
                }
              </select>
            }
            @if (errorAgente(); as m) {
              <p id="error-agente" class="msg-error" role="alert">{{ m }}</p>
            }
          </div>
        }

        @if (errorEnvio(); as m) {
          <div class="caja-error" role="alert">{{ m }}</div>
        }

        <div class="acciones">
          <button type="button" class="btn pri" [disabled]="enviando()" (click)="confirmar()">{{ enviando() ? 'Cambiando…' : 'Cambiar estado' }}</button>
          <button type="button" class="btn sec" [disabled]="enviando()" (click)="cerrar.emit()">Cancelar</button>
        </div>
      }
    </app-dialogo>
  `,
  styles: `
    :host { display: contents; }
    p { margin: 0; }
    .quien { color: var(--sub); overflow-wrap: anywhere; }
    fieldset { margin: 0; padding: 0; border: 0; display: flex; flex-direction: column; gap: 6px; }
    legend { padding: 0; margin-bottom: 6px; font-weight: 700; }
    .opcion { display: flex; align-items: center; gap: 10px; min-height: 44px; margin: 0; font-weight: 500; font-size: 18px; cursor: pointer; }
    .opcion input { width: 24px; height: 24px; margin: 0; accent-color: var(--p); }
    label[for] { display: block; margin-bottom: 6px; font-weight: 700; }
    select { width: 100%; box-sizing: border-box; height: 52px; padding: 0 14px; border: 2px solid var(--borde); border-radius: 12px; background: #fff; font: inherit; font-size: 18px; color: var(--tinta); }
    .msg-error { margin-top: 6px; color: var(--error); font-weight: 500; font-size: 16px; }
    .caja-error { padding: 12px 14px; border: 3px solid var(--error); border-radius: 14px; background: var(--error-fondo); overflow-wrap: anywhere; }
    .acciones { display: flex; gap: 10px; flex-wrap: wrap; }
    .btn { min-height: 52px; padding: 0 22px; border: 2px solid transparent; border-radius: 16px; font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; }
    .btn.pri { background: var(--p); color: #fff; }
    .btn.sec { background: #fff; color: var(--p); border-color: var(--p); }
    .btn:disabled { opacity: 0.6; cursor: default; }
  `,
})
export class DialogoCambiarEstado {
  readonly reclamos = input.required<readonly ReclamoElegido[]>();
  readonly agentes = input<readonly AgenteDto[]>([]);
  readonly cerrar = output<void>();
  // Se emitió el resultado: la pantalla de atrás recarga sus datos sin esperar a que se cierre la ventana.
  readonly aplicado = output<ResultadoEnLoteDto>();

  private readonly api = inject(ReclamosPanelService);

  protected readonly estados = ESTADOS_DE_CAMBIO;
  protected readonly nombres = NOMBRE_ESTADO;
  protected readonly estado = signal<EstadoDeCambio | null>(null);
  protected readonly idAgente = signal<number | null>(null);
  protected readonly intentado = signal(false);
  protected readonly enviando = signal(false);
  protected readonly errorEnvio = signal<string | null>(null);
  protected readonly resultado = signal<ResultadoEnLoteDto | null>(null);

  protected readonly quien = computed(() => {
    const lista = this.reclamos();
    if (lista.length === 1) {
      return `Reclamo n.º ${lista[0].numero} (ahora: ${NOMBRE_ESTADO[lista[0].estado]})`;
    }
    return `${lista.length} reclamos: ${lista.map((r) => `n.º ${r.numero}`).join(', ')}`;
  });
  protected readonly errorEstado = computed(() => (this.intentado() && !this.estado() ? 'Elegí el estado nuevo.' : null));
  protected readonly errorAgente = computed(() =>
    this.intentado() && this.estado() === 'ASIGNADO' && this.idAgente() === null && this.agentes().length > 0 ? 'Elegí a qué agente se lo asignás.' : null,
  );

  protected elegirEstado(e: EstadoDeCambio): void {
    this.estado.set(e);
    this.errorEnvio.set(null);
  }

  protected elegirAgente(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.idAgente.set(valor ? Number(valor) : null);
    this.errorEnvio.set(null);
  }

  protected confirmar(): void {
    this.intentado.set(true);
    const estado = this.estado();
    if (this.enviando() || !estado || this.errorAgente() || (estado === 'ASIGNADO' && this.idAgente() === null)) {
      return;
    }
    this.enviando.set(true);
    this.errorEnvio.set(null);
    this.api
      .cambiarEstado({
        numeros: this.reclamos().map((r) => r.numero),
        estado,
        ...(estado === 'ASIGNADO' ? { idAgente: this.idAgente() } : {}),
      })
      .subscribe({
        next: (r) => {
          this.enviando.set(false);
          this.resultado.set(r);
          this.aplicado.emit(r);
        },
        error: (e: unknown) => {
          this.enviando.set(false);
          this.errorEnvio.set(mensajeDeError(e));
        },
      });
  }
}
