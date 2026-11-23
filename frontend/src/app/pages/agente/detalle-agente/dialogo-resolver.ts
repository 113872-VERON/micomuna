import { Component, OnDestroy, inject, input, output, signal } from '@angular/core';
import { codigoDeError, mensajeDeAgente } from '../../../agente/errores-agente';
import { MAX_NOTA, ReclamoAgenteDetalleDto } from '../../../agente/modelos-agente';
import { ReclamosAgenteService } from '../../../agente/reclamos-agente.service';
import { Dialogo } from '../../../compartido/dialogo/dialogo';
import { ACHICAR_FOTO } from '../../../reclamos/achicar-foto';

// "Marcar como resuelto": una nota (hasta 1000 caracteres) y una foto del trabajo, las dos opcionales. La foto se
// achica en el teléfono antes de subirla, igual que al reportar. La nota la lee el vecino.
@Component({
  selector: 'app-dialogo-resolver',
  imports: [Dialogo],
  template: `
    <app-dialogo titulo="Marcar como resuelto" [bloqueado]="enviando()" (cerrar)="cerrar.emit()">
      <p class="quien">Reclamo n.º {{ numero() }}</p>

      <div>
        <label for="nota">Nota de lo que se hizo (opcional)</label>
        <textarea id="nota" rows="4" [attr.maxlength]="maximo" [value]="nota()" [attr.aria-describedby]="'ayuda-nota'" (input)="escribir($event)"></textarea>
        <p id="ayuda-nota" class="ayuda">
          La nota la lee el vecino: no pongas datos de otras personas.
          <span class="contador" [class.lleno]="nota().length >= maximo">{{ nota().length }}/{{ maximo }}</span>
        </p>
      </div>

      <div class="foto-bloque">
        <b id="titulo-foto">Foto del trabajo (opcional)</b>
        <input #camara class="oculto" type="file" accept="image/*" capture="environment" tabindex="-1" aria-hidden="true" (change)="elegir($event)" />
        <input #galeria class="oculto" type="file" accept="image/*" tabindex="-1" aria-hidden="true" (change)="elegir($event)" />
        @if (foto(); as f) {
          <img class="vista" [src]="f.url" alt="La foto del trabajo que elegiste" />
          <button type="button" class="btn sec" [disabled]="enviando()" (click)="quitar()">Quitar la foto</button>
        } @else {
          <div class="botones">
            <button type="button" class="btn sec" [disabled]="enviando() || preparando()" (click)="camara.click()">Sacar una foto</button>
            <button type="button" class="btn sec" [disabled]="enviando() || preparando()" (click)="galeria.click()">Elegir una foto del teléfono</button>
          </div>
        }
        <div role="status">
          @if (preparando()) {
            <p class="ayuda">Preparando la foto…</p>
          }
        </div>
        @if (fotoIlegible()) {
          <p class="msg-error" role="alert">No pudimos usar esa foto. Puede que el archivo no sea una foto o que esté dañado. Sacá otra o elegí una distinta.</p>
        }
      </div>

      @if (error(); as m) {
        <div class="caja-error" role="alert">{{ m }}</div>
      }

      <div class="acciones">
        <button type="button" class="btn pri" [disabled]="enviando() || preparando()" (click)="resolver()">{{ enviando() ? 'Guardando…' : 'Resuelto' }}</button>
        <button type="button" class="btn cancelar" [disabled]="enviando()" (click)="cerrar.emit()">Cancelar</button>
      </div>
    </app-dialogo>
  `,
  styles: `
    :host { display: contents; }
    p { margin: 0; }
    .quien { color: var(--sub); }
    label { display: block; margin-bottom: 6px; font-weight: 700; }
    textarea { width: 100%; box-sizing: border-box; padding: 10px 14px; border: 2px solid var(--borde); border-radius: 12px; font: inherit; font-size: 18px; color: var(--tinta); resize: vertical; }
    .ayuda { display: flex; justify-content: space-between; gap: 12px; margin-top: 6px; font-size: 15px; color: var(--sub); }
    .contador { flex: none; font-variant-numeric: tabular-nums; }
    .contador.lleno { font-weight: 700; color: var(--tinta); }
    .foto-bloque { display: flex; flex-direction: column; gap: 10px; }
    .oculto { display: none; }
    .botones { display: flex; flex-direction: column; gap: 10px; }
    .vista { display: block; width: 100%; max-height: 260px; object-fit: contain; border-radius: 14px; background: #e4e7eb; }
    .msg-error { margin-top: 4px; color: var(--error); font-weight: 500; font-size: 16px; }
    .caja-error { padding: 12px 14px; border: 3px solid var(--error); border-radius: 14px; background: var(--error-fondo); overflow-wrap: anywhere; }
    .acciones { display: flex; flex-direction: column; gap: 10px; }
    .btn { min-height: 56px; padding: 0 22px; border: 2px solid transparent; border-radius: 16px; font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; user-select: none; }
    .btn.pri { background: var(--p); color: #fff; }
    .btn.sec { background: #fff; color: var(--p); border-color: var(--p); }
    .btn.cancelar { background: #fff; color: var(--error); border-color: var(--error); }
    .btn:disabled { opacity: 0.6; cursor: default; }
  `,
})
export class DialogoResolver implements OnDestroy {
  readonly numero = input.required<number>();
  readonly cerrar = output<void>();
  // El detalle actualizado que devolvió el servidor.
  readonly resuelto = output<ReclamoAgenteDetalleDto>();
  // El servidor dice que el reclamo ya cambió (otro estado u otra persona): la pantalla de atrás lo vuelve a pedir.
  readonly desactualizado = output<void>();

  private readonly api = inject(ReclamosAgenteService);
  private readonly achicar = inject(ACHICAR_FOTO);

  protected readonly maximo = MAX_NOTA;
  protected readonly nota = signal('');
  protected readonly foto = signal<{ blob: Blob; url: string } | null>(null);
  protected readonly preparando = signal(false);
  protected readonly fotoIlegible = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  private cerrado = false;

  protected escribir(evento: Event): void {
    this.nota.set((evento.target as HTMLTextAreaElement).value.slice(0, MAX_NOTA));
  }

  protected async elegir(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (!archivo) {
      return;
    }
    this.fotoIlegible.set(false);
    this.preparando.set(true);
    try {
      const achicada = await this.achicar(archivo);
      if (this.cerrado) {
        return;
      }
      this.quitar();
      this.foto.set({ blob: achicada, url: URL.createObjectURL(achicada) });
    } catch {
      this.fotoIlegible.set(true);
    } finally {
      this.preparando.set(false);
    }
  }

  protected quitar(): void {
    const f = this.foto();
    if (f) {
      URL.revokeObjectURL(f.url);
    }
    this.foto.set(null);
    this.fotoIlegible.set(false);
  }

  protected resolver(): void {
    if (this.enviando() || this.preparando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.api.resolver(this.numero(), this.nota(), this.foto()?.blob ?? null).subscribe({
      next: (detalle) => {
        this.enviando.set(false);
        this.resuelto.emit(detalle);
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeAgente(e));
        const codigo = codigoDeError(e);
        if (codigo === 'TRANSICION_INVALIDA' || codigo === 'CONFLICTO') {
          this.desactualizado.emit();
        }
      },
    });
  }

  ngOnDestroy(): void {
    this.cerrado = true;
    const f = this.foto();
    if (f) {
      URL.revokeObjectURL(f.url);
    }
  }
}
