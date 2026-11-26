import { ReclamoAgenteDto } from './modelos-agente';
import { conDistancias, enlaceComoLlegar, metrosEntre, ordenar, textoDeDistancia } from './distancia';

const AQUI = { latitud: -31.4201, longitud: -64.1888 };

const reclamo = (numero: number, extra: Partial<ReclamoAgenteDto> = {}): ReclamoAgenteDto => ({
  numero,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'ASIGNADO',
  direccion: 'Av. San Martín 120',
  referencia: null,
  latitud: -31.4201,
  longitud: -64.1888,
  creadoEn: '2026-11-03T14:05:00',
  estadoCambiadoEn: '2026-11-03T14:05:00',
  ...extra,
});

describe('metrosEntre (haversine)', () => {
  it('elMismoPuntoEstaACeroMetros', () => {
    expect(metrosEntre(AQUI, AQUI)).toBe(0);
  });

  it('unaCentesimaDeGradoDeLatitudSonUnos1112Metros', () => {
    expect(metrosEntre(AQUI, { latitud: -31.4101, longitud: -64.1888 })).toBeCloseTo(1111.9, 0);
  });

  it('esSimetrica', () => {
    const otro = { latitud: -31.3, longitud: -64.0 };
    expect(metrosEntre(AQUI, otro)).toBeCloseTo(metrosEntre(otro, AQUI), 6);
  });

  it('cordobaABuenosAiresEstaPorLosSetecientosKilometros', () => {
    const km = metrosEntre({ latitud: -31.4201, longitud: -64.1888 }, { latitud: -34.6037, longitud: -58.3816 }) / 1000;
    expect(km).toBeGreaterThan(640);
    expect(km).toBeLessThan(660);
  });
});

describe('textoDeDistancia', () => {
  it('debajoDelKilometroVaEnMetrosRedondeadosDeADiez', () => {
    expect(textoDeDistancia(400)).toBe('A 400 m');
    expect(textoDeDistancia(404)).toBe('A 400 m');
    expect(textoDeDistancia(406)).toBe('A 410 m');
  });

  it('desdeElKilometroVaEnKilometrosConUnaComa', () => {
    expect(textoDeDistancia(1200)).toBe('A 1,2 km');
    expect(textoDeDistancia(12345)).toBe('A 12,3 km');
  });

  it('casiUnKilometroNoDiceMilMetros', () => {
    expect(textoDeDistancia(996)).toBe('A 1 km');
  });

  it('muyCercaDiceMenosDeDiezMetros', () => {
    expect(textoDeDistancia(0)).toBe('A menos de 10 m');
    expect(textoDeDistancia(4)).toBe('A menos de 10 m');
  });

  it('unNumeroRaroNoDiceNada', () => {
    expect(textoDeDistancia(NaN)).toBe('');
    expect(textoDeDistancia(-5)).toBe('');
  });
});

describe('ordenar', () => {
  // C no tiene coordenadas; A está a ~1,5 km; B a ~400 m. Por fecha: C, B, A. Por distancia: B, A, C.
  const A = reclamo(1, { latitud: -31.4066, creadoEn: '2026-11-01T10:00:00' });
  const B = reclamo(2, { tipo: 'LUZ', tipoNombre: 'Luz quemada', latitud: -31.4165, creadoEn: '2026-10-20T10:00:00' });
  const C = reclamo(3, { latitud: null, longitud: null, creadoEn: '2026-10-01T10:00:00' });
  const numeros = (grupos: ReturnType<typeof ordenar>) => grupos.flatMap((g) => g.tarjetas.map((t) => t.reclamo.numero));

  it('conDistanciasCalculaSoloLoQueTieneCoordenadas', () => {
    const lista = conDistancias([A, B, C], AQUI);
    expect(lista[0].metros).toBeGreaterThan(1400);
    expect(lista[0].metros).toBeLessThan(1600);
    expect(lista[1].metros).toBeGreaterThan(350);
    expect(lista[1].metros).toBeLessThan(450);
    expect(lista[2].metros).toBeNull();
  });

  it('sinUbicacionNingunoTieneDistancia', () => {
    expect(conDistancias([A, B, C], null).every((t) => t.metros === null)).toBe(true);
  });

  it('porFechaVaElMasAntiguoPrimeroYNoAgrupa', () => {
    const grupos = ordenar(conDistancias([A, B, C], AQUI), 'fecha');

    expect(numeros(grupos)).toEqual([3, 2, 1]);
    expect(grupos.length).toBe(1);
    expect(grupos[0].titulo).toBeNull();
  });

  it('porTipoAgrupaConUnEncabezadoPorTipoYCadaGrupoPorFecha', () => {
    const grupos = ordenar(conDistancias([A, B, C], AQUI), 'tipo');

    expect(grupos.map((g) => g.titulo)).toEqual(['Bache o calle rota', 'Luz quemada']);
    expect(grupos.map((g) => g.tarjetas.map((t) => t.reclamo.numero))).toEqual([[3, 1], [2]]);
  });

  it('porDistanciaVaElMasCercaPrimeroYLosSinDistanciaAlFinal', () => {
    expect(numeros(ordenar(conDistancias([A, B, C], AQUI), 'distancia'))).toEqual([2, 1, 3]);
  });

  it('conLaMismaFechaDesempataElNumero', () => {
    const x = reclamo(9);
    const y = reclamo(5);
    expect(numeros(ordenar(conDistancias([x, y], null), 'fecha'))).toEqual([5, 9]);
  });

  it('noCambiaLaListaOriginal', () => {
    const lista = conDistancias([A, B, C], AQUI);
    ordenar(lista, 'distancia');
    expect(lista.map((t) => t.reclamo.numero)).toEqual([1, 2, 3]);
  });
});

