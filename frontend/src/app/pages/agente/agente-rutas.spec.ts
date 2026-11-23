import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { Rol } from '../../auth/modelos';
import { SinUbicacion, UbicacionService } from '../../reclamos/ubicacion.service';

const comuna = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };

describe('Rutas del agente (con las rutas reales)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
        provideHttpClient(),
        provideHttpClientTesting(),
        // El GPS del teléfono no existe en las pruebas.
        { provide: UbicacionService, useValue: { obtener: () => Promise.reject(new SinUbicacion('no-disponible')) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.match(() => true);
    document.documentElement.style.removeProperty('--p');
  });

  function entrar(rol: Rol, comunaSlug: string | null = 'salsipuedes-cordoba') {
    TestBed.inject(AuthService).guardarSesion({ token: 'tk', usuario: { id: 1, nombre: 'Juan Agente', correo: 'j@c.com', rol, comunaSlug } });
  }

  async function ir(url: string) {
    let terminada = false;
    const navegacion = harness.navigateByUrl(url).finally(() => (terminada = true));
    await vi.waitFor(() => {
      http.match('/api/comunas/salsipuedes-cordoba').forEach((p) => p.flush(comuna));
      if (!terminada) {
        throw new Error('la navegación sigue en curso');
      }
    });
    await navegacion;
    harness.detectChanges();
  }
  const url = () => TestBed.inject(Router).url;
  const pantalla = () => (harness.routeNativeElement as HTMLElement).textContent ?? '';

  it('elAgenteVeMisReclamosEnLaRutaDeSuComunaConLaBarraDeLaComunaYSalir', async () => {
    entrar('AGENTE');
    await ir('/salsipuedes-cordoba/agente');

    expect(url()).toBe('/salsipuedes-cordoba/agente');
    http.expectOne((p) => p.url === '/api/agente/reclamos').flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
    await new Promise((r) => setTimeout(r));
    harness.detectChanges();
    expect(pantalla()).toContain('Comuna de');
    expect(pantalla()).toContain('Salsipuedes');
    expect(pantalla()).toContain('Salir');
    expect(pantalla()).toContain('Mis reclamos');
    expect(pantalla()).toContain('Para hacer');
    expect(pantalla()).toContain('No tenés reclamos para hacer');
  });

  it('elAgenteVeElDetalleEnLaRutaDeSuComuna', async () => {
    entrar('AGENTE');
    await ir('/salsipuedes-cordoba/agente/reclamos/12');

    http.expectOne('/api/agente/reclamos/12').flush(null, { status: 404, statusText: 'No encontrado' });
    harness.detectChanges();
    expect(pantalla()).toContain('No encontramos ese reclamo');
  });

  it('salirCierraLaSesionYVaAlLoginDeLaComuna', async () => {
    entrar('AGENTE');
    await ir('/salsipuedes-cordoba/agente');
    http.expectOne((p) => p.url === '/api/agente/reclamos').flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });

    const salir = Array.from((harness.routeNativeElement as HTMLElement).querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Salir') as HTMLButtonElement;
    salir.click();
    await vi.waitFor(() => expect(url()).toBe('/salsipuedes-cordoba/login'));

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
  });

  it('elInicioViejoLlevaAlAgenteAMisReclamos', async () => {
    entrar('AGENTE');
    await ir('/inicio');

    expect(url()).toBe('/salsipuedes-cordoba/agente');
    http.match((p) => p.url === '/api/agente/reclamos');
  });

  it('sinSesionLlevaAlLoginDeLaComuna', async () => {
    await ir('/salsipuedes-cordoba/agente');

    expect(url()).toBe('/salsipuedes-cordoba/login');
  });

  it('losOtrosRolesNoEntranAMisReclamos', async () => {
    for (const rol of ['VECINO', 'ADMINISTRATIVO', 'ADMIN_COMUNA'] as Rol[]) {
      entrar(rol);
      await ir('/salsipuedes-cordoba/agente');

      expect(url()).toBe('/sin-permiso');
      http.match(() => true);
    }
  });

  it('elAgenteNoEntraALasPantallasDelVecinoNiDelPanel', async () => {
    entrar('AGENTE');
    await ir('/salsipuedes-cordoba/inicio');
    expect(url()).toBe('/sin-permiso');

    await ir('/salsipuedes-cordoba/panel/reclamos');
    expect(url()).toBe('/sin-permiso');
  });

  it('unAgenteDeOtraComunaPierdeLaSesionYVaAlLoginDeEsaComuna', async () => {
    entrar('AGENTE', 'rio-ceballos');
    await ir('/salsipuedes-cordoba/agente/reclamos/12');

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(url()).toBe('/salsipuedes-cordoba/login');
  });
});
