import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { MenuUsuario } from '../../../compartido/menu-usuario/menu-usuario';
import { TemaComunaService } from '../../../tema/tema-comuna.service';

// Marco del panel de una comuna (administrativo y administrador): barra superior con el logo y el nombre de la comuna, el menú y quién ingresó.
// Mismo esquema que el panel del superadmin, pero con la marca y el color de la comuna.
// La barra es una grilla de tres columnas (1fr auto 1fr): la comuna a la izquierda, las opciones realmente al centro y el usuario a la derecha.
@Component({
  selector: 'app-panel-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MenuUsuario],
  template: `
    <header class="top">
      <div class="marca">
        @if (mostrarLogo()) {
          <div class="logo"><img [src]="urlLogo()" alt="" (error)="logoFallo.set(true)" /></div>
        }
        <div class="nombre"><small>Comuna de</small><b>{{ comuna().nombre }}</b></div>
      </div>
      <nav aria-label="Panel de la comuna">
        <a [routerLink]="['/', slug(), 'panel', 'reclamos']" routerLinkActive="on" ariaCurrentWhenActive="page">Reclamos</a>
        <a [routerLink]="['/', slug(), 'panel', 'metricas']" routerLinkActive="on" ariaCurrentWhenActive="page">Métricas</a>
        @if (puedeVerUsuarios()) {
          <a [routerLink]="['/', slug(), 'panel', 'usuarios']" routerLinkActive="on" ariaCurrentWhenActive="page">Usuarios</a>
        }
      </nav>
      <app-menu-usuario class="yo" [zona]="['/', slug(), 'panel']" [alSalir]="urlLogin()" />
    </header>
    <main class="cont panel"><router-outlet /></main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; background: var(--fondo); color: var(--tinta); font-size: 18px; line-height: 1.3; }
    .top { background: var(--p); color: #fff; display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 12px 24px; padding: 14px 28px; }
    .marca { display: flex; gap: 12px; align-items: center; min-width: 0; }
    .logo { flex: none; width: 48px; height: 48px; box-sizing: border-box; padding: 4px; background: #fff; border-radius: 14px; display: flex; align-items: center; justify-content: center; }
    .logo img { max-width: 100%; max-height: 100%; object-fit: contain; display: block; }
    .nombre { min-width: 0; }
    .nombre small { display: block; font-size: 15px; font-weight: 500; }
    .nombre b { display: block; font-size: 24px; line-height: 1.1; overflow-wrap: anywhere; }
    nav { display: flex; gap: 6px; justify-content: center; }
    nav a { min-height: 44px; box-sizing: border-box; display: inline-flex; align-items: center; padding: 0 18px; border-radius: 12px; font-weight: 700; color: #fff; text-decoration: none; touch-action: manipulation; }
    nav a.on { background: #fff; color: var(--p); }
    nav a:focus-visible { outline: 4px solid #fff; outline-offset: 2px; }
    .yo { justify-self: end; }
    .cont { flex: 1; width: 100%; max-width: 1680px; box-sizing: border-box; margin: 0 auto; padding: 24px 28px 32px; display: flex; flex-direction: column; gap: 16px; }
    // Angosto: la comuna y el usuario arriba, las opciones debajo y centradas.
    @media (max-width: 900px) {
      .top { grid-template-columns: minmax(0, 1fr) auto; grid-template-areas: 'marca yo' 'nav nav'; }
      .marca { grid-area: marca; }
      .yo { grid-area: yo; }
      nav { grid-area: nav; }
    }
    @media (max-width: 640px) {
      .top { padding: 12px 16px; gap: 10px 12px; }
      nav a { padding: 0 14px; }
      .nombre b { font-size: 21px; }
      .cont { padding: 20px 16px 28px; }
    }
  `,
})
export class PanelLayout {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  protected readonly auth = inject(AuthService);
  private readonly tema = inject(TemaComunaService);

  protected readonly logoFallo = signal(false);
  protected readonly mostrarLogo = computed(() => this.comuna().tieneLogo && !this.logoFallo());
  protected readonly urlLogo = computed(() => `/api/comunas/${encodeURIComponent(this.comuna().slug)}/logo`);
  protected readonly urlLogin = computed(() => `/${encodeURIComponent(this.slug())}/login`);
  // Solo el administrador de la comuna maneja usuarios; el personal administrativo ve solo los reclamos.
  protected readonly puedeVerUsuarios = computed(() => this.auth.usuario()?.rol === 'ADMIN_COMUNA');

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => this.tema.restablecer());
  }
}
