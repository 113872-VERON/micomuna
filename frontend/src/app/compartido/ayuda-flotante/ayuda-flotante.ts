import { DOCUMENT } from '@angular/common';
import { Directive, ElementRef, OnDestroy, booleanAttribute, effect, inject, input } from '@angular/core';

let contador = 0;

// Texto de ayuda flotante para un botón o enlace: aparece al pasar el mouse, al tocarlo y con el foco del teclado,
// y se cierra con Escape (sin mover el foco). El globito va en `body` con posición fija, para que ningún
// contenedor con scroll lo recorte, y se mantiene dentro de la pantalla.
@Directive({
  selector: '[appAyuda]',
  host: {
    '(mouseenter)': 'mostrar()',
    '(mouseleave)': 'ocultar()',
    '(focus)': 'mostrar()',
    '(blur)': 'ocultar()',
    '(click)': 'mostrar()',
    '[attr.aria-describedby]': 'describir() && activa() ? idGlobito : null',
  },
})
export class AyudaFlotante implements OnDestroy {
  readonly appAyuda = input.required<string>();
  // Si el nombre accesible del elemento ya dice lo mismo, no hace falta repetirlo como descripción.
  readonly describir = input(true, { alias: 'appAyudaDescribe', transform: booleanAttribute });

  // Si es falso no hay ayuda (por ejemplo, solo la fila propia la necesita).
  readonly activa = input(true, { alias: 'appAyudaActiva', transform: booleanAttribute });

  protected readonly idGlobito = `ayuda-flotante-${++contador}`;
  private readonly doc = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private globito: HTMLElement | null = null;
  private readonly alEscape = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      this.ocultar();
    }
  };
  private readonly alMoverPagina = () => this.ocultar();

  constructor() {
    // El globito se crea solo si la ayuda está activa, y se saca si deja de estarlo.
    effect(() => {
      if (!this.activa()) {
        this.quitar();
        return;
      }
      if (!this.globito) {
        const g = this.doc.createElement('div');
        g.id = this.idGlobito;
        g.setAttribute('role', 'tooltip');
        g.style.cssText =
          'position:fixed;left:0;top:0;z-index:1000;visibility:hidden;pointer-events:none;box-sizing:border-box;' +
          'max-width:min(280px,calc(100vw - 16px));padding:8px 12px;border-radius:10px;background:#1f2933;color:#fff;' +
          'font:500 15px/1.35 Roboto,system-ui,sans-serif;box-shadow:0 4px 14px rgba(0,0,0,.25);overflow-wrap:anywhere;white-space:pre-line;';
        this.doc.body.appendChild(g);
        this.globito = g;
      }
      this.globito.textContent = this.appAyuda();
    });
  }

  protected mostrar(): void {
    const g = this.globito;
    const ventana = this.doc.defaultView;
    if (!g || !ventana || !g.textContent) {
      return;
    }
    g.style.visibility = 'visible';
    const caja = this.host.getBoundingClientRect();
    const ancho = g.offsetWidth;
    const alto = g.offsetHeight;
    const izquierda = Math.min(Math.max(caja.left + caja.width / 2 - ancho / 2, 8), Math.max(8, ventana.innerWidth - ancho - 8));
    // Debajo del elemento; si no entra y hay lugar arriba, arriba.
    let arriba = caja.bottom + 8;
    if (arriba + alto > ventana.innerHeight - 8 && caja.top - alto - 8 >= 8) {
      arriba = caja.top - alto - 8;
    }
    g.style.left = `${izquierda}px`;
    g.style.top = `${arriba}px`;
    this.doc.addEventListener('keydown', this.alEscape);
    ventana.addEventListener('scroll', this.alMoverPagina, { passive: true });
  }

  protected ocultar(): void {
    if (this.globito) {
      this.globito.style.visibility = 'hidden';
    }
    this.doc.removeEventListener('keydown', this.alEscape);
    this.doc.defaultView?.removeEventListener('scroll', this.alMoverPagina);
  }

  ngOnDestroy(): void {
    this.quitar();
  }

  private quitar(): void {
    this.ocultar();
    this.globito?.remove();
    this.globito = null;
  }
}
