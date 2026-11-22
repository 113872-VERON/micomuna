import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { Registro } from './registro';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: null, slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
};
const URL_REGISTRO = '/api/comunas/salsipuedes-cordoba/registro';

describe('Registro', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Registro],
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

  function crear(): ComponentFixture<Registro> {
    const fixture = TestBed.createComponent(Registro);
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
    f.detectChanges();
  }
  function enviar(f: ComponentFixture<unknown>) {
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }
  function completar(f: ComponentFixture<unknown>, clave: string) {
    escribir(f, 'nombre', '  Rosa Gimenez ');
    escribir(f, 'correo', 'Rosa@Correo.com');
    escribir(f, 'clave', clave);
  }

  it('registroValidaLargoDeClave', () => {
    const corta = crear();
    completar(corta, '1234567');
    enviar(corta);
    http.expectNone(URL_REGISTRO);
    expect(el(corta).textContent).toContain('entre 8 y 72');

    const larga = crear();
    completar(larga, 'a'.repeat(73));
    enviar(larga);
    http.expectNone(URL_REGISTRO);
    expect(el(larga).textContent).toContain('entre 8 y 72');
  });

  it('registroValidaQueEstenLosTresCampos', () => {
    const f = crear();
    enviar(f);

    http.expectNone(URL_REGISTRO);
    expect(el(f).textContent).toContain('Escribí tu nombre');
    expect(el(f).textContent).toContain('Escribí tu correo');
  });

  it('registroCorrectoGuardaLaSesionYVaAInicio', () => {
    const f = crear();
    completar(f, 'miclave123');
    enviar(f);

    const pedido = http.expectOne(URL_REGISTRO);
    expect(pedido.request.body).toEqual({ nombre: 'Rosa Gimenez', correo: 'rosa@correo.com', clave: 'miclave123' });
    pedido.flush(
      { token: 'tk', usuario: { id: 3, nombre: 'Rosa Gimenez', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' } },
      { status: 201, statusText: 'Created' },
    );

    expect(TestBed.inject(AuthService).token()).toBe('tk');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/inicio');
  });

  it('registroMuestraElErrorDeCorreoRepetido', () => {
    const f = crear();
    completar(f, 'miclave123');
    enviar(f);

    http.expectOne(URL_REGISTRO).flush(
      { detail: 'Ya hay una cuenta con ese correo en esta comuna', codigo: 'CONFLICTO' },
      { status: 409, statusText: 'Conflicto' },
    );
    f.detectChanges();

    expect(el(f).textContent).toContain('Ya hay una cuenta con ese correo');
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
  });

  it('laReglaDeLargoSeMarcaAlCumplirse', () => {
    const f = crear();
    const regla = () => el(f).querySelector('.reglas div');

    escribir(f, 'clave', '1234');
    expect(regla()?.classList.contains('cumplida')).toBe(false);

    escribir(f, 'clave', '12345678');
    expect(regla()?.classList.contains('cumplida')).toBe(true);
    expect(regla()?.textContent).toContain('Tiene 8 o más caracteres');
  });

  it('llevaAlLoginDeLaMismaComuna', () => {
    const enlaces = Array.from(el(crear()).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/salsipuedes-cordoba/login');
  });
});
