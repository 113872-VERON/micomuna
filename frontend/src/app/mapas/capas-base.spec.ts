import {
  ATRIBUCION_ARGENMAP,
  ATRIBUCION_IGN,
  ATRIBUCION_OSM,
  ERRORES_PARA_RESPALDO,
  RespaldoDeTeselas,
  URL_ARGENMAP,
  URL_OSM,
} from './capas-base';

describe('capas base', () => {
  it('lasDosCapasTienenAtribucionVisible', () => {
    expect(ATRIBUCION_IGN).toContain('Instituto Geográfico Nacional');
    expect(ATRIBUCION_OSM).toContain('colaboradores de OpenStreetMap');
    expect(ATRIBUCION_OSM).toContain('https://www.openstreetmap.org/copyright');
    // Argenmap usa datos de OpenStreetMap: las dos se muestran juntas.
    expect(ATRIBUCION_ARGENMAP).toContain('Instituto Geográfico Nacional');
    expect(ATRIBUCION_ARGENMAP).toContain('colaboradores de OpenStreetMap');
  });

  it('lasUrlsPidenZoomXYDeTeselas', () => {
    expect(URL_ARGENMAP).toContain('wms.ign.gob.ar');
    expect(URL_ARGENMAP).toContain('{z}/{x}/{y}.png');
    expect(URL_OSM).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
  });
});

describe('RespaldoDeTeselas', () => {
  it('pasaARespaldoDespuesDeVariosErroresSinCargarNinguna', () => {
    const r = new RespaldoDeTeselas();
    for (let i = 1; i < ERRORES_PARA_RESPALDO; i++) {
      expect(r.teselaConError()).toBe(false);
    }

    expect(r.teselaConError()).toBe(true);
    expect(r.enRespaldo).toBe(true);
    // El cambio es una sola vez.
    expect(r.teselaConError()).toBe(false);
  });

  it('siAlgunaTeselaCargoLosErroresSueltosNoCambianDeCapa', () => {
    const r = new RespaldoDeTeselas();
    r.teselaCargada();

    for (let i = 0; i < 10; i++) {
      expect(r.teselaConError()).toBe(false);
    }
    expect(r.enRespaldo).toBe(false);
  });
});
