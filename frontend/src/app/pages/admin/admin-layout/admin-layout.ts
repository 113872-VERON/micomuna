import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';

// Marco del panel del superadmin: barra superior con la marca, el menú y quién ingresó.
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
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
        <a routerLink="/admin/comunas" routerLinkActive="on">Comunas</a>
      </nav>
      <div class="yo">
        <div class="av" aria-hidden="true">{{ inicial() }}</div>
        <div><b>{{ auth.usuario()?.nombre }}</b><small>Superadmin</small></div>
        <button type="button" class="salir" (click)="salir()">Salir</button>
      </div>
    </header>
    <main class="cont"><router-outlet /></main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; background: var(--fondo); color: var(--tinta); font-size: 18px; line-height: 1.3; }
    .top { background: var(--p); color: #fff; display: flex; align-items: center; flex-wrap: wrap; gap: 12px 24px; padding: 14px 28px; }
    .marca { display: flex; gap: 12px; align-items: center; }
    .marca small { display: block; font-size: 15px; font-weight: 500; }
    .marca b { font-size: 24px; line-height: 1.1; }
    nav { display: flex; gap: 6px; flex: 1; }
    nav a { padding: 10px 18px; border-radius: 12px; font-weight: 700; color: #fff; text-decoration: none; min-height: 24px; }
    nav a.on { background: #fff; color: var(--p); }
    .yo { display: flex; gap: 12px; align-items: center; }
    .av { width: 46px; height: 46px; border-radius: 50%; background: #fff; color: var(--p); font-weight: 700; font-size: 22px; display: flex; align-items: center; justify-content: center; }
    .yo b { display: block; font-size: 17px; }
    .yo small { font-size: 15px; }
    .salir { min-height: 44px; padding: 0 16px; border: 2px solid #fff; border-radius: 12px; background: transparent; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
    .cont { flex: 1; padding: 24px 28px 32px; display: flex; flex-direction: column; gap: 16px; }
    @media (max-width: 640px) { .top { padding: 12px 16px; } .cont { padding: 20px 16px 28px; } }
  `,
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected inicial(): string {
    return (this.auth.usuario()?.nombre ?? '?').trim().charAt(0).toUpperCase();
  }

  protected salir(): void {
    this.auth.cerrarSesion();
    void this.router.navigateByUrl('/admin/login');
  }
}
