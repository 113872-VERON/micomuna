import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReclamosService, aFormData } from './reclamos.service';

const jpeg = (texto: string) => new Blob([texto], { type: 'image/jpeg' });

describe('aFormData', () => {
  it('llevaLosCamposPlanosDelContratoYLasFotosEnFotos', () => {
    const datos = aFormData({
      tipo: 'BACHE',
      descripcion: '  Hay un pozo grande  ',
      referencia: ' frente a la panadería ',
      ubicacion: { latitud: -31.123456789, longitud: -64.2999999, precision: 12 },
      fotos: [jpeg('uno'), jpeg('dos')],
    });

    expect(datos.get('tipo')).toBe('BACHE');
    expect(datos.get('descripcion')).toBe('Hay un pozo grande');
    expect(datos.get('referencia')).toBe('frente a la panadería');
    expect(datos.get('latitud')).toBe('-31.123457');
    expect(datos.get('longitud')).toBe('-64.300000');
    // La precisión del GPS no viaja: no está en el contrato.
    expect(datos.has('precision')).toBe(false);
    const fotos = datos.getAll('fotos') as File[];
    expect(fotos.map((f) => f.name)).toEqual(['foto-1.jpg', 'foto-2.jpg']);
    expect(fotos.every((f) => f.type === 'image/jpeg')).toBe(true);
  });

  it('sinUbicacionNiReferenciaNiFotosNoMandaEsosCampos', () => {
    const datos = aFormData({ tipo: 'LUZ', descripcion: 'Farol apagado', referencia: '   ', ubicacion: null, fotos: [] });

    expect(Array.from(datos.keys())).toEqual(['tipo', 'descripcion']);
  });
});

describe('aFormData con dirección', () => {
  const base = { tipo: 'LUZ', descripcion: 'Farol', referencia: '', ubicacion: { latitud: -31.1, longitud: -64.3 }, fotos: [] } as const;

  it('mandaLaDireccionSinEspaciosSobrantes', () => {
    expect(aFormData({ ...base, direccion: '  San Martín 450, Salsipuedes ' }).get('direccion')).toBe('San Martín 450, Salsipuedes');
  });

  it('enBlancoONullNoLaManda', () => {
    expect(aFormData({ ...base, direccion: '   ' }).has('direccion')).toBe(false);
    expect(aFormData({ ...base, direccion: null }).has('direccion')).toBe(false);
  });

  it('laCortaEnLos300CaracteresDelContrato', () => {
    expect((aFormData({ ...base, direccion: 'x'.repeat(400) }).get('direccion') as string).length).toBe(300);
  });
});

describe('ReclamosService', () => {
  let http: HttpTestingController;
  let servicio: ReclamosService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ReclamosService);
  });

  afterEach(() => http.verify());

  it('pideLosTiposDeProblema', () => {
    let recibidos: unknown;
    servicio.listarTipos().subscribe((tipos) => (recibidos = tipos));
    const pedido = http.expectOne('/api/tipos-reclamo');
    expect(pedido.request.method).toBe('GET');
    pedido.flush([{ codigo: 'BACHE', nombre: 'Bache o calle rota', consejoFoto: 'De día.' }]);
    expect(recibidos).toEqual([{ codigo: 'BACHE', nombre: 'Bache o calle rota', consejoFoto: 'De día.' }]);
  });

  it('creaElReclamoComoMultipartSinPonerElContentTypeAMano', () => {
    servicio.crear({ tipo: 'OTRO', descripcion: 'Algo', referencia: 'Plaza', ubicacion: null, fotos: [] }).subscribe();
    const pedido = http.expectOne('/api/reclamos');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toBeInstanceOf(FormData);
    expect(pedido.request.headers.has('Content-Type')).toBe(false);
    pedido.flush({ id: 1, numero: 1, tipo: 'OTRO', estado: 'RECIBIDO', cantidadFotos: 0, creadoEn: '2026-11-21T10:00:00Z' });
  });
  it('pideElLimiteDeMiComunaYUn204LlegaComoNull', () => {
    let recibido: unknown = 'sin respuesta';
    servicio.limiteDeMiComuna().subscribe((l) => (recibido = l));
    const pedido = http.expectOne('/api/mi-comuna/limite');
    expect(pedido.request.method).toBe('GET');
    pedido.flush(null, { status: 204, statusText: 'No Content' });
    expect(recibido).toBeNull();
  });
});
