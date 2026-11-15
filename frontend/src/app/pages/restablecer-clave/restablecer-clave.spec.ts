import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ComunaDto } from '../../auth/modelos';
import { RestablecerClave } from './restablecer-clave';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: null, slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
};
const URL = '/api/comunas/salsipuedes-cordoba/restablecer-clave';

describe('RestablecerClave', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RestablecerClave],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(codigo: string | undefined): ComponentFixture<RestablecerClave> {
    const fixture = TestBed.createComponent(RestablecerClave);
    fixture.componentRef.setInput('slug', 'salsipuedes-cordoba');
    fixture.componentRef.setInput('comuna', comuna);
    fixture.componentRef.setInput('codigo', codigo);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  function elegir(f: ComponentFixture<unknown>, clave: string) {
    const campo = el(f).querySelector('#clave') as HTMLInputElement;
    campo.value = clave;
    campo.dispatchEvent(new Event('input'));
    f.detectChanges();
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  it('laContrasenaNuevaMuestraLaReglaDeLargo', () => {
    const f = crear('abc');
    expect(el(f).textContent).toContain('Tiene 8 o más caracteres');
    expect(el(f).textContent).toContain('Elegí tu contraseña nueva');
  });

  it('restablecerTomaElCodigoDeLaURL', () => {
    const f = crear('codigo-del-correo');
    elegir(f, 'miclave2026');

    const pedido = http.expectOne(URL);
    expect(pedido.request.body).toEqual({ codigo: 'codigo-del-correo', claveNueva: 'miclave2026' });
    pedido.flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();

    expect(el(f).textContent).toContain('Listo, ya cambiaste tu contraseña');
    const enlaces = Array.from(el(f).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/salsipuedes-cordoba/login');
  });

  it('unaClaveCortaNoSeEnvia', () => {
    const f = crear('abc');
    elegir(f, '1234567');

    http.expectNone(URL);
    expect(el(f).textContent).toContain('entre 8 y 72');
  });

  it('elEnlaceVencidoMuestraElMensajeYOfreceUnoNuevo', () => {
    const f = crear('viejo');
    elegir(f, 'miclave2026');

    http.expectOne(URL).flush(
      { detail: 'El enlace no es válido', codigo: 'PETICION_INVALIDA' },
      { status: 400, statusText: 'Bad Request' },
    );
    f.detectChanges();

    expect(el(f).textContent).toContain('Este enlace ya no sirve');
    const enlaces = Array.from(el(f).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/salsipuedes-cordoba/recuperar');
  });

  it('sinCodigoEnLaURLMuestraElEnlaceVencido', () => {
    const f = crear(undefined);
    expect(el(f).textContent).toContain('Este enlace ya no sirve');
    expect(el(f).querySelector('form')).toBeNull();
  });

  it('siFallaLaRedSeAvisaYSePuedeReintentar', () => {
    const f = crear('abc');
    elegir(f, 'miclave2026');

    http.expectOne(URL).error(new ProgressEvent('error'));
    f.detectChanges();

    expect(el(f).textContent).toContain('No pudimos conectarnos');
    expect(el(f).querySelector('form')).not.toBeNull();
  });
});
