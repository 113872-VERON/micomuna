import { MAX_LIMITE_BYTES, leerLimite } from './limite';

const archivo = (contenido: string | Uint8Array<ArrayBuffer>, nombre = 'limite.geojson') => new File([contenido], nombre, { type: 'application/geo+json' });

describe('leerLimite', () => {
  it('leeUnGeoJsonYLoDevuelveComoObjeto', async () => {
    const figura = { type: 'Polygon', coordinates: [[[0, 0], [1, 0], [1, 1], [0, 0]]] };

    const lectura = await leerLimite(archivo(JSON.stringify(figura)));

    expect(lectura.geojson).toEqual(figura);
    expect(lectura.error).toBeUndefined();
  });

  it('rechazaMasDe1MBSinLeerlo', async () => {
    const lectura = await leerLimite(archivo(new Uint8Array(MAX_LIMITE_BYTES + 1)));

    expect(lectura.error).toBe('El archivo del límite pesa más de 1 MB. Elegí uno más liviano.');
  });

  it('aceptaJustoUnMegabyte', async () => {
    const relleno = ' '.repeat(MAX_LIMITE_BYTES - 2);

    const lectura = await leerLimite(archivo(`{}${relleno}`));

    expect(lectura.geojson).toEqual({});
  });

  it('rechazaLoQueNoEsJson', async () => {
    const lectura = await leerLimite(archivo('esto no es json'));

    expect(lectura.error).toContain('no es un GeoJSON');
  });

  it('rechazaUnJsonQueNoEsUnObjeto', async () => {
    for (const contenido of ['[1,2]', '"texto"', '42', 'null']) {
      expect((await leerLimite(archivo(contenido))).error).toContain('no es un GeoJSON');
    }
  });
});
