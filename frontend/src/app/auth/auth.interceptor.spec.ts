import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthRespuesta } from './modelos';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

const vecino: AuthRespuesta = {
  token: 'tk',
  usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
};
const superadmin: AuthRespuesta = {
  token: 'tk-admin',
  usuario: { id: 2, nombre: 'Andres', correo: 'a@correo.com', rol: 'SUPERADMIN', comunaSlug: null },
};

describe('authInterceptor', () => {
  let http: HttpClient;
  let controlador: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    controlador = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    controlador.verify();
    vi.restoreAllMocks();
  });

  it('elInterceptorAgregaElHeader', () => {
    auth.guardarSesion(vecino);
    http.get('/api/reclamos').subscribe();
    const pedido = controlador.expectOne('/api/reclamos');
    expect(pedido.request.headers.get('Authorization')).toBe('Bearer tk');
    pedido.flush([]);
  });

  it('sinSesionNoAgregaElHeader', () => {
    http.get('/api/comunas/salsipuedes-cordoba').subscribe();
    const pedido = controlador.expectOne('/api/comunas/salsipuedes-cordoba');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush({});
  });

  it('noMandaElTokenAOtroServidor', () => {
    auth.guardarSesion(vecino);
    http.get('https://otro.example.com/datos').subscribe();
    const pedido = controlador.expectOne('https://otro.example.com/datos');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush({});
  });

  it('noMandaElTokenAUnaUrlSinProtocolo', () => {
    auth.guardarSesion(vecino);
    http.get('//otro.example.com/datos').subscribe();
    const pedido = controlador.expectOne('//otro.example.com/datos');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush({});
  });

  it('unaRespuesta401LimpiaLaSesionYVaAlLoginDeLaComuna', () => {
    auth.guardarSesion(vecino);
    http.get('/api/reclamos').subscribe({ error: () => undefined });
    controlador.expectOne('/api/reclamos').flush(null, { status: 401, statusText: 'No autorizado' });

    expect(auth.estaAutenticado()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/login');
  });

  it('unaRespuesta401DelSuperadminVaAlLoginDeAdmin', () => {
    auth.guardarSesion(superadmin);
    http.get('/api/admin/comunas').subscribe({ error: () => undefined });
    controlador.expectOne('/api/admin/comunas').flush(null, { status: 401, statusText: 'No autorizado' });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/login');
  });

  it('unaRespuesta403VaASinPermisoYNoCierraLaSesion', () => {
    auth.guardarSesion(vecino);
    http.get('/api/admin/comunas').subscribe({ error: () => undefined });
    controlador.expectOne('/api/admin/comunas').flush({ codigo: 'ACCESO_DENEGADO' }, { status: 403, statusText: 'Prohibido' });

    expect(auth.estaAutenticado()).toBe(true);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/sin-permiso');
  });

  it('unaRespuesta403DeOtroServidorNoRedirige', () => {
    auth.guardarSesion(vecino);
    http.get('https://otro.example.com/datos').subscribe({ error: () => undefined });
    controlador.expectOne('https://otro.example.com/datos').flush(null, { status: 403, statusText: 'Prohibido' });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('unLoginIncorrectoNoRedirigeNiLimpiaLaSesion', () => {
    auth.guardarSesion(vecino);
    http.post('/api/comunas/salsipuedes-cordoba/login', {}).subscribe({ error: () => undefined });
    controlador.expectOne('/api/comunas/salsipuedes-cordoba/login').flush(null, { status: 401, statusText: 'No autorizado' });

    expect(auth.estaAutenticado()).toBe(true);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
