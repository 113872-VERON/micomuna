import { Component, computed, inject, input, output, signal } from '@angular/core';
import { mensajeDeError } from '../../../auth/errores';
import { Dialogo } from '../../../compartido/dialogo/dialogo';
import { MotivoDescarte } from '../../../reclamos/modelos';
import { ESTADOS_CERRADOS, MAX_OBSERVACION, ReclamoElegido, ResultadoEnLoteDto, sePuedeDescartar } from '../../../reclamos/modelos-panel';
import { TEXTO_MOTIVO } from '../../../reclamos/seguimiento';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { ResultadoLote } from './resultado-lote';

const MOTIVOS = Object.keys(TEXTO_MOTIVO) as MotivoDescarte[];

// Dice cuáles de los elegidos no se descartan: los que están en reparación y los ya cerrados (resueltos o descartados).
// Ejemplo: "Descartar aplica a 1 de 3: el n.º 3 está en reparación y el n.º 4 ya está cerrado."
export function textoDeAplicables(elegidos: readonly ReclamoElegido[]): string | null {
  const enReparacion = elegidos.filter((r) => r.estado === 'EN_REPARACION').map((r) => r.numero);
  const cerrados = elegidos.filter((r) => ESTADOS_CERRADOS.includes(r.estado)).map((r) => r.numero);
  if (enReparacion.length === 0 && cerrados.length === 0) {
    return null;
  }
  const aplican = elegidos.length - enReparacion.length - cerrados.length;
  const grupo = (numeros: number[], singular: string, plural: string): string => {
    if (numeros.length > 3) {
      return `${numeros.length} ${plural}`;
    }
    const lista = numeros.map((n) => `n.º ${n}`).join(', ').replace(/, ([^,]*)$/, ' y $1');
    return `${numeros.length === 1 ? 'el' : 'los'} ${lista} ${numeros.length === 1 ? singular : plural}`;
  };
  const partes: string[] = [];
  if (enReparacion.length > 0) {
    partes.push(grupo(enReparacion, 'está en reparación', 'están en reparación'));
  }
  if (cerrados.length > 0) {
    partes.push(grupo(cerrados, 'ya está cerrado', 'ya están cerrados'));
  }
  return `Descartar aplica a ${aplican} de ${elegidos.length}: ${partes.join(' y ')}.`;
}

// "Descartar" uno o varios reclamos con un mismo motivo y observación. El vecino lee la observación.
// Los resueltos, descartados y en reparación quedan afuera y la ventana lo explica antes de confirmar.
@Component({
  selector: 'app-dialogo-descartar',
  imports: [Dialogo, ResultadoLote],
  template: `
    <app-dialogo titulo="Descartar" [bloqueado]="enviando()" (cerrar)="cerrar.emit()">
      @if (resultado(); as r) {
        <app-resultado-lote [resultado]="r" modo="descarte" />
        <div class="acciones">
          <button type="button" class="btn pri" (click)="cerrar.emit()">Cerrar</button>
        </div>
      } @else {
        <p class="quien">{{ quien() }}</p>

        @if (explicacion(); as t) {
          <div class="aviso alerta" role="status">{{ t }}</div>
        }

        @if (aplicables().length > 0) {
          <fieldset [attr.aria-describedby]="errorMotivo() ? 'error-motivo' : null">
            <legend>Motivo</legend>
            @for (m of motivos; track m) {
              <label class="opcion">
                <input type="radio" name="motivo" [value]="m" [checked]="motivo() === m" (change)="elegirMotivo(m)" />
                {{ textos[m] }}
              </label>
            }
          </fieldset>
          @if (errorMotivo(); as m) {
            <p id="error-motivo" class="msg-error" role="alert">{{ m }}</p>
          }

          @if (motivo() === 'DUPLICADO') {
            <div>
              <label for="original">Número del reclamo original</label>
              <input id="original" type="text" inputmode="numeric" autocomplete="off" [value]="original()" [attr.aria-invalid]="errorOriginal() ? 'true' : null"
                [attr.aria-describedby]="errorOriginal() ? 'error-original' : null" (input)="escribirOriginal($event)" />
              @if (errorOriginal(); as m) {
                <p id="error-original" class="msg-error" role="alert">{{ m }}</p>
              }
            </div>
          }

          <div>
            <label for="observacion">Observación{{ motivo() === 'DUPLICADO' ? ' (opcional)' : '' }}</label>
            <textarea id="observacion" rows="4" [attr.maxlength]="maximo" [value]="observacion()" [attr.aria-invalid]="errorObservacion() ? 'true' : null"
              [attr.aria-describedby]="'ayuda-observacion' + (errorObservacion() ? ' error-observacion' : '')" (input)="escribirObservacion($event)"></textarea>
            <p id="ayuda-observacion" class="ayuda">
              El vecino lee este texto: no pongas datos de otras personas.
              <span class="contador" [class.lleno]="observacion().length >= maximo">{{ observacion().length }}/{{ maximo }}</span>
            </p>
            @if (errorObservacion(); as m) {
              <p id="error-observacion" class="msg-error" role="alert">{{ m }}</p>
            }
          </div>
        }

        @if (errorEnvio(); as m) {
          <div class="caja-error" role="alert">{{ m }}</div>
        }

        <div class="acciones">
          <button type="button" class="btn peligro" [disabled]="enviando() || aplicables().length === 0" (click)="confirmar()">
            {{ enviando() ? 'Descartando…' : aplicables().length === 1 ? 'Descartar reclamo' : 'Descartar ' + aplicables().length + ' reclamos' }}
          </button>
          <button type="button" class="btn sec" [disabled]="enviando()" (click)="cerrar.emit()">Cancelar</button>
        </div>
      }
    </app-dialogo>
  `,
  styles: `
    :host { display: contents; }
    p { margin: 0; }
    .quien { color: var(--sub); overflow-wrap: anywhere; }
    fieldset { margin: 0; padding: 0; border: 0; display: flex; flex-direction: column; gap: 4px; }
    legend { padding: 0; margin-bottom: 6px; font-weight: 700; }
    .opcion { display: flex; align-items: center; gap: 10px; min-height: 44px; margin: 0; font-weight: 500; font-size: 18px; cursor: pointer; }
    .opcion input { width: 24px; height: 24px; margin: 0; accent-color: var(--p); }
    label[for] { display: block; margin-bottom: 6px; font-weight: 700; }
    input[type='text'] { width: 100%; box-sizing: border-box; height: 52px; padding: 0 14px; border: 2px solid var(--borde); border-radius: 12px; font: inherit; font-size: 18px; color: var(--tinta); }
    textarea { width: 100%; box-sizing: border-box; padding: 10px 14px; border: 2px solid var(--borde); border-radius: 12px; font: inherit; font-size: 18px; color: var(--tinta); resize: vertical; }
    [aria-invalid='true'] { border-color: var(--error); background: var(--error-fondo); }
    .ayuda { display: flex; justify-content: space-between; gap: 12px; margin-top: 6px; font-size: 15px; color: var(--sub); }
    .contador { flex: none; font-variant-numeric: tabular-nums; }
    .contador.lleno { font-weight: 700; color: var(--tinta); }
    .msg-error { margin-top: 6px; color: var(--error); font-weight: 500; font-size: 16px; }
    .caja-error { padding: 12px 14px; border: 3px solid var(--error); border-radius: 14px; background: var(--error-fondo); overflow-wrap: anywhere; }
    .aviso.alerta { padding: 12px 14px; border-radius: 14px; background: #fff4d6; border: 2px solid #e0a81b; color: #3d2a00; font-size: 17px; }
    .acciones { display: flex; gap: 10px; flex-wrap: wrap; }
    .btn { min-height: 52px; padding: 0 22px; border: 2px solid transparent; border-radius: 16px; font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; }
    .btn.pri { background: var(--p); color: #fff; }
    .btn.peligro { background: var(--error); color: #fff; }
    .btn.sec { background: #fff; color: var(--p); border-color: var(--p); }
    .btn:disabled { opacity: 0.6; cursor: default; }
  `,
})
export class DialogoDescartar {
  readonly reclamos = input.required<readonly ReclamoElegido[]>();
  readonly cerrar = output<void>();
  readonly aplicado = output<ResultadoEnLoteDto>();

