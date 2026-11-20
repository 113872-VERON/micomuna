import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

// Para quien tiene la sesión iniciada pero entra a algo que su rol no permite.
@Component({
  selector: 'app-sin-permiso',
  imports: [RouterLink],
  template: `
    <main class="pagina">
      <h1>No tenés permiso para ver esta página</h1>
      <p>Tu usuario no tiene acceso a esta parte de MiComuna. Si creés que es un error, consultalo con tu comuna.</p>
      <a routerLink="/inicio">Volver al inicio</a>
    </main>
  `,
  styles: `
    .pagina { max-width: 32rem; margin: 0 auto; padding: 3rem 1.5rem; font-size: 1.25rem; line-height: 1.4; }
    h1 { font-size: 2rem; margin: 0 0 1rem; }
    a { display: inline-flex; align-items: center; min-height: 3rem; font-weight: 700; }
  `,
})
export class SinPermiso {}
