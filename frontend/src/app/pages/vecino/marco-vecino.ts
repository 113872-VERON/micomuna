import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { MenuUsuario } from '../../compartido/menu-usuario/menu-usuario';
import { TemaComunaService } from '../../tema/tema-comuna.service';

// Marco de las pantallas del vecino y del agente con sesión (inicio, detalle de reclamos, mis datos): una barra chica con
// la marca de la comuna y el menú de quien ingresó, y una columna angosta pensada para el celular también en la computadora.
@Component({
  selector: 'app-marco-vecino',
  imports: [RouterOutlet, MenuUsuario],
  template: `
    <header class="barra">
      <div class="marca">
        @if (mostrarLogo()) {
          <div class="logo"><img [src]="urlLogo()" alt="" (error)="logoFallo.set(true)" /></div>
        }
        <div class="nombre"><small>Comuna de</small><b>{{ comuna().nombre }}</b></div>
      </div>
      <app-menu-usuario [zona]="zona()" [alSalir]="urlLogin()" />
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
    app-menu-usuario { flex: none; max-width: 55%; }
    .columna { max-width: 520px; margin: 0 auto; box-sizing: border-box; padding: 20px 16px calc(32px + env(safe-area-inset-bottom, 0px)); display: flex; flex-direction: column; gap: 20px; }
  `,
})
export class MarcoVecino {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly auth = inject(AuthService);
  private readonly tema = inject(TemaComunaService);

  protected readonly logoFallo = signal(false);
  protected readonly mostrarLogo = computed(() => this.comuna().tieneLogo && !this.logoFallo());
  protected readonly urlLogo = computed(() => `/api/comunas/${encodeURIComponent(this.comuna().slug)}/logo`);
  protected readonly urlLogin = computed(() => `/${encodeURIComponent(this.slug())}/login`);
  // "Mis datos" y "Cambiar contraseña" viven en la zona de cada uno: la del agente o la del vecino.
  protected readonly zona = computed(() => (this.auth.usuario()?.rol === 'AGENTE' ? ['/', this.slug(), 'agente'] : ['/', this.slug()]));

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => this.tema.restablecer());
  }
}
