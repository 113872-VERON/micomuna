import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DireccionService } from './direccion.service';

const URL = '/api/ubicacion/direccion';

describe('DireccionService', () => {
  let http: HttpTestingController;
  let servicio: DireccionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(DireccionService);
  });

  afterEach(() => http.verify());

  async function pedir(respuesta: () => void): Promise<string | null> {
    const resultado = servicio.de({ latitud: -31.1234567, longitud: -64.3 });
    respuesta();
    return resultado;
  }
  const pedido = () => http.expectOne((p) => p.url === URL);

  it('pideLaDireccionConLatitudYLongitudDe6Decimales', async () => {
    const direccion = await pedir(() => {
      const p = pedido();
      expect(p.request.method).toBe('GET');
      expect(p.request.params.get('latitud')).toBe('-31.123457');
      expect(p.request.params.get('longitud')).toBe('-64.300000');
      p.flush({ direccion: '  Av. San Martín 120, Agua de Oro ' });
    });

    expect(direccion).toBe('Av. San Martín 120, Agua de Oro');
  });

  it('un204EsSinDireccion', async () => {
    expect(await pedir(() => pedido().flush(null, { status: 204, statusText: 'No Content' }))).toBeNull();
  });

  it('un429EsSinDireccion', async () => {
    expect(await pedir(() => pedido().flush(null, { status: 429, statusText: 'Too Many Requests' }))).toBeNull();
  });

  it('unErrorOLaFaltaDeRedEsSinDireccion', async () => {
    expect(await pedir(() => pedido().flush(null, { status: 500, statusText: 'Server Error' }))).toBeNull();
    expect(await pedir(() => pedido().error(new ProgressEvent('error')))).toBeNull();
  });

  it('unaRespuestaSinTextoEsSinDireccionYUnaLargaSeCorta', async () => {
    expect(await pedir(() => pedido().flush({ direccion: '   ' }))).toBeNull();
    expect((await pedir(() => pedido().flush({ direccion: 'x'.repeat(400) })))?.length).toBe(300);
  });
});
