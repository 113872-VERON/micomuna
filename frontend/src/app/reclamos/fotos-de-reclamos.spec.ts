import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { FotosDeReclamos } from './fotos-de-reclamos';

const imagen = () => new Blob(['x'], { type: 'image/jpeg' });

describe('FotosDeReclamos', () => {
  let http: HttpTestingController;
  let fotos: FotosDeReclamos;
  let creadas: string[];
  let liberadas: string[];

  beforeEach(() => {
    localStorage.clear();
    creadas = [];
    liberadas = [];
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
      creadas.push(`blob:${creadas.length + 1}`);
      return creadas[creadas.length - 1];
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((u) => void liberadas.push(u));
    TestBed.configureTestingModule({ providers: [FotosDeReclamos, provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'r@c.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });
    fotos = TestBed.inject(FotosDeReclamos);
  });

  afterEach(() => http.verify());

  it('unaFotoYaCargadaNoSeVuelveAPedir', () => {
    const urls: string[] = [];
    fotos.url(12, 5).subscribe((u) => urls.push(u));
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());

    fotos.url(12, 5).subscribe((u) => urls.push(u));

    http.expectNone('/api/reclamos/mios/12/fotos/5');
    expect(urls).toEqual(['blob:1', 'blob:1']);
    expect(creadas.length).toBe(1);
  });

  it('dosPedidosAlMismoTiempoSeResuelvenConUnaSolaDescarga', () => {
    fotos.url(12, 5).subscribe();
    fotos.url(12, 5).subscribe();

    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());
    expect(creadas.length).toBe(1);
  });

  it('fotosDistintasSePidenPorSeparado', () => {
    fotos.url(12, 5).subscribe();
    fotos.url(12, 6).subscribe();
    fotos.url(13, 5).subscribe();

    expect(http.match((p) => p.url.includes('/fotos/')).length).toBe(3);
  });

  it('unaFotoQueFalloSeVuelveAPedirAlReintentar', () => {
    let fallo = false;
    fotos.url(12, 5).subscribe({ error: () => (fallo = true) });
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(null, { status: 500, statusText: 'Error' });
    expect(fallo).toBe(true);

    fotos.url(12, 5).subscribe();
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());
  });

  it('alDestruirseRevocaTodasLasUrls', () => {
    fotos.url(12, 5).subscribe();
    fotos.url(12, 6).subscribe();
    http.match((p) => p.url.includes('/fotos/')).forEach((p) => p.flush(imagen()));

    fotos.ngOnDestroy();

    expect(liberadas.sort()).toEqual(['blob:1', 'blob:2']);
  });

  it('alCerrarLaSesionNoQuedaNingunaFotoEnMemoria', () => {
    fotos.url(12, 5).subscribe();
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());

    TestBed.inject(AuthService).cerrarSesion();
    TestBed.tick();

    expect(liberadas).toEqual(['blob:1']);
    // Y si se vuelve a pedir, se baja de nuevo.
    fotos.url(12, 5).subscribe();
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());
  });

  it('unaDescargaQueTerminaDespuesDeVaciarLaMemoriaNoDejaUrlsSueltas', () => {
    let fallo = false;
    fotos.url(12, 5).subscribe({ error: () => (fallo = true) });

    fotos.limpiar();
    http.expectOne('/api/reclamos/mios/12/fotos/5').flush(imagen());

    expect(fallo).toBe(true);
    expect(creadas.length).toBe(0);
  });
});
