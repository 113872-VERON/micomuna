import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { UsuarioDto } from '../../../auth/modelos';
import { MisDatos } from './mis-datos';

const USUARIO: UsuarioDto = { id: 7, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' };

describe('MisDatos', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<MisDatos>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [MisDatos], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({ token: 'tk', usuario: USUARIO });
    fixture = TestBed.createComponent(MisDatos);
    fixture.detectChanges();
    http.expectOne('/api/auth/mis-datos').flush(USUARIO);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const nombre = () => el().querySelector('#mis-datos-nombre') as HTMLInputElement;
  const texto = () => el().textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const escribir = (valor: string) => {
    nombre().value = valor;
    nombre().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const guardar = () => {
    (el().querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  it('muestraElNombreEditableYElCorreoYElRolSoloDeLectura', () => {
    expect(nombre().value).toBe('Marta Gil');
    expect(el().querySelectorAll('input').length).toBe(1);
    expect(texto()).toContain('marta@correo.com');
    expect(texto()).toContain('Personal administrativo');
  });

  it('alGuardarEnviaElNombreSinEspaciosYActualizaLaSesion', () => {
    escribir('  Marta Inés Gil  ');
    guardar();

    const pedido = http.expectOne('/api/auth/mis-datos');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ nombre: 'Marta Inés Gil' });
    pedido.flush({ ...USUARIO, nombre: 'Marta Inés Gil' });
    fixture.detectChanges();

    const auth = TestBed.inject(AuthService);
    expect(auth.usuario()?.nombre).toBe('Marta Inés Gil');
    expect(JSON.parse(localStorage.getItem('micomuna.usuario')!).nombre).toBe('Marta Inés Gil');
    expect(auth.token()).toBe('tk');
    expect(texto()).toContain('Listo, guardamos tu nombre.');
  });

  it('siElServidorResponde400MuestraSuMensajeYNoCambiaLaSesion', () => {
    escribir('Otro nombre');
    guardar();

    http.expectOne('/api/auth/mis-datos').flush({ detail: 'El nombre no puede superar los 100 caracteres.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(el().querySelector('.caja-error')?.textContent).toContain('El nombre no puede superar los 100 caracteres.');
    expect(TestBed.inject(AuthService).usuario()?.nombre).toBe('Marta Gil');
    expect(texto()).not.toContain('Listo, guardamos');
  });

  it('unNombreVacioOSoloDeEspaciosNoSeEnvia', () => {
    escribir('   ');
    guardar();

    http.expectNone('/api/auth/mis-datos');
    expect(el().querySelector('#error-mis-datos-nombre')?.textContent).toContain('Escribí tu nombre');
    expect(nombre().getAttribute('aria-invalid')).toBe('true');
  });

  describe('aviso sobre el correo y el rol', () => {
    const AVISO = 'El correo y el rol no se cambian desde acá.';
    const aviso = (rol: UsuarioDto['rol']): string => {
      const usuario = { ...USUARIO, rol, comunaSlug: rol === 'SUPERADMIN' ? null : USUARIO.comunaSlug };
      TestBed.inject(AuthService).guardarSesion({ token: 'tk', usuario });
      fixture = TestBed.createComponent(MisDatos);
      fixture.detectChanges();
      http.expectOne('/api/auth/mis-datos').flush(usuario);
      fixture.detectChanges();
      return (el().querySelector('.ayuda')?.textContent ?? '').replace(/\s+/g, ' ').trim();
    };

    it('vecinoAgenteYAdministrativoSeLoPidenAQuienAdministraLaComuna', () => {
      for (const rol of ['VECINO', 'AGENTE', 'ADMINISTRATIVO'] as const) {
        expect(aviso(rol)).toBe(`${AVISO} Si necesitás cambiarlos, pedíselo a quien administra tu comuna.`);
      }
    });

    it('elAdministradorDeLaComunaSeLoPideASoporte', () => {
      expect(aviso('ADMIN_COMUNA')).toBe(`${AVISO} Si necesitás cambiarlos, pedíselo a soporte.`);
    });

    it('elSuperadminSoloVeQueNoSeCambianDesdeAca', () => {
      expect(aviso('SUPERADMIN')).toBe(AVISO);
    });
  });

  it('unNombreDeMasDe100CaracteresNoSeEnvia', () => {
    escribir('a'.repeat(101));
    guardar();

    http.expectNone('/api/auth/mis-datos');
    expect(el().querySelector('#error-mis-datos-nombre')?.textContent).toContain('hasta 100 caracteres');
  });
});
