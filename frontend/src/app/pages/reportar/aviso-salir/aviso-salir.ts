import { Component, ElementRef, OnDestroy, afterNextRender, input, output, viewChild } from '@angular/core';

// Aviso antes de salir del recorrido con algo cargado. Es un <dialog> modal: atrapa el foco,
// deja el fondo inactivo y se cierra con Escape (que cuenta como "Seguir cargando").
// El foco arranca en "Seguir cargando": la opción que no pierde nada.
@Component({
  selector: 'app-aviso-salir',
  template: `
    <dialog #dialogo class="aviso" aria-labelledby="aviso-salir-titulo" aria-describedby="aviso-salir-texto" (cancel)="cancelar($event)">
      <h2 id="aviso-salir-titulo">{{ enviando() ? 'Tu reclamo se está enviando' : '¿Salir sin enviar?' }}</h2>
      <p id="aviso-salir-texto">
        {{ enviando() ? 'Si salís ahora, no vas a ver el número de tu reclamo.' : 'Se pierde lo que cargaste.' }}
      </p>
      <button type="button" class="btn pri" (click)="seguir.emit()">{{ enviando() ? 'Esperar' : 'Seguir cargando' }}</button>
      <button type="button" class="btn sec" (click)="salir.emit()">Salir</button>
    </dialog>
  `,
  styles: `
    .aviso {
      width: min(440px, calc(100vw - 32px));
      box-sizing: border-box;
      padding: 24px 20px calc(20px + env(safe-area-inset-bottom, 0px));
      border: 0;
      border-radius: 28px;
      background: #fff;
      color: var(--tinta);
      font-size: 20px;
      line-height: 1.3;
      flex-direction: column;
      gap: 14px;
    }
    .aviso[open] { display: flex; }
    .aviso::backdrop { background: rgb(31 41 51 / 55%); }
    h2 { margin: 0; font-size: 26px; line-height: 1.2; }
    p { margin: 0 0 6px; color: var(--sub); }
    .btn {
      width: 100%; height: 56px; display: flex; align-items: center; justify-content: center; box-sizing: border-box;
      border: 0; border-radius: 16px; font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; user-select: none;
      transition: transform 160ms cubic-bezier(0.23, 1, 0.32, 1);
    }
    .btn:active { transform: scale(0.97); }
    .pri { background: var(--p); color: #fff; }
    .sec { background: #fff; color: var(--p); border: 2px solid var(--p); }
    @media (prefers-reduced-motion: reduce) { .btn { transition: none; } }
  `,
})
export class AvisoSalir implements OnDestroy {
  readonly enviando = input(false);
  readonly seguir = output<void>();
  readonly salir = output<void>();

  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');
  // Al cerrarse, el foco vuelve a donde estaba (por ejemplo el botón "Volver").
  private readonly anterior = document.activeElement as HTMLElement | null;

  constructor() {
    afterNextRender(() => {
      const dialogo = this.dialogo().nativeElement;
      // Algún navegador viejo (y el entorno de pruebas) no tiene showModal: se muestra igual.
      if (typeof dialogo.showModal === 'function') {
        dialogo.showModal();
      } else {
        dialogo.setAttribute('open', '');
      }
      dialogo.querySelector<HTMLElement>('.pri')?.focus();
    });
  }

  protected cancelar(evento: Event): void {
    evento.preventDefault();
    this.seguir.emit();
  }

  ngOnDestroy(): void {
    const dialogo = this.dialogo().nativeElement;
    if (typeof dialogo.close === 'function' && dialogo.open) {
      dialogo.close();
    }
    if (this.anterior?.isConnected) {
      this.anterior.focus();
    }
  }
}
