import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { CambioDeEstadoDto, DescarteDto, EstadoReclamo } from '../../reclamos/modelos';
import { fechaDelEstado, pasosDelReclamo, textoDeMotivo } from '../../reclamos/seguimiento';

// Dónde está el reclamo: la línea de 4 pasos (Recibido, Asignado, En reparación, Resuelto) con la fecha
// de cada paso alcanzado, o, si la comuna lo descartó, el motivo en lenguaje claro. Se usa en el inicio
// del vecino y en el detalle. Todo es texto plano: lo que escribe la comuna nunca se interpreta como HTML.
@Component({
  selector: 'app-estado-reclamo',
  imports: [DatePipe, NgTemplateOutlet],
  template: `
    <ng-template #lista let-pasos>
      <ol class="pasos" aria-label="Avance del reclamo">
        @for (paso of pasos; track paso.estado) {
          <li [class]="paso.situacion" [attr.aria-current]="paso.situacion === 'actual' ? 'step' : null">
            <span class="punto" aria-hidden="true">
              @if (paso.situacion === 'hecho') {
                ✓
              } @else if (paso.situacion === 'actual') {
                ●
              }
            </span>
            <span class="texto">
              <b>{{ paso.nombre }}</b>
              <small>
                @if (paso.fecha) {
                  {{ paso.fecha | date: 'dd/MM/yyyy HH:mm' }}
                } @else if (paso.situacion === 'pendiente') {
                  Todavía no
                }
                @if (paso.situacion === 'actual') {
                  <span class="aqui">@if (paso.fecha) { · }Estado actual</span>
                }
              </small>
            </span>
          </li>
        }
      </ol>
    </ng-template>

    @if (estado() === 'DESCARTADO' && descarte()?.motivo === 'DUPLICADO') {
      <div class="duplicado">
        <p class="titulo-dup">Se detectó que este reclamo ya fue reportado</p>
        @if (resumido()) {
          <p>La comuna ya se está ocupando. Seguís el avance acá.</p>
        } @else {
          <p>Otro vecino ya avisó de este mismo problema y la comuna se está ocupando. No hace falta que hagas nada más.</p>
        }
        @if (descarte()?.estadoOriginal === 'DESCARTADO') {
          <p class="original-descartado">La comuna descartó el reclamo que estaba siguiendo. Si el problema sigue, comunicate con ella.</p>
        } @else if (pasosOriginal().length > 0) {
          @if (!resumido()) {
            <h3 class="sigue">Seguí el avance del problema</h3>
          }
          <ng-container *ngTemplateOutlet="lista; context: { $implicit: pasosOriginal() }" />
        }
      </div>
    } @else if (estado() === 'DESCARTADO') {
      <div class="descartado">
        <p class="titulo"><span aria-hidden="true">✕ </span>Descartado</p>
        @if (fechaDescarte(); as fecha) {
          <p class="fecha">{{ fecha | date: 'dd/MM/yyyy HH:mm' }}</p>
        }
        @if (descarte(); as d) {
          <p>{{ motivo() }}</p>
          @if (d.observacion) {
            <p class="observacion"><span class="rotulo">Mensaje de la comuna</span>{{ d.observacion }}</p>
          }
          @if (d.motivo === 'NO_ES_DE_LA_COMUNA') {
            <p>Si creés que sí le corresponde a la comuna, comunicate con ella.</p>
          } @else {
            <p>Si querés saber más, comunicate con la comuna.</p>
          }
          @if (telefono() || whatsapp()) {
            <div class="botones">
              @if (telefono(); as numero) {
                <a class="btn" [href]="'tel:+' + numero">Llamar</a>
              }
              @if (whatsapp(); as numero) {
                <a class="btn" [href]="'https://wa.me/' + numero" target="_blank" rel="noopener noreferrer">WhatsApp<span class="oculto"> (se abre en una pestaña nueva)</span></a>
              }
            </div>
          }
        }
      </div>
    } @else {
      <ng-container *ngTemplateOutlet="lista; context: { $implicit: pasos() }" />
      @if (estado() === 'RESUELTO' && trabajoRealizado()) {
        <div class="trabajo"><span class="rotulo">Lo que se hizo</span>{{ trabajoRealizado() }}</div>
      }
    }
  `,
  styles: `
    :host { display: block; }
    p { margin: 0; }
    .pasos { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    li { position: relative; display: flex; gap: 12px; align-items: flex-start; padding-bottom: 14px; min-width: 0; }
    li:last-child { padding-bottom: 0; }
    /* La línea que une los puntos. */
    li:not(:last-child)::before { content: ''; position: absolute; left: 12px; top: 28px; bottom: 0; width: 3px; background: #cbd2d9; }
    li.hecho:not(:last-child)::before { background: var(--p); }
    .punto { flex: none; width: 28px; height: 28px; box-sizing: border-box; display: flex; align-items: center; justify-content: center; border-radius: 50%; border: 3px solid #9aa5b1; background: #fff; font-size: 15px; font-weight: 700; line-height: 1; color: #fff; }
    .hecho .punto { background: var(--p); border-color: var(--p); }
    .actual .punto { border-color: var(--p); color: var(--p); box-shadow: 0 0 0 4px color-mix(in srgb, var(--p) 25%, white); }
    .texto { min-width: 0; display: flex; flex-direction: column; padding-top: 1px; }
    .texto b { font-size: 19px; line-height: 1.2; }
    .pendiente .texto b { font-weight: 500; color: var(--sub); }
    small { font-size: 16px; color: var(--sub); }
    .aqui { font-weight: 700; color: var(--tinta); }
    .duplicado { display: flex; flex-direction: column; gap: 10px; padding: 12px 14px; border-radius: 14px; background: var(--ayuda); overflow-wrap: anywhere; }
    .titulo-dup { font-size: 20px; font-weight: 700; }
    .sigue { margin: 6px 0 0; font-size: 18px; }
    .descartado { display: flex; flex-direction: column; gap: 6px; padding: 12px 14px; border: 2px solid var(--error); border-radius: 14px; background: var(--error-fondo); overflow-wrap: anywhere; }
    .titulo { font-size: 20px; font-weight: 700; color: var(--error); }
    .fecha { font-size: 16px; color: var(--sub); }
    .botones { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 4px; }
    .btn { flex: 1 1 130px; min-height: 48px; box-sizing: border-box; display: flex; align-items: center; justify-content: center; padding: 0 16px; border: 2px solid var(--p); border-radius: 14px; background: #fff; color: var(--p); font-weight: 700; text-decoration: none; touch-action: manipulation; }
    .oculto { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
    .observacion, .trabajo { white-space: pre-line; overflow-wrap: anywhere; }
    .rotulo { display: block; font-size: 15px; font-weight: 700; color: var(--sub); }
    .trabajo { margin-top: 14px; padding: 12px 14px; border-radius: 14px; background: var(--ayuda); }
  `,
})
export class EstadoReclamoVista {
  readonly estado = input.required<EstadoReclamo>();
  readonly historial = input<readonly CambioDeEstadoDto[]>([]);
  readonly descarte = input<DescarteDto | null>(null);
  // En la tarjeta del inicio: texto corto y sin el título "Seguí el avance del problema".
  readonly resumido = input(false);
  readonly trabajoRealizado = input<string | null>(null);
  // Números de contacto de la comuna (ya validados), para el descarte por "otro motivo" sin explicación.
  readonly telefono = input<string | null>(null);
  readonly whatsapp = input<string | null>(null);

  protected readonly pasos = computed(() => pasosDelReclamo(this.estado(), this.historial()));
  // Los pasos del reclamo que sigue la comuna, con la misma cuenta que un reclamo propio.
  protected readonly pasosOriginal = computed(() => {
    const d = this.descarte();
    return d?.motivo === 'DUPLICADO' && d.estadoOriginal ? pasosDelReclamo(d.estadoOriginal, d.historialOriginal ?? []) : [];
  });
  protected readonly fechaDescarte = computed(() => fechaDelEstado(this.historial(), 'DESCARTADO'));
  protected readonly motivo = computed(() => textoDeMotivo(this.descarte()?.motivo ?? 'OTRO'));
}
