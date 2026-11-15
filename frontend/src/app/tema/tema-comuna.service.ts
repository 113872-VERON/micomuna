import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { ColorComuna } from '../auth/modelos';

// Los siete colores de la paleta cerrada (todos con contraste AA contra el blanco). Mismos valores que el diseño.
export const COLORES_COMUNA: Record<ColorComuna, string> = {
  CELESTE: '#1D6AA8',
  MARINO: '#1E3A8A',
  VERDE_AZULADO: '#0B6E6E',
  BOSQUE: '#2F6B3F',
  CIRUELA: '#6B2D7B',
  OCRE: '#9A4A0B',
  PIZARRA: '#3F4F5F',
};

// Escribe las variables de CSS del tema en la raíz: ningún componente lleva colores de comuna escritos a mano.
@Injectable({ providedIn: 'root' })
export class TemaComunaService {
  private readonly raiz = inject(DOCUMENT).documentElement.style;

  aplicar(color: string): void {
    const hex = COLORES_COMUNA[color as ColorComuna] ?? COLORES_COMUNA.CELESTE;
    this.raiz.setProperty('--p', hex);
    this.raiz.setProperty('--fondo', `color-mix(in srgb, ${hex} 5%, white)`);
    this.raiz.setProperty('--ayuda', `color-mix(in srgb, ${hex} 12%, white)`);
  }

  restablecer(): void {
    for (const variable of ['--p', '--fondo', '--ayuda']) {
      this.raiz.removeProperty(variable);
    }
  }
}
