import { Component } from '@angular/core';

@Component({
  selector: 'app-no-encontrada',
  template: `
    <main class="pagina">
      <h1>No encontramos esta página</h1>
      <p>Entrá desde el enlace de tu comuna. Si no lo tenés, pedíselo a tu comuna.</p>
    </main>
  `,
  styles: `
    .pagina { max-width: 32rem; margin: 0 auto; padding: 3rem 1.5rem; font-size: 1.25rem; line-height: 1.4; }
    h1 { font-size: 2rem; margin: 0 0 1rem; }
  `,
})
export class NoEncontrada {}
