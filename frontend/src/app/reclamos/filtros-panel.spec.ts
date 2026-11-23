import { convertToParamMap } from '@angular/router';
import { diasDesde } from './estilo-estados';
import { SIN_FILTROS, aParamsDeApi, aQueryParams, buscarValido, hayFiltros, leerEstado } from './filtros-panel';

const leer = (params: Record<string, string>) => leerEstado(convertToParamMap(params));

describe('filtros del panel en la URL', () => {
  it('sinParametrosNoHayFiltrosYSeVeLaVistaConMapa', () => {
    expect(leer({})).toEqual({ filtros: SIN_FILTROS, vista: 'mapa', pagina: 0 });
  });

  it('leeTodosLosFiltrosDeLaUrl', () => {
    const e = leer({ estado: 'ASIGNADO', tipo: 'BACHE', agente: '7', buscar: 'San Martín', vista: 'lista', pagina: '3' });
    expect(e.filtros).toEqual({ estado: 'ASIGNADO', tipo: 'BACHE', agente: 7, buscar: 'San Martín' });
    expect(e.vista).toBe('lista');
    // En la URL la página va desde 1; adentro, desde 0.
    expect(e.pagina).toBe(2);
  });

  it('loInvalidoSeIgnoraEnLugarDeRomper', () => {
    const e = leer({ estado: 'INVENTADO', tipo: 'xx', agente: 'abc', vista: 'otra', pagina: '-4' });
    expect(e).toEqual({ filtros: SIN_FILTROS, vista: 'mapa', pagina: 0 });
    expect(leer({ pagina: '0' }).pagina).toBe(0);
    expect(leer({ pagina: 'x' }).pagina).toBe(0);
  });

  it('laBusquedaMuyLargaSeCorta', () => {
    expect(leer({ buscar: 'a'.repeat(300) }).filtros.buscar.length).toBe(100);
  });

  it('losParametrosDeLaUrlSoloLlevanLoQueSeUsa', () => {
    const q = aQueryParams({ filtros: { estado: 'RECIBIDO', tipo: null, agente: 5, buscar: '  luz  ' }, vista: 'lista', pagina: 1 });
    expect(q).toEqual({ estado: 'RECIBIDO', tipo: null, agente: 5, buscar: 'luz', vista: 'lista', pagina: 2 });
    const limpio = aQueryParams({ filtros: SIN_FILTROS, vista: 'mapa', pagina: 0 });
    expect(Object.values(limpio).every((v) => v === null)).toBe(true);
  });

  it('aLaApiSoloViajaLoQueElServidorAcepta', () => {
    expect(aParamsDeApi({ estado: 'ASIGNADO', tipo: 'LUZ', agente: 3, buscar: ' 42 ' })).toEqual({ estado: 'ASIGNADO', tipo: 'LUZ', agente: 3, buscar: '42' });
    // El resumen no lleva el estado.
    expect(aParamsDeApi({ estado: 'ASIGNADO', tipo: null, agente: null, buscar: '' }, false)).toEqual({});
    // Una sola letra no es una búsqueda válida: no se manda.
    expect(aParamsDeApi({ estado: null, tipo: null, agente: null, buscar: 'a' })).toEqual({});
  });

  it('buscarValidoAceptaNumerosYTextosDeDosACienCaracteres', () => {
    expect(buscarValido('7')).toBe('7');
    expect(buscarValido('ab')).toBe('ab');
    expect(buscarValido('a')).toBeNull();
    expect(buscarValido('   ')).toBeNull();
    expect(buscarValido('x'.repeat(101))).toBeNull();
  });

  it('hayFiltrosDetectaCualquierFiltro', () => {
    expect(hayFiltros(SIN_FILTROS)).toBe(false);
    expect(hayFiltros({ ...SIN_FILTROS, agente: 0 })).toBe(true);
    expect(hayFiltros({ ...SIN_FILTROS, buscar: ' x ' })).toBe(true);
  });
});

describe('diasDesde', () => {
  const ahora = new Date(2026, 10, 10, 9, 0);

  it('cuentaDiasDeCalendario', () => {
    expect(diasDesde(new Date(2026, 10, 5, 23, 0).toISOString(), ahora)).toBe(5);
    expect(diasDesde(new Date(2026, 10, 9, 23, 59).toISOString(), ahora)).toBe(1);
    expect(diasDesde(new Date(2026, 10, 10, 1, 0).toISOString(), ahora)).toBe(0);
  });

  it('unaFechaDelFuturoOInvalidaNoDaNegativos', () => {
    expect(diasDesde(new Date(2026, 10, 20).toISOString(), ahora)).toBe(0);
    expect(diasDesde('no es una fecha', ahora)).toBe(0);
  });
});
