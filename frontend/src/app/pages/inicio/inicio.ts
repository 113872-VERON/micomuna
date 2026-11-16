import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

// Pantalla provisoria: el inicio real del vecino se arma en MC-17.
@Component({
  selector: 'app-inicio',
  template: `
    <main class="pagina">
      <h1>Hola, {{ auth.usuario()?.nombre }}</h1>
      <p>Ingresaste correctamente.</p>
      <button type="button" (click)="salir()">Salir</button>
    </main>
  `,
  styles: `
    .pagina { max-width: 32rem; margin: 0 auto; padding: 3rem 1.5rem; font-size: 1.25rem; line-height: 1.4; }
    h1 { font-size: 2rem; margin: 0 0 1rem; }
    button { min-height: 3rem; padding: 0 1.5rem; font: inherit; font-weight: 700; }
  `,
})
export class Inicio {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected salir(): void {
    const slug = this.auth.usuario()?.comunaSlug;
    this.auth.cerrarSesion();
    void this.router.navigateByUrl(slug ? `/${encodeURIComponent(slug)}/login` : '/admin/login');
  }
}
