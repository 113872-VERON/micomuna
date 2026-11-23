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
    // La pantalla de reclamos pide varias cosas al abrirse: se contestan todas vacías.
    function responderPanelDeReclamos() {
      http.match('/api/panel/agentes').forEach((p) => p.flush([]));
      http.match('/api/tipos-reclamo').forEach((p) => p.flush([]));
      http.match('/api/mi-comuna/limite').forEach((p) => p.flush(null, { status: 204, statusText: 'No Content' }));
      http.match((p) => p.url === '/api/panel/reclamos').forEach((p) => p.flush({ reclamos: [], pagina: 0, tamanio: 25, total: 0 }));
      http.match('/api/panel/reclamos/mapa').forEach((p) => p.flush({ puntos: [], recortado: false }));
      http
        .match('/api/panel/reclamos/resumen')
        .forEach((p) =>
          p.flush({ porEstado: { RECIBIDO: 0, ASIGNADO: 0, EN_REPARACION: 0, RESUELTO: 0, DESCARTADO: 0 }, total: 0, sinAsignar: 0, sinAsignarMasAntiguo: null }),
        );
    }

    async function irAlPanel(url: string) {
      const navegacion = harness.navigateByUrl(url);
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await navegacion;
      responderPanelDeReclamos();
      harness.detectChanges();
    }

    it('elAdministradorDeLaComunaEntraAlPanelYVaALosReclamos', async () => {
      entrar('ADMIN_COMUNA');
      await irAlPanel('/salsipuedes-cordoba/panel');

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/panel/reclamos');
      const texto = (harness.fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Salsipuedes');
      const menu = Array.from((harness.fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map((a) => a.textContent?.trim());
      expect(menu).toEqual(['Reclamos', 'Métricas', 'Usuarios']);
    });

    it('elPersonalAdministrativoVeReclamosYMetricasEnElMenu', async () => {
      entrar('ADMINISTRATIVO');
      await irAlPanel('/salsipuedes-cordoba/panel');

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/panel/reclamos');
      const menu = Array.from((harness.fixture.nativeElement as HTMLElement).querySelectorAll('nav a')).map((a) => a.textContent?.trim());
      expect(menu).toEqual(['Reclamos', 'Métricas']);
    });

    it('elAdministradorYElAdministrativoEntranALasMetricasConLaRutaDeSuComuna', async () => {
      for (const rol of ['ADMIN_COMUNA', 'ADMINISTRATIVO'] as Rol[]) {
        await harness.navigateByUrl('/sin-permiso');
        entrar(rol);
        const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/panel/metricas');
        await vi.waitFor(() => { http.match('/api/comunas/salsipuedes-cordoba').forEach((c) => c.flush(comuna)); http.expectOne('/api/panel/metricas').flush({ desde: '2026-10-05', hasta: '2026-11-03', dias: 30, reclamosDelPeriodo: 0, resueltos: 0, tiempoPromedioResolucionHoras: null, sinAsignar: 0, porTipo: [], porSemana: [] }); });
        await navegacion;
        harness.detectChanges();

        expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/panel/metricas');
        expect((harness.fixture.nativeElement as HTMLElement).textContent).toContain('Mostrando del 5/10/2026 al 3/11/2026 (30 días)');
      }
    });

    it('elVecinoYElAgenteNoEntranALasMetricas', async () => {
      for (const rol of ['VECINO', 'AGENTE'] as Rol[]) {
        await harness.navigateByUrl('/sin-permiso');
        entrar(rol);
        const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/panel/metricas');
        await navegacion;
        http.match('/api/comunas/salsipuedes-cordoba').forEach((x) => x.flush(comuna));

        expect(TestBed.inject(Router).url).toBe('/sin-permiso');
        http.expectNone('/api/panel/metricas');
      }
    });

    it('elAdministradorDeLaComunaEntraALaPantallaDeUsuarios', async () => {
      entrar('ADMIN_COMUNA');
      const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await vi.waitFor(() => http.expectOne('/api/usuarios').flush([]));
      await navegacion;
      harness.detectChanges();

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/panel/usuarios');
    });

    it('elPersonalAdministrativoNoEntraAUsuariosYNoSePidenLosUsuarios', async () => {
      entrar('ADMINISTRATIVO');
      await harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');

      expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      http.expectNone('/api/usuarios');
    });

    it('elDetalleDeUnReclamoEsDelPersonalYUnAgenteNoEntra', async () => {
      entrar('AGENTE');
      await harness.navigateByUrl('/salsipuedes-cordoba/panel/reclamos/12');

      expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      http.expectNone('/api/panel/reclamos/12');
    });

    it('unVecinoVaASinPermisoSinPedirLosUsuarios', async () => {
      entrar('VECINO');
      await harness.navigateByUrl('/salsipuedes-cordoba/panel/usuarios');

      expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      http.expectNone('/api/usuarios');
      expect(TestBed.inject(AuthService).estaAutenticado()).toBe(true);
    });

    it('losDemasRolesNoVenUsuarios', async () => {
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

  describe('reportar un problema', () => {
    it('elVecinoEntraYVeElPrimerPaso', async () => {
      entrar('VECINO');
      const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/reportar');
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await navegacion;
      await vi.waitFor(() => http.expectOne('/api/tipos-reclamo').flush([]));
      harness.detectChanges();

      // Entrar a reportar lleva al primer paso, con su propia dirección.
      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/reportar/que-paso');
      const texto = (harness.routeNativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Paso 1 de 4');
      expect(texto).toContain('¿Qué pasó?');
    });

    it('losDemasRolesVanASinPermisoSinPedirLosTipos', async () => {
      for (const rol of ['AGENTE', 'ADMINISTRATIVO', 'ADMIN_COMUNA'] as Rol[]) {
        entrar(rol);
        await harness.navigateByUrl('/salsipuedes-cordoba/reportar');
        expect(TestBed.inject(Router).url).toBe('/sin-permiso');
      }
      http.expectNone('/api/tipos-reclamo');
    });

    it('sinSesionVaAlLoginDeEsaComuna', async () => {
      const navegacion = harness.navigateByUrl('/salsipuedes-cordoba/reportar');
      await vi.waitFor(() => http.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna));
      await navegacion;

      expect(TestBed.inject(Router).url).toBe('/salsipuedes-cordoba/login');
      http.expectNone('/api/tipos-reclamo');
    });

    it('unVecinoDeOtraComunaPierdeLaSesionYVaAlLoginDeEstaComuna', async () => {
      entrar('VECINO');
      const navegacion = harness.navigateByUrl('/rio-ceballos/reportar');
      await vi.waitFor(() =>
        http.expectOne('/api/comunas/rio-ceballos').flush({ ...comuna, nombre: 'Río Ceballos', slug: 'rio-ceballos' }),
      );
      await navegacion;

      expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
      expect(TestBed.inject(Router).url).toBe('/rio-ceballos/login');
      http.expectNone('/api/tipos-reclamo');
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
