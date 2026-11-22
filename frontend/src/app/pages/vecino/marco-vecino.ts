import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { TemaComunaService } from '../../tema/tema-comuna.service';

// Marco de las pantallas del vecino con sesión (inicio y detalle de reclamos): una barra chica con la marca
// de la comuna y "Salir", y una columna angosta pensada para el celular también en la computadora.
@Component({
  selector: 'app-marco-vecino',
  imports: [RouterOutlet],
  template: `
    <header class="barra">
      <div class="marca">
        @if (mostrarLogo()) {
          <div class="logo"><img [src]="urlLogo()" alt="" (error)="logoFallo.set(true)" /></div>
        }
        <div class="nombre"><small>Comuna de</small><b>{{ comuna().nombre }}</b></div>
      </div>
      <button type="button" class="salir" (click)="salir()">Salir</button>
    </header>
    <main class="columna"><router-outlet /></main>
  `,
  styles: `
    :host { display: block; min-height: 100dvh; background: var(--fondo); color: var(--tinta); font-size: 19px; line-height: 1.3; }
    .barra { background: var(--p); color: #fff; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: calc(10px + env(safe-area-inset-top, 0px)) 16px 10px; }
    .marca { display: flex; gap: 12px; align-items: center; min-width: 0; }
    .logo { flex: none; width: 44px; height: 44px; box-sizing: border-box; padding: 4px; background: #fff; border-radius: 12px; display: flex; align-items: center; justify-content: center; }
    .logo img { max-width: 100%; max-height: 100%; object-fit: contain; display: block; }
    .nombre { min-width: 0; }
    .nombre small { display: block; font-size: 14px; font-weight: 500; }
    .nombre b { display: block; font-size: 20px; line-height: 1.1; overflow-wrap: anywhere; }
    .salir { flex: none; min-height: 44px; padding: 0 16px; border: 2px solid #fff; border-radius: 12px; background: transparent; color: #fff; font: inherit; font-size: 17px; font-weight: 700; cursor: pointer; touch-action: manipulation; user-select: none; }
    .salir:focus-visible { outline: 4px solid #fff; outline-offset: 2px; }
    .columna { max-width: 520px; margin: 0 auto; box-sizing: border-box; padding: 20px 16px calc(32px + env(safe-area-inset-bottom, 0px)); display: flex; flex-direction: column; gap: 20px; }
  `,
})
export class MarcoVecino {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly tema = inject(TemaComunaService);

  protected readonly logoFallo = signal(false);
  protected readonly mostrarLogo = computed(() => this.comuna().tieneLogo && !this.logoFallo());
  protected readonly urlLogo = computed(() => `/api/comunas/${encodeURIComponent(this.comuna().slug)}/logo`);

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => this.tema.restablecer());
  }

  protected salir(): void {
    this.auth.cerrarSesion();
    void this.router.navigateByUrl(`/${encodeURIComponent(this.slug())}/login`);
  }
}