describe('enlaceComoLlegar', () => {
  it('armaElEnlaceDeIndicacionesDeGoogleMaps', () => {
    expect(enlaceComoLlegar(-31.123456, -64.299123)).toBe('https://www.google.com/maps/dir/?api=1&destination=-31.123456,-64.299123');
  });

  it('sinCoordenadasNoHayEnlace', () => {
    expect(enlaceComoLlegar(null, -64.2)).toBeNull();
    expect(enlaceComoLlegar(-31.1, null)).toBeNull();
    expect(enlaceComoLlegar(NaN, -64.2)).toBeNull();
  });
});

describe('ordenar por prioridad (MC-27)', () => {
  const orden = (lista: ReclamoAgenteDto[], desde: typeof AQUI | null) => ordenar(conDistancias(lista, desde), 'prioridad')[0].tarjetas.map((t) => t.reclamo.numero);
  const alta = { valor: 'ALTA', origen: 'IA' } as const;
  const media = { valor: 'MEDIA', origen: 'PERSONAL' } as const;
  const baja = { valor: 'BAJA', origen: 'PERSONAL' } as const;
  const ninguna = { valor: null, origen: 'NINGUNA' } as const;

  it('altaPrimeroYSinPrioridadAlFinal', () => {
    const lista = [reclamo(1, { prioridad: ninguna }), reclamo(2, { prioridad: baja }), reclamo(3, { prioridad: alta }), reclamo(4, { prioridad: media }), reclamo(5)];
    expect(orden(lista, null)).toEqual([3, 4, 2, 1, 5]);
  });

  it('aIgualPrioridadVaElMasCercaSiHayUbicacion', () => {
    const lista = [
      reclamo(1, { prioridad: alta, latitud: -31.4066 }),
      reclamo(2, { prioridad: alta, latitud: -31.4165 }),
      reclamo(3, { prioridad: media, latitud: -31.4201 }),
    ];
    // La Alta más cerca (2) antes que la Alta lejana (1); la Media, aunque esté al lado, va después.
    expect(orden(lista, AQUI)).toEqual([2, 1, 3]);
  });

  it('aIgualPrioridadSinUbicacionVaElMasViejo', () => {
    const lista = [
      reclamo(1, { prioridad: alta, creadoEn: '2026-11-05T10:00:00' }),
      reclamo(2, { prioridad: alta, creadoEn: '2026-11-01T10:00:00' }),
    ];
    expect(orden(lista, null)).toEqual([2, 1]);
  });

  it('aIgualPrioridadLosQueNoTienenCoordenadasVanDespuesDeLosQueSi', () => {
    const lista = [
      reclamo(1, { prioridad: baja, latitud: null, longitud: null, creadoEn: '2026-10-01T10:00:00' }),
      reclamo(2, { prioridad: baja, latitud: -31.4066 }),
    ];
    expect(orden(lista, AQUI)).toEqual([2, 1]);
  });

  it('noAgrupaConTitulo', () => {
    expect(ordenar(conDistancias([reclamo(1)], null), 'prioridad')[0].titulo).toBeNull();
  });
});
