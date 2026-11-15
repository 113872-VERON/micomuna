import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { AdminLogin } from './admin-login';

const URL = '/api/admin/login';

describe('AdminLogin', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AdminLogin],
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

  function crear(): ComponentFixture<AdminLogin> {
    const fixture = TestBed.createComponent(AdminLogin);
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

  it('muestraLaMarcaDeLaPlataformaYNoUnaComuna', () => {
    const texto = el(crear()).textContent;
    expect(texto).toContain('MiComuna');
    expect(texto).toContain('Plataforma');
    expect(texto).not.toContain('Comuna de');
  });

  it('loginInvalidoNoEnviaElFormulario', () => {
    const f = crear();
    enviar(f);
    http.expectNone(URL);
    expect(el(f).textContent).toContain('Escribí tu correo');
    expect(el(f).textContent).toContain('Escribí tu contraseña');
  });

  it('loginCorrectoGuardaLaSesionYVaALasComunas', () => {
    const f = crear();
    escribir(f, 'correo', ' Admin@MiComuna.com ');
    escribir(f, 'clave', 'una-clave-larga');
    enviar(f);

    const pedido = http.expectOne(URL);
    expect(pedido.request.body).toEqual({ correo: 'admin@micomuna.com', clave: 'una-clave-larga' });
    pedido.flush({
      token: 'tk-admin',
      usuario: { id: 1, nombre: 'Andrés Verón', correo: 'admin@micomuna.com', rol: 'SUPERADMIN', comunaSlug: null },
    });

    expect(TestBed.inject(AuthService).usuario()?.rol).toBe('SUPERADMIN');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/comunas');
  });

  it('mostrarElErrorDeCredencialesIncorrectas', () => {
    const f = crear();
    escribir(f, 'correo', 'admin@micomuna.com');
    escribir(f, 'clave', 'mala');
    enviar(f);

    http.expectOne(URL).flush({ detail: 'Credenciales incorrectas', codigo: 'CREDENCIALES_INVALIDAS' }, { status: 401, statusText: 'No autorizado' });
    f.detectChanges();

    expect(el(f).textContent).toContain('Credenciales incorrectas');
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
  });
});
