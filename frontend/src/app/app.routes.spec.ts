import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './auth/auth.service';
import { Rol } from './auth/modelos';

const comuna = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: null, slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
};

describe('Rutas de la aplicación', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  function entrar(rol: Rol) {
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Alguien', correo: 'a@correo.com', rol, comunaSlug: rol === 'SUPERADMIN' ? null : 'salsipuedes-cordoba' },
    });
  }

  it('lasPantallasDeAdminSinSesionVanAlLoginDeAdmin', async () => {
    await harness.navigateByUrl('/admin/comunas');
    expect(TestBed.inject(Router).url).toBe('/admin/login');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Plataforma');
  });

  it('unVecinoNoEntraAlPanelDelSuperadmin', async () => {
    entrar('VECINO');
    await harness.navigateByUrl('/admin/comunas/nueva');
    expect(TestBed.inject(Router).url).toBe('/admin/login');
  });

  it('elSuperadminVeLaListaDeComunas', async () => {
    entrar('SUPERADMIN');
    const navegacion = harness.navigateByUrl('/admin');
    await vi.waitFor(() => http.expectOne('/api/admin/comunas').flush([]));
    await navegacion;
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/admin/comunas');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Todavía no hay comunas');
  });

  it('adminNoSeConfundeConLaComunaDeNombreAdmin', async () => {
    await harness.navigateByUrl('/admin/login');
    http.expectNone('/api/comunas/admin');
  });

  it('elEnlaceDeLaInvitacionMuestraLaComunaYPideLaContrasena', async () => {
    const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/invitacion?codigo=abc');
    await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
    await navegacion;
    harness.detectChanges();
    const texto = (harness.routeNativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Salsipuedes');
    expect(texto).toContain('Elegí tu contraseña');
  });
});
