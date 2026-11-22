import { bordesParaDibujar, centroDelLimite, poligonosDe, puntoDentroDelLimite } from './geo';

// Un cuadrado de (-64.31, -31.13) a (-64.29, -31.11) con un hueco al medio.
const CUADRADO = {
  type: 'Polygon',
  coordinates: [[[-64.31, -31.13], [-64.29, -31.13], [-64.29, -31.11], [-64.31, -31.11], [-64.31, -31.13]]],
};
const CON_HUECO = {
  type: 'Polygon',
  coordinates: [
    CUADRADO.coordinates[0],
    [[-64.302, -31.122], [-64.298, -31.122], [-64.298, -31.118], [-64.302, -31.118], [-64.302, -31.122]],
  ],
};
const DOS_ISLAS = {
  type: 'MultiPolygon',
  coordinates: [CUADRADO.coordinates, [[[-64.2, -31.2], [-64.19, -31.2], [-64.19, -31.19], [-64.2, -31.19], [-64.2, -31.2]]]],
};
const punto = (latitud: number, longitud: number) => ({ latitud, longitud });

describe('puntoDentroDelLimite', () => {
  it('unPuntoAdentroEstaDentro', () => {
    expect(puntoDentroDelLimite(punto(-31.12, -64.3), CUADRADO)).toBe(true);
  });

  it('unPuntoAfueraEstaFuera', () => {
    expect(puntoDentroDelLimite(punto(-31.2, -64.5), CUADRADO)).toBe(false);
    expect(puntoDentroDelLimite(punto(-31.12, -64.28), CUADRADO)).toBe(false);
  });

  it('unPuntoSobreElBordeOUnVerticeCuentaComoAdentro', () => {
    expect(puntoDentroDelLimite(punto(-31.13, -64.3), CUADRADO)).toBe(true);
    expect(puntoDentroDelLimite(punto(-31.11, -64.29), CUADRADO)).toBe(true);
  });

  it('unHuecoDejaAfueraLoQueTieneAdentro', () => {
    expect(puntoDentroDelLimite(punto(-31.12, -64.3), CON_HUECO)).toBe(false);
    expect(puntoDentroDelLimite(punto(-31.125, -64.3), CON_HUECO)).toBe(true);
    // Sobre el borde del hueco cuenta como adentro.
    expect(puntoDentroDelLimite(punto(-31.122, -64.3), CON_HUECO)).toBe(true);
  });

  it('conVariasFigurasAlcanzaConEstarEnUna', () => {
    expect(puntoDentroDelLimite(punto(-31.195, -64.195), DOS_ISLAS)).toBe(true);
    expect(puntoDentroDelLimite(punto(-31.12, -64.3), DOS_ISLAS)).toBe(true);
    expect(puntoDentroDelLimite(punto(-31.15, -64.25), DOS_ISLAS)).toBe(false);
  });

  it('sinFiguraLegibleTodoSeDaPorAdentro', () => {
    expect(puntoDentroDelLimite(punto(0, 0), null)).toBe(true);
    expect(puntoDentroDelLimite(punto(0, 0), {})).toBe(true);
    expect(puntoDentroDelLimite(punto(0, 0), { type: 'Point', coordinates: [1, 2] })).toBe(true);
    expect(puntoDentroDelLimite(punto(0, 0), { type: 'Polygon', coordinates: [[[0, 0], [1, 1]]] })).toBe(true);
  });
});

describe('centroDelLimite', () => {
  it('esElCentroDeLaCajaQueRodeaLaFigura', () => {
    const centro = centroDelLimite(CUADRADO)!;
    expect(centro.latitud).toBeCloseTo(-31.12, 9);
    expect(centro.longitud).toBeCloseTo(-64.3, 9);
  });

  it('conVariasFigurasRodeaATodas', () => {
    const centro = centroDelLimite(DOS_ISLAS)!;
    expect(centro.longitud).toBeCloseTo((-64.31 + -64.19) / 2, 9);
    expect(centro.latitud).toBeCloseTo((-31.2 + -31.11) / 2, 9);
  });

  it('sinFiguraDevuelveNull', () => {
    expect(centroDelLimite(null)).toBeNull();
    expect(centroDelLimite({ type: 'Polygon', coordinates: [] })).toBeNull();
  });
});

describe('bordesParaDibujar', () => {
  it('conviertePosicionesGeoJsonALatLng', () => {
    const [anillo] = bordesParaDibujar(CUADRADO);
    expect(anillo[0]).toEqual({ lat: -31.13, lng: -64.31 });
    expect(anillo.length).toBe(5);
  });

  it('dibujaTambienLosHuecosYCadaFigura', () => {
    expect(bordesParaDibujar(CON_HUECO).length).toBe(2);
    expect(bordesParaDibujar(DOS_ISLAS).length).toBe(2);
    expect(poligonosDe(DOS_ISLAS).length).toBe(2);
  });
});
