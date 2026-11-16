import { Component, DestroyRef, effect, inject, input } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ComunaDto } from '../../auth/modelos';
import { CabeceraComuna } from '../../compartido/cabecera-comuna/cabecera-comuna';
import { TemaComunaService } from '../../tema/tema-comuna.service';

// Marco común de las pantallas de una comuna: la cabecera con su nombre, logo y color, y la hoja de la pantalla.
@Component({
  selector: 'app-comuna-layout',
  imports: [RouterOutlet, CabeceraComuna],
  template: `
    <div class="pantalla">
      <app-cabecera-comuna [comuna]="comuna()" />
      <main class="hoja"><router-outlet /></main>
    </div>
  `,
  styles: `
    :host { display: block; min-height: 100dvh; background: var(--fondo); }
  `,
})
export class ComunaLayout {
  readonly comuna = input.required<ComunaDto>();

  private readonly tema = inject(TemaComunaService);

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => this.tema.restablecer());
  }
}
