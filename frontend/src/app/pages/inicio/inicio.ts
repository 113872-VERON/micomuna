import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

// Pantalla provisoria: el inicio real del vecino se arma en MC-17.
@Component({
  selector: 'app-inicio',
  imports: [RouterLink],
  template: `
    <main class="pagina">
      <h1>Hola, {{ auth.usuario()?.nombre }}</h1>
      <p>Ingresaste correctamente.</p>
      @if (reportar(); as enlace) {
        <a class="reportar" [routerLink]="enlace">Reportar un problema</a>
      }
      <button type="button" (click)="salir()">Salir</button>
    </main>
  `,
  styles: `
    .pagina { max-width: 32rem; margin: 0 auto; padding: 3rem 1.5rem; font-size: 1.25rem; line-height: 1.4; display: flex; flex-direction: column; align-items: flex-start; gap: 1rem; }
    h1 { font-size: 2rem; margin: 0; overflow-wrap: anywhere; }
    p { margin: 0; }
    .reportar {
      align-self: stretch; min-height: 64px; display: flex; align-items: center; justify-content: center; box-sizing: border-box;
      padding: 0 1.5rem; border-radius: 16px; background: var(--p); color: #fff; font-size: 22px; font-weight: 700;
      text-decoration: none; touch-action: manipulation; user-select: none; transition: transform 160ms cubic-bezier(0.23, 1, 0.32, 1);
    }
    .reportar:active { transform: scale(0.97); }
    button { min-height: 3rem; padding: 0 1.5rem; font: inherit; font-weight: 700; }
    @media (prefers-reduced-motion: reduce) { .reportar { transition: none; } }
  `,
})
export class Inicio {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Solo el vecino reporta. El enlace lleva la comuna de su sesión.
  protected readonly reportar = computed(() => {
    const usuario = this.auth.usuario();
    return usuario?.rol === 'VECINO' && usuario.comunaSlug ? ['/', usuario.comunaSlug, 'reportar'] : null;
  });

  protected salir(): void {
    const slug = this.auth.usuario()?.comunaSlug;
    this.auth.cerrarSesion();
    void this.router.navigateByUrl(slug ? `/${encodeURIComponent(slug)}/login` : '/admin/login');
  }
}
