import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';

const comuna = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE',
};

describe('Pantallas de una comuna (con las rutas reales)', () => {
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
    http.verify();
    document.documentElement.style.removeProperty('--p');
  });

  async function ir(url: string) {
    let terminada = false;
    const navegacion = harness.navigateByUrl(url).finally(() => (terminada = true));
    // El resolver pide la comuna al entrar; al pasar a otra pantalla de la misma comuna no vuelve a pedirla.
    await vi.waitFor(() => {
      http.match('/api/comunas/salsipuedes-cordoba').forEach((pedido) => pedido.flush(comuna));
      if (!terminada) {
        throw new Error('la navegación sigue en curso');
      }
    });
    await navegacion;
    harness.detectChanges();
    return harness.routeNativeElement as HTMLElement;
  }

  it('todasMuestranElNombreDeLaComuna', async () => {
    for (const url of ['/salsipuedes-cordoba/login', '/salsipuedes-cordoba/registro', '/salsipuedes-cordoba/recuperar', '/salsipuedes-cordoba/restablecer?codigo=abc']) {
      const pantalla = await ir(url);
      expect(pantalla.textContent, url).toContain('Comuna de');
      expect(pantalla.textContent, url).toContain('Salsipuedes');
    }
  });

  it('laRutaDeLaComunaSinPantallaVaAlLogin', async () => {
    const pantalla = await ir('/salsipuedes-cordoba');
    expect(pantalla.textContent).toContain('Ingresar');
  });

  it('aplicaElColorDeLaComunaAlEntrar', async () => {
    await ir('/salsipuedes-cordoba/login');
    expect(document.documentElement.style.getPropertyValue('--p')).toBe('#9A4A0B');
  });

  it('unaComunaInexistenteVaANoEncontrada', async () => {
    const navegacion = harness.navigateByUrl('/no-existe/login');
    await vi.waitFor(() => http.expectOne('/api/comunas/no-existe').flush(null, { status: 404, statusText: 'No encontrada' }));
    await navegacion;
    harness.detectChanges();
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('No encontramos esta página');
  });
});
