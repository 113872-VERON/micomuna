import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../auth/auth.service';
import { Rol } from '../../auth/modelos';

const comuna = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };

describe('Rutas del vecino (con las rutas reales)', () => {
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

  afterEach(() => {
    http.match(() => true);
    document.documentElement.style.removeProperty('--p');
  });

  function entrar(rol: Rol, comunaSlug: string | null = 'salsipuedes-cordoba') {
    TestBed.inject(AuthService).guardarSesion({ token: 'tk', usuario: { id: 1, nombre: 'Rosa', correo: 'r@c.com', rol, comunaSlug } });
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

  it('elVecinoVeSuInicioEnLaRutaDeSuComuna', async () => {
    entrar('VECINO');
    await ir('/salsipuedes-cordoba/inicio');

    expect(url()).toBe('/salsipuedes-cordoba/inicio');
    http.expectOne((p) => p.url === '/api/reclamos/mios').flush({ reclamos: [], pagina: 0, tamanio: 10, total: 0 });
    harness.detectChanges();
    expect(pantalla()).toContain('Comuna de');
    expect(pantalla()).toContain('Todavía no reportaste nada');
  });

  it('elVecinoVeElDetalleEnLaRutaDeSuComuna', async () => {
    entrar('VECINO');
    await ir('/salsipuedes-cordoba/reclamos/12');

    http.expectOne('/api/reclamos/mios/12').flush(null, { status: 404, statusText: 'No encontrado' });
    harness.detectChanges();
    expect(pantalla()).toContain('No encontramos ese reclamo');
  });

  it('elInicioViejoLlevaAlVecinoAlDeSuComuna', async () => {
    entrar('VECINO');
    await ir('/inicio');

    expect(url()).toBe('/salsipuedes-cordoba/inicio');
  });

  it('sinSesionElInicioDeLaComunaLlevaAlLogin', async () => {
    await ir('/salsipuedes-cordoba/inicio');

    expect(url()).toBe('/salsipuedes-cordoba/login');
  });

  it('elPersonalNoEntraAlInicioDelVecino', async () => {
    entrar('AGENTE');
    await ir('/salsipuedes-cordoba/inicio');

    expect(url()).toBe('/sin-permiso');
  });

  it('unVecinoDeOtraComunaPierdeLaSesionYVaAlLoginDeEsaComuna', async () => {
    entrar('VECINO', 'rio-ceballos');
    await ir('/salsipuedes-cordoba/reclamos/12');

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(url()).toBe('/salsipuedes-cordoba/login');
  });
});
