import { Component, input } from '@angular/core';

// Ícono de cada tipo de problema (los trazos de la maqueta reportar-problema.html).
// El servidor manda el código y el nombre; el dibujo lo pone el frontend. Siempre va junto a la palabra.
@Component({
  selector: 'app-icono-tipo',
  template: `
    <svg
      [attr.width]="tamano()"
      [attr.height]="tamano()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2.2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (codigo()) {
        @case ('BACHE') {
          <path d="M3 17h18" /><path d="M5 17l3-9M19 17l-3-9M12 7v3M12 13v2" />
        }
        @case ('LUZ') {
          <path d="M9 18h6M10 21h4" />
          <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z" />
        }
        @case ('BASURA') {
          <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />
        }
        @case ('ARBOL') {
          <path d="M12 21v-6" /><path d="M12 15c-4 0-7-2.5-7-6a7 7 0 0 1 14 0c0 3.5-3 6-7 6z" />
        }
        @case ('AGUA') {
          <path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z" />
        }
        @default {
          <circle cx="12" cy="12" r="9.5" /><path d="M12 8v5M12 16.5v.5" />
        }
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; }
  `,
})
export class IconoTipo {
  readonly codigo = input.required<string>();
  readonly tamano = input(28);
}
