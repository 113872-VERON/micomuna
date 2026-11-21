import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { Login } from './login';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: null, slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
};
const URL_LOGIN = '/api/comunas/salsipuedes-cordoba/login';

describe('Login', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function crear(): ComponentFixture<Login> {
    const fixture = TestBed.createComponent(Login);
    fixture.componentRef.setInput('slug', 'salsipuedes-cordoba');
    fixture.componentRef.setInput('comuna', comuna);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  function escribir(f: ComponentFixture<unknown>, id: string, valor: string) {
    const campo = el(f).querySelector(`#${id}`) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }
  function enviar(f: ComponentFixture<unknown>) {
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  it('loginInvalidoNoEnviaElFormulario', () => {
    const f = crear();
    enviar(f);

    http.expectNone(URL_LOGIN);
    expect(el(f).textContent).toContain('Escribí tu correo');
    expect(el(f).textContent).toContain('Escribí tu contraseña');
  });

  it('unCorreoMalEscritoSeExplica', () => {
    const f = crear();
    escribir(f, 'correo', 'rosa-sin-arroba');
    escribir(f, 'clave', 'miclave123');
    enviar(f);

    http.expectNone(URL_LOGIN);
    expect(el(f).textContent).toContain('ejemplo@correo.com');
  });

  it('loginCorrectoGuardaLaSesionYVaAInicio', () => {
    const f = crear();
    escribir(f, 'correo', '  Rosa@Correo.com ');
    escribir(f, 'clave', 'miclave123');
    enviar(f);

    const pedido = http.expectOne(URL_LOGIN);
    expect(pedido.request.body).toEqual({ correo: 'rosa@correo.com', clave: 'miclave123' });
    pedido.flush({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });

    expect(TestBed.inject(AuthService).token()).toBe('tk');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/inicio');
  });

  it('elAdministradorDeLaComunaVaASuPanelDeUsuarios', () => {
    const f = crear();
    escribir(f, 'correo', 'sergio@correo.com');
    escribir(f, 'clave', 'miclave123');
    enviar(f);

    http.expectOne(URL_LOGIN).flush({
      token: 'tk',
      usuario: { id: 1, nombre: 'Sergio', correo: 'sergio@correo.com', rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/panel/usuarios');
  });

  it('elPersonalQueNoEsAdministradorSigueYendoAInicio', () => {
    for (const rol of ['ADMINISTRATIVO', 'AGENTE'] as const) {
      const f = crear();
      escribir(f, 'correo', 'marta@correo.com');
      escribir(f, 'clave', 'miclave123');
      enviar(f);

      http.expectOne(URL_LOGIN).flush({
        token: 'tk',
        usuario: { id: 2, nombre: 'Marta', correo: 'marta@correo.com', rol, comunaSlug: 'salsipuedes-cordoba' },
      });

      expect(router.navigateByUrl).toHaveBeenLastCalledWith('/inicio');
      f.destroy();
    }
  });

  it('mostrarElErrorDeCredencialesIncorrectas', () => {
    const f = crear();
    escribir(f, 'correo', 'rosa@correo.com');
    escribir(f, 'clave', 'mala-clave');
    enviar(f);

    http.expectOne(URL_LOGIN).flush(
      { detail: 'Credenciales incorrectas', codigo: 'CREDENCIALES_INVALIDAS' },
      { status: 401, statusText: 'No autorizado' },
    );
    f.detectChanges();

    expect(el(f).textContent).toContain('Credenciales incorrectas');
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('siNoHayConexionLoExplica', () => {
    const f = crear();
    escribir(f, 'correo', 'rosa@correo.com');
    escribir(f, 'clave', 'miclave123');
    enviar(f);

    http.expectOne(URL_LOGIN).error(new ProgressEvent('error'));
    f.detectChanges();

    expect(el(f).textContent).toContain('No pudimos conectarnos');
  });

  it('noEnviaDosVecesMientrasEspera', () => {
    const f = crear();
    escribir(f, 'correo', 'rosa@correo.com');
    escribir(f, 'clave', 'miclave123');
    enviar(f);
    enviar(f);

    http.expectOne(URL_LOGIN).flush({}, { status: 500, statusText: 'Error' });
  });

  it('llevaAlRegistroYALaRecuperacionDeLaMismaComuna', () => {
    const f = crear();
    const enlaces = Array.from(el(f).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/salsipuedes-cordoba/registro');
    expect(enlaces).toContain('/salsipuedes-cordoba/recuperar');
  });
});
