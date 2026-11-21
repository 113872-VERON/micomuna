import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto, NOMBRES_ROL, RolDeComuna } from '../../../auth/modelos';
import { TemaComunaService } from '../../../tema/tema-comuna.service';

// Marco del panel de una comuna: barra superior con el logo y el nombre de la comuna, el menú y quién ingresó.
// Mismo esquema que el panel del superadmin, pero con la marca y el color de la comuna.
@Component({
  selector: 'app-panel-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="top">
      <div class="marca">
        @if (mostrarLogo()) {
          <div class="logo"><img [src]="urlLogo()" alt="" (error)="logoFallo.set(true)" /></div>
        }
        <div class="nombre"><small>Comuna de</small><b>{{ comuna().nombre }}</b></div>
      </div>
      <nav aria-label="Panel de la comuna">
        <a [routerLink]="['/', slug(), 'panel', 'usuarios']" routerLinkActive="on" ariaCurrentWhenActive="page">Usuarios</a>
      </nav>
      <div class="yo">
        <div class="av" aria-hidden="true">{{ inicial() }}</div>
        <div class="quien"><b>{{ auth.usuario()?.nombre }}</b><small>{{ nombreRol() }}</small></div>
        <button type="button" class="salir" (click)="salir()">Salir</button>
      </div>
    </header>
    <main class="cont panel"><router-outlet /></main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; background: var(--fondo); color: var(--tinta); font-size: 18px; line-height: 1.3; }
    .top { background: var(--p); color: #fff; display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; padding: 14px 28px; }
    .marca { display: flex; gap: 12px; align-items: center; min-width: 0; }
    .logo { flex: none; width: 48px; height: 48px; box-sizing: border-box; padding: 4px; background: #fff; border-radius: 14px; display: flex; align-items: center; justify-content: center; }
    .logo img { max-width: 100%; max-height: 100%; object-fit: contain; display: block; }
    .nombre { min-width: 0; }
    .nombre small { display: block; font-size: 15px; font-weight: 500; }
    .nombre b { display: block; font-size: 24px; line-height: 1.1; overflow-wrap: anywhere; }
    nav { display: flex; gap: 6px; flex: 1; }
    nav a { min-height: 44px; box-sizing: border-box; display: inline-flex; align-items: center; padding: 0 18px; border-radius: 12px; font-weight: 700; color: #fff; text-decoration: none; touch-action: manipulation; }
    nav a.on { background: #fff; color: var(--p); }
    nav a:focus-visible, .salir:focus-visible { outline: 4px solid #fff; outline-offset: 2px; }
    .yo { display: flex; gap: 12px; align-items: center; min-width: 0; }
    .av { flex: none; width: 46px; height: 46px; border-radius: 50%; background: #fff; color: var(--p); font-weight: 700; font-size: 22px; display: flex; align-items: center; justify-content: center; }
    .quien { min-width: 0; }
    .quien b { display: block; font-size: 17px; overflow-wrap: anywhere; }
    .quien small { font-size: 15px; }
    .salir { flex: none; min-height: 44px; padding: 0 16px; border: 2px solid #fff; border-radius: 12px; background: transparent; color: #fff; font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; }
    .cont { flex: 1; width: 100%; max-width: 1240px; box-sizing: border-box; margin: 0 auto; padding: 24px 28px 32px; display: flex; flex-direction: column; gap: 16px; }
    @media (max-width: 640px) {
      .top { padding: 12px 16px; }
      .marca { flex: 1 1 100%; }
      .nombre b { font-size: 21px; }
      .cont { padding: 20px 16px 28px; }
    }
  `,
})
export class PanelLayout {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly tema = inject(TemaComunaService);

  protected readonly logoFallo = signal(false);
  protected readonly mostrarLogo = computed(() => this.comuna().tieneLogo && !this.logoFallo());
  protected readonly urlLogo = computed(() => `/api/comunas/${encodeURIComponent(this.comuna().slug)}/logo`);
  protected readonly nombreRol = computed(() => {
    const rol = this.auth.usuario()?.rol;
    return rol && rol !== 'SUPERADMIN' ? NOMBRES_ROL[rol as RolDeComuna] : '';
  });

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => this.tema.restablecer());
  }

  protected inicial(): string {
    // Array.from separa por caracteres reales: una tilde o un emoji no quedan partidos.
    return (Array.from((this.auth.usuario()?.nombre ?? '').trim())[0] ?? '?').toUpperCase();
  }

  protected salir(): void {
    this.auth.cerrarSesion();
    void this.router.navigateByUrl(`/${encodeURIComponent(this.slug())}/login`);
  }
}