  private readonly api = inject(ReclamosPanelService);

  protected readonly motivos = MOTIVOS;
  protected readonly textos = TEXTO_MOTIVO;
  protected readonly maximo = MAX_OBSERVACION;
  protected readonly motivo = signal<MotivoDescarte | null>(null);
  protected readonly observacion = signal('');
  protected readonly original = signal('');
  protected readonly intentado = signal(false);
  protected readonly enviando = signal(false);
  protected readonly errorEnvio = signal<string | null>(null);
  protected readonly resultado = signal<ResultadoEnLoteDto | null>(null);

  protected readonly aplicables = computed(() => this.reclamos().filter((r) => sePuedeDescartar(r.estado)));
  protected readonly explicacion = computed(() => textoDeAplicables(this.reclamos()));
  protected readonly quien = computed(() => {
    const lista = this.reclamos();
    return lista.length === 1 ? `Reclamo n.º ${lista[0].numero}` : `${lista.length} reclamos seleccionados`;
  });

  protected readonly errorMotivo = computed(() => (this.intentado() && !this.motivo() ? 'Elegí un motivo.' : null));
  protected readonly errorObservacion = computed(() => {
    if (!this.intentado() || this.motivo() === null || this.motivo() === 'DUPLICADO') {
      return null;
    }
    return this.observacion().trim() ? null : 'Escribí por qué lo descartás. El vecino lo va a leer.';
  });
  protected readonly errorOriginal = computed(() => {
    if (!this.intentado() || this.motivo() !== 'DUPLICADO') {
      return null;
    }
    const texto = this.original().trim();
    if (!/^\d{1,9}$/.test(texto) || Number(texto) < 1) {
      return 'Escribí el número del reclamo original.';
    }
    return this.aplicables().some((r) => r.numero === Number(texto)) ? 'El original no puede ser uno de los reclamos que estás descartando.' : null;
  });

  protected elegirMotivo(m: MotivoDescarte): void {
    this.motivo.set(m);
    this.errorEnvio.set(null);
  }

  protected escribirObservacion(evento: Event): void {
    this.observacion.set((evento.target as HTMLTextAreaElement).value.slice(0, MAX_OBSERVACION));
  }

  protected escribirOriginal(evento: Event): void {
    this.original.set((evento.target as HTMLInputElement).value);
  }

  protected confirmar(): void {
    this.intentado.set(true);
    const motivo = this.motivo();
    if (this.enviando() || !motivo || this.aplicables().length === 0 || this.errorObservacion() || this.errorOriginal()) {
      return;
    }
    const observacion = this.observacion().trim();
    this.enviando.set(true);
    this.errorEnvio.set(null);
    this.api
      .descartar({
        // Los que no se pueden descartar no viajan: ya se explicó que quedan afuera.
        numeros: this.aplicables().map((r) => r.numero),
        motivo,
        observacion: observacion || null,
        ...(motivo === 'DUPLICADO' ? { numeroOriginal: Number(this.original().trim()) } : {}),
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
