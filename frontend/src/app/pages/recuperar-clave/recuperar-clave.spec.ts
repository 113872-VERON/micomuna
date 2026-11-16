import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ComunaDto } from '../../auth/modelos';
import { RecuperarClave } from './recuperar-clave';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: null, slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
  telefono: '(0351) 000-0000',
};
const URL = '/api/comunas/salsipuedes-cordoba/recuperar-clave';

describe('RecuperarClave', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecuperarClave],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(c: ComunaDto = comuna): ComponentFixture<RecuperarClave> {
    const fixture = TestBed.createComponent(RecuperarClave);
    fixture.componentRef.setInput('slug', 'salsipuedes-cordoba');
    fixture.componentRef.setInput('comuna', c);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  function pedir(f: ComponentFixture<unknown>, correo: string) {
    const campo = el(f).querySelector('#correo') as HTMLInputElement;
    campo.value = correo;
    campo.dispatchEvent(new Event('input'));
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  it('pideSoloElCorreo', () => {
    const f = crear();
    expect(el(f).textContent).toContain('Recuperar mi contraseña');
    expect(el(f).querySelectorAll('input').length).toBe(1);
  });

  it('sinCorreoNoEnviaNada', () => {
    const f = crear();
    pedir(f, '');
    http.expectNone(URL);
    expect(el(f).textContent).toContain('Escribí tu correo');
  });

  it('recuperarSiempreMuestraElMismoMensaje', () => {
    const f = crear();
    pedir(f, ' Ana.Lopez@Correo.com ');

    const pedido = http.expectOne(URL);
    expect(pedido.request.body).toEqual({ correo: 'ana.lopez@correo.com' });
    pedido.flush(null, { status: 202, statusText: 'Accepted' });
    f.detectChanges();

    expect(el(f).textContent).toContain('Revisá tu correo');
    expect(el(f).textContent).toContain('ana.lopez@correo.com');
    expect(el(f).textContent).toContain('Si');
    expect(el(f).textContent).toContain('Spam');
  });

  it('permiteEnviarDeNuevo', () => {
    const f = crear();
    pedir(f, 'ana@correo.com');
    http.expectOne(URL).flush(null, { status: 202, statusText: 'Accepted' });
    f.detectChanges();

    const reenviar = Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes('Enviar de nuevo'));
    reenviar!.click();

    http.expectOne(URL).flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('siFallaLaRedSeAvisaYSeSigueEnElFormulario', () => {
    const f = crear();
    pedir(f, 'ana@correo.com');
    http.expectOne(URL).error(new ProgressEvent('error'));
    f.detectChanges();

    expect(el(f).textContent).toContain('No pudimos conectarnos');
    expect(el(f).querySelector('#correo')).not.toBeNull();
  });

  it('laAyudaMuestraElTelefonoSiLaComunaLoCargo', () => {
    expect(el(crear()).textContent).toContain('(0351) 000-0000');
    expect(el(crear({ ...comuna, telefono: null })).textContent).toContain('Acercate a la comuna');
  });

  it('volverAIngresarLlevaAlLoginDeLaComuna', () => {
    const enlaces = Array.from(el(crear()).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/salsipuedes-cordoba/login');
  });
});
