import { Component, ElementRef, OnDestroy, afterNextRender, input, output, viewChild } from '@angular/core';

let contador = 0;

// Ventana modal del panel: un <dialog> nativo, que atrapa el foco, deja el fondo inactivo y se cierra con
// Escape. El foco arranca en el título (el lector de pantalla anuncia de qué trata) y, al cerrarse, vuelve
// al botón que la abrió. El contenido va adentro, con `ng-content`.
@Component({
  selector: 'app-dialogo',
  template: `
    <dialog #dialogo class="ventana" [attr.aria-labelledby]="idTitulo" (cancel)="cancelar($event)">
      <h2 [id]="idTitulo" tabindex="-1">{{ titulo() }}</h2>
      <ng-content />
    </dialog>
  `,
  styles: `
    .ventana {
      width: min(600px, calc(100vw - 32px));
      max-height: calc(100dvh - 32px);
      box-sizing: border-box;
      padding: 24px;
      border: 0;
      border-radius: 24px;
      background: #fff;
      color: var(--tinta);
      font-size: 18px;
      line-height: 1.3;
      overflow-y: auto;
    }
    .ventana[open] { display: flex; flex-direction: column; gap: 14px; }
    .ventana::backdrop { background: rgb(31 41 51 / 55%); }
    h2 { margin: 0; font-size: 26px; line-height: 1.2; }
    h2:focus { outline: none; }
  `,
})
export class Dialogo implements OnDestroy {
  readonly titulo = input.required<string>();
  // Mientras algo se está enviando no se deja cerrar con Escape.
  readonly bloqueado = input(false);
  readonly cerrar = output<void>();

  protected readonly idTitulo = `dialogo-titulo-${++contador}`;
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');
  private readonly anterior = document.activeElement as HTMLElement | null;

  constructor() {
    afterNextRender(() => {
      const d = this.dialogo().nativeElement;
      // Un navegador viejo (y el entorno de pruebas) puede no tener showModal: se muestra igual.
      if (typeof d.showModal === 'function') {
        d.showModal();
      } else {
        d.setAttribute('open', '');
      }
      d.querySelector<HTMLElement>('h2')?.focus();
    });
  }

  protected cancelar(evento: Event): void {
    evento.preventDefault();
    if (!this.bloqueado()) {
      this.cerrar.emit();
    }
  }

  ngOnDestroy(): void {
    const d = this.dialogo().nativeElement;
    if (typeof d.close === 'function' && d.open) {
      d.close();
    }
    if (this.anterior?.isConnected) {
      this.anterior.focus();
    }
  }
}
