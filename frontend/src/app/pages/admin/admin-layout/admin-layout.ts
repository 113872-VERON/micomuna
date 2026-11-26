import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuUsuario } from '../../../compartido/menu-usuario/menu-usuario';

// Marco del panel del superadmin: barra superior con la marca, el menú y quién ingresó.
// Mismo criterio que la barra del panel: la marca a la izquierda, la opción al centro y el usuario a la derecha.
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MenuUsuario],
  template: `
    <header class="top">
      <div class="marca">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 22s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          <circle cx="12" cy="10" r="2.5" stroke="currentColor" stroke-width="2" />
        </svg>
        <div><small>Plataforma</small><b>MiComuna</b></div>
      </div>
      <nav aria-label="Principal">
        <a routerLink="/admin/comunas" routerLinkActive="on" ariaCurrentWhenActive="page">Comunas</a>
      </nav>
      <app-menu-usuario class="yo" [zona]="['/admin']" alSalir="/admin/login" />
    </header>
    <main class="cont"><router-outlet /></main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; background: var(--fondo); color: var(--tinta); font-size: 18px; line-height: 1.3; }
    .top { background: var(--p); color: #fff; display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 12px 24px; padding: 14px 28px; }
    .marca { display: flex; gap: 12px; align-items: center; }
    .marca small { display: block; font-size: 15px; font-weight: 500; }
    .marca b { font-size: 24px; line-height: 1.1; }
    nav { display: flex; gap: 6px; justify-content: center; }
    nav a { min-height: 44px; box-sizing: border-box; display: inline-flex; align-items: center; padding: 0 18px; border-radius: 12px; font-weight: 700; color: #fff; text-decoration: none; touch-action: manipulation; }
    nav a.on { background: #fff; color: var(--p); }
    nav a:focus-visible { outline: 4px solid #fff; outline-offset: 2px; }
    .yo { justify-self: end; }
    .cont { flex: 1; padding: 24px 28px 32px; display: flex; flex-direction: column; gap: 16px; }
    @media (max-width: 900px) {
      .top { grid-template-columns: minmax(0, 1fr) auto; grid-template-areas: 'marca yo' 'nav nav'; }
      .marca { grid-area: marca; }
      .yo { grid-area: yo; }
      nav { grid-area: nav; }
    }
    @media (max-width: 640px) { .top { padding: 12px 16px; } .cont { padding: 20px 16px 28px; } }
  `,
})
export class AdminLayout {}
