import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FotosDeReclamos, OrigenDeFotos } from '../../reclamos/fotos-de-reclamos';

// Una foto de un reclamo del vecino. La API pide el token, así que no sirve un <img src> directo:
// `FotosDeReclamos` la baja con HttpClient como blob (una sola vez por pantalla) y entrega una URL de
// objeto que ese servicio revoca al cerrar la pantalla o la sesión. Si no carga, muestra un recuadro con
// texto en lugar de una imagen rota.
@Component({
  selector: 'app-foto-reclamo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (url(); as src) {
      <img [src]="src" [alt]="alt()" />
    } @else if (fallo()) {
      <span class="hueco" role="img" [attr.aria-label]="alt() + ' (no se pudo cargar)'">No se pudo cargar la foto</span>
    } @else {
      <span class="hueco" role="img" [attr.aria-label]="'Cargando: ' + alt()"></span>
    }
  `,
  styles: `
    :host { display: block; overflow: hidden; background: #e4e7eb; }
    img { display: block; width: 100%; height: 100%; object-fit: cover; }
    .hueco { display: flex; width: 100%; height: 100%; min-height: 64px; box-sizing: border-box; align-items: center; justify-content: center; padding: 8px; text-align: center; font-size: 14px; color: #3e4c59; }
  `,
})
export class FotoReclamo {
  readonly numero = input.required<number>();
  readonly idFoto = input.required<number>();
  readonly alt = input('Foto del reclamo');
  // Del vecino (por defecto) o del panel del personal.
  readonly origen = input<OrigenDeFotos>('mios');

  protected readonly url = signal<string | null>(null);
  protected readonly fallo = signal(false);

  private readonly fotos = inject(FotosDeReclamos);

  constructor() {
    effect((alLimpiar) => {
      this.url.set(null);
      this.fallo.set(false);
      const sub = this.fotos.url(this.numero(), this.idFoto(), this.origen()).subscribe({
        next: (u) => this.url.set(u),
        error: () => this.fallo.set(true),
      });
      alLimpiar(() => sub.unsubscribe());
    });
  }
}
