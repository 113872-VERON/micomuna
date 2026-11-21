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
    expect(TestBed.inject(Router).url).toBe('/sin-permiso');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('No tenés permiso');
  });

  it('cadaRolQueNoEsSuperadminVaASinPermisoAlPanel', async () => {
    for (const rol of ['VECINO', 'ADMINISTRATIVO', 'AGENTE', 'ADMIN_COMUNA'] as Rol[]) {
      entrar(rol);
      await harness.navigateByUrl('/admin/comunas');
      expect(TestBed.inject(Router).url).toBe('/sin-permiso');
    }
  });

  it('sinPermisoSinSesionNoMuestraNada', async () => {
    await harness.navigateByUrl('/sin-permiso');
    expect(TestBed.inject(Router).url).toBe('/no-encontrada');
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

  async function abrirLogin(slug: string, datos: typeof comuna) {
    const navegacion = harness.navigateByUrl(`/${slug}/login`);
    await vi.waitFor(() => http.expectOne(`/api/comunas/${slug}`).flush(datos));
    await navegacion;
    harness.detectChanges();
  }

  it('abrirOtraComunaCierraLaSesionYMuestraSuLogin', async () => {
    entrar('VECINO');
    await abrirLogin('rio-ceballos', { ...comuna, nombre: 'Río Ceballos', slug: 'rio-ceballos' });
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/rio-ceballos/login');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Río Ceballos');
  });

  it('abrirSuPropiaComunaNoCierraLaSesion', async () => {
    entrar('VECINO');
    await abrirLogin('salsipuedes-cordoba', comuna);
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(true);
  });

  it('alSuperadminNoLeCierraLaSesionAbrirUnaComuna', async () => {
    entrar('SUPERADMIN');
    await abrirLogin('salsipuedes-cordoba', comuna);
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(true);
  });

  it('sinSesionLaPantallaDeLaComunaSeAbreIgual', async () => {
    await abrirLogin('rio-ceballos', { ...comuna, nombre: 'Río Ceballos', slug: 'rio-ceballos' });
    expect(TestBed.inject(Router).url).toBe('/rio-ceballos/login');
  });

  describe('panel de la comuna', () => {
    it('elAdministradorDeLaComunaEntraALaPantallaDeUsuarios', async () => {
      entrar('ADMIN_COMUNA');
      const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/panel');
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await vi.waitFor(() => http.expectOne('/api/usuarios').flush([]));
      await navegacion;
      harness.detectChanges();

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/panel/usuarios');
      const texto = (harness.fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Salsipuedes');
      expect(texto).toContain('Usuarios');
    });

    it('unVecinoVaASinPermisoSinPedirLosUsuarios', async () => {
      entrar('VECINO');
      await harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');

      expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      http.expectNone('/api/usuarios');
      expect(TestBed.inject(AuthService).estaAutenticado()).toBe(true);
    });

    it('losDemasRolesDeLaComunaTambienVanASinPermiso', async () => {
      for (const rol of ['ADMINISTRATIVO', 'AGENTE'] as Rol[]) {
        entrar(rol);
        await harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');
        expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      }
    });

    it('sinSesionVaAlLoginDeEsaMismaComuna', async () => {
      const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await navegacion;

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/login');
      http.expectNone('/api/usuarios');
    });

    it('elAdministradorDeOtraComunaPierdeLaSesionYVaAlLoginDeEstaComuna', async () => {
      entrar('ADMIN_COMUNA');
      const navegacion = harness.navigateByUrl('/rio-ceballos/panel/usuarios');
      await vi.waitFor(() =>
        http.expectOne('/api/comunas/rio-ceballos').flush({ ...comuna, nombre: 'Río Ceballos', slug: 'rio-ceballos' }),
      );
      await navegacion;

      expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/rio-ceballos/login');
      http.expectNone('/api/usuarios');
    });
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
