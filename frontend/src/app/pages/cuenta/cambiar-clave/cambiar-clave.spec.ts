import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { CambiarClave } from './cambiar-clave';

describe('CambiarClave', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CambiarClave>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [CambiarClave], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 7, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'AGENTE', comunaSlug: 'salsipuedes-cordoba' },
    });
    fixture = TestBed.createComponent(CambiarClave);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = () => el().textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const campo = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;
  function llenar(actual: string, nueva: string, repetida: string): void {
    for (const [id, valor] of [['clave-actual', actual], ['clave-nueva', nueva], ['clave-repetida', repetida]]) {
      campo(id).value = valor;
      campo(id).dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  }
  function enviar(): void {
    (el().querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }
  const error = (id: string) => el().querySelector(`#${id}`)?.textContent ?? '';

  it('conTodoVacioPideCadaCampoYNoEnvia', () => {
    enviar();

    http.expectNone('/api/auth/cambiar-clave');
    expect(error('error-clave-actual')).toContain('contraseña actual');
    expect(error('error-clave-nueva')).toContain('Elegí una contraseña nueva');
    expect(error('error-clave-repetida')).toContain('otra vez');
  });

  it('laNuevaTieneQueTenerDe8A72Caracteres', () => {
    llenar('vieja123', 'corta', 'corta');
    enviar();
    expect(error('error-clave-nueva')).toContain('entre 8 y 72');
    http.expectNone('/api/auth/cambiar-clave');

    llenar('vieja123', 'a'.repeat(73), 'a'.repeat(73));
    enviar();
    expect(error('error-clave-nueva')).toContain('entre 8 y 72');
    http.expectNone('/api/auth/cambiar-clave');
  });

  it('laRepetidaTieneQueCoincidirConLaNuevaAunqueSeCambieLaNuevaDespues', () => {
    llenar('vieja123', 'nueva-clave-1', 'nueva-clave-2');
    enviar();
    expect(error('error-clave-repetida')).toContain('tienen que ser iguales');
    http.expectNone('/api/auth/cambiar-clave');

    llenar('vieja123', 'nueva-clave-2', 'nueva-clave-2');
    expect(el().querySelector('#error-clave-repetida')).toBeNull();
    campo('clave-nueva').value = 'nueva-clave-3';
    campo('clave-nueva').dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(error('error-clave-repetida')).toContain('tienen que ser iguales');
  });

  it('conDatosValidosEnviaLaActualYLaNuevaYConfirmaConUn204', () => {
    llenar('vieja123', 'nueva-clave-1', 'nueva-clave-1');
    enviar();

    const pedido = http.expectOne('/api/auth/cambiar-clave');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ claveActual: 'vieja123', claveNueva: 'nueva-clave-1' });
    pedido.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(texto()).toContain('Cambiaste tu contraseña');
    expect(el().querySelector('[role="status"]')?.textContent).toContain('usá la contraseña nueva');
    expect(el().querySelector('form')).toBeNull();
    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(true);
  });

  it('siElServidorResponde400MuestraSuMensajeYConservaLoEscrito', () => {
    llenar('mal-puesta', 'nueva-clave-1', 'nueva-clave-1');
    enviar();

    http.expectOne('/api/auth/cambiar-clave').flush({ detail: 'La contraseña actual no es correcta.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(el().querySelector('.caja-error')?.textContent).toContain('La contraseña actual no es correcta.');
    expect(campo('clave-nueva').value).toBe('nueva-clave-1');
    expect(texto()).not.toContain('Cambiaste tu contraseña');
  });

  it('elBotonMostrarCambiaElTipoDeLosTresCampos', () => {
    expect(['clave-actual', 'clave-nueva', 'clave-repetida'].every((id) => campo(id).type === 'password')).toBe(true);

    (el().querySelector('.mostrar input') as HTMLInputElement).click();
    fixture.detectChanges();

    expect(['clave-actual', 'clave-nueva', 'clave-repetida'].every((id) => campo(id).type === 'text')).toBe(true);
  });
});
