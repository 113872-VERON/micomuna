import { TestBed } from '@angular/core/testing';
import { COLORES_COMUNA, TemaComunaService } from './tema-comuna.service';

describe('TemaComunaService', () => {
  const raiz = () => document.documentElement.style;

  afterEach(() => {
    raiz().removeProperty('--p');
    raiz().removeProperty('--fondo');
    raiz().removeProperty('--ayuda');
  });

  it('aplicaElColorDeLaComuna', () => {
    TestBed.inject(TemaComunaService).aplicar('OCRE');

    expect(raiz().getPropertyValue('--p')).toBe('#9A4A0B');
    expect(raiz().getPropertyValue('--fondo')).toBe('color-mix(in srgb, #9A4A0B 5%, white)');
    expect(raiz().getPropertyValue('--ayuda')).toBe('color-mix(in srgb, #9A4A0B 12%, white)');
  });

  it('usaCelesteSiElColorNoEsConocido', () => {
    TestBed.inject(TemaComunaService).aplicar('FUCSIA');

    expect(raiz().getPropertyValue('--p')).toBe(COLORES_COMUNA.CELESTE);
  });

  it('restablecerQuitaElColorDeLaComuna', () => {
    const tema = TestBed.inject(TemaComunaService);
    tema.aplicar('BOSQUE');

    tema.restablecer();

    expect(raiz().getPropertyValue('--p')).toBe('');
  });

  it('losSieteColoresSonLosDeLaPaleta', () => {
    expect(Object.keys(COLORES_COMUNA)).toEqual([
      'CELESTE', 'MARINO', 'VERDE_AZULADO', 'BOSQUE', 'CIRUELA', 'OCRE', 'PIZARRA',
    ]);
  });
});
