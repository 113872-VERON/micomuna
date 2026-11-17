import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ComunaAdminDto } from '../auth/modelos';
import { ComunasAdminService } from './comunas-admin.service';

describe('ComunasAdminService', () => {
  let http: HttpTestingController;
  let servicio: ComunasAdminService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ComunasAdminService);
  });

  afterEach(() => http.verify());

  it('subirLogoEnviaElArchivoEnElCampoArchivoPorPut', () => {
    const archivo = new File([new Uint8Array([1, 2, 3])], 'logo.png', { type: 'image/png' });
    const devuelta = { id: 7, tieneLogo: true } as ComunaAdminDto;
    let recibida: ComunaAdminDto | undefined;

    servicio.subirLogo(7, archivo).subscribe((c) => (recibida = c));

    const pedido = http.expectOne('/api/admin/comunas/7/logo');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body instanceof FormData).toBe(true);
    const enviado = (pedido.request.body as FormData).get('archivo') as File;
    expect(enviado.name).toBe('logo.png');
    // El Content-Type con el boundary lo arma el navegador: no se fija a mano.
    expect(pedido.request.headers.has('Content-Type')).toBe(false);
    pedido.flush(devuelta);

    expect(recibida).toEqual(devuelta);
  });
});
