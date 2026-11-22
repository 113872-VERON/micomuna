import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UsuarioComunaDto } from '../../../../auth/modelos';
import { NuevoUsuario } from './nuevo-usuario';

const LISTA_URL = '/api/usuarios';
const nuevo: UsuarioComunaDto = {
  id: 7, nombre: 'Bartolomé Fitzgerald', correo: 'bartolome.fitzgerald@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'PENDIENTE', creadoEn: '2026-11-03T10:00:00Z',
};

describe('NuevoUsuario', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NuevoUsuario],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(): ComponentFixture<NuevoUsuario> {
    const f = TestBed.createComponent(NuevoUsuario);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ') ?? '';
  const form = (f: ComponentFixture<unknown>) => el(f).querySelector('form#form-nuevo, form') as HTMLFormElement;
  function boton(f: ComponentFixture<unknown>, etiqueta: string): HTMLButtonElement {
    const b = Array.from(el(f).querySelectorAll('button')).find((x) => x.textContent?.trim() === etiqueta);
    if (!b) {
      throw new Error(`No hay un botón "${etiqueta}"`);
    }
    return b;
  }
  function escribir(f: ComponentFixture<unknown>, selector: string, valor: string) {
    const campo = el(f).querySelector(selector) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    f.detectChanges();
  }
  function enviar(f: ComponentFixture<unknown>, formulario: HTMLFormElement) {
    formulario.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }
  function completar(f: ComponentFixture<unknown>, rol = 'AGENTE') {
    escribir(f, '#nuevo-nombre', '  Bartolomé Fitzgerald ');
    escribir(f, '#nuevo-correo', 'Bartolome.Fitzgerald@Salsipuedes.gob.ar');
    const radio = el(f).querySelector(`input[type="radio"][value="${rol}"]`) as HTMLInputElement;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    f.detectChanges();
  }
  const enlaceALaLista = (f: ComponentFixture<unknown>, etiqueta: string) =>
    Array.from(el(f).querySelectorAll('a')).find((a) => a.textContent?.trim() === etiqueta);

  it('cancelarVuelveALaListaSinCrearNada', () => {
    const f = crear();
    expect(enlaceALaLista(f, 'Cancelar')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/usuarios');
    http.expectNone((r) => r.method === 'POST');
  });

  it('elBotonDeInformacionTieneNombreAccesibleYMuestraSuTexto', () => {
    const f = crear();
    const info = el(f).querySelector('.titulo button') as HTMLButtonElement;

    expect(info.getAttribute('aria-label')).toBe('Información sobre el alta de usuarios');
    expect(info.textContent?.trim()).toBe('info');
    const ayuda = document.getElementById(info.getAttribute('aria-describedby')!)!;
    expect(ayuda.textContent).toBe(
      'Le mandamos un correo con un enlace para que elija su propia contraseña.\n' +
        'Los vecinos no se dan de alta acá: se registran solos con el enlace de la comuna.',
    );
    info.dispatchEvent(new Event('mouseenter'));
    expect(ayuda.style.visibility).toBe('visible');
    info.dispatchEvent(new Event('mouseleave'));
    info.dispatchEvent(new Event('focus'));
    expect(ayuda.style.visibility).toBe('visible');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(ayuda.style.visibility).toBe('hidden');
  });

  it('losTextosViejosYaNoEstanEnElFormulario', () => {
    const f = crear();
    const t = form(f).textContent ?? '';
    expect(t).not.toContain('Los vecinos no se dan de alta');
    expect(t).not.toContain('Le mandamos un correo');
    expect(t).not.toContain('Vos no vas a ver ninguna clave');
    expect(texto(f)).not.toContain('Vos no vas a ver ninguna clave');
  });

  it('soloOfreceRolesDelPersonalYNoEnviaSiFaltanDatos', () => {
    const f = crear();
    const roles = Array.from(form(f).querySelectorAll<HTMLInputElement>('input[type="radio"]')).map((r) => r.value);
    expect(roles).toEqual(['ADMINISTRATIVO', 'AGENTE', 'ADMIN_COMUNA']);
    expect(form(f).textContent).toContain('Personal administrativo');
    expect(form(f).textContent).not.toContain('Vecino');

    enviar(f, form(f));
    http.expectNone((r) => r.method === 'POST');
    expect(texto(f)).toContain('Escribí el nombre y apellido.');
    expect(texto(f)).toContain('Escribí el correo.');
    expect(texto(f)).toContain('Elegí qué va a hacer.');
  });

  it('conInvitacionEnviadaMuestraElResultadoYVuelveALaLista', () => {
    const f = crear();
    completar(f);
    enviar(f, form(f));

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === LISTA_URL);
    expect(pedido.request.body).toEqual({ nombre: 'Bartolomé Fitzgerald', correo: 'bartolome.fitzgerald@salsipuedes.gob.ar', rol: 'AGENTE' });
    pedido.flush({ usuario: nuevo, invitacionEnviada: true }, { status: 201, statusText: 'Created' });
    f.detectChanges();

    expect(el(f).querySelector('[role="status"]')?.textContent).toContain('Invitación enviada a bartolome.fitzgerald@salsipuedes.gob.ar');
    expect(el(f).querySelector('form')).toBeNull();
    expect(enlaceALaLista(f, 'Volver a la lista')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/usuarios');
  });

  it('siElCorreoNoSalioLoAvisaPermiteReintentarYVolverALaLista', () => {
    const f = crear();
    completar(f, 'ADMINISTRATIVO');
    enviar(f, form(f));
    http.expectOne((r) => r.method === 'POST' && r.url === LISTA_URL).flush(
      { usuario: { ...nuevo, rol: 'ADMINISTRATIVO' }, invitacionEnviada: false },
      { status: 201, statusText: 'Created' },
    );
    f.detectChanges();

    expect(texto(f)).toContain('Creamos el usuario, pero el correo no salió');
    expect(texto(f)).toContain('bartolome.fitzgerald@salsipuedes.gob.ar');
    expect(enlaceALaLista(f, 'Volver a la lista')).toBeDefined();

    boton(f, 'Reintentar el envío').click();
    http.expectOne('/api/usuarios/7/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();

    expect(texto(f)).toContain('Invitación enviada a bartolome.fitzgerald@salsipuedes.gob.ar');
  });

  it('siElCorreoNoSalioSePuedeModificarElCorreoYReenviar', () => {
    const f = crear();
    completar(f);
    enviar(f, form(f));
    http.expectOne((r) => r.method === 'POST' && r.url === LISTA_URL).flush(
      { usuario: nuevo, invitacionEnviada: false },
      { status: 201, statusText: 'Created' },
    );
    f.detectChanges();

    boton(f, 'Modificar el correo').click();
    f.detectChanges();
    escribir(f, '#nuevo-correo-corregido', 'bfitzgerald@salsipuedes.gob.ar');
    enviar(f, el(f).querySelector('app-corregir-correo form') as HTMLFormElement);

    const cambio = http.expectOne('/api/usuarios/7');
    expect(cambio.request.body).toEqual({ correo: 'bfitzgerald@salsipuedes.gob.ar' });
    cambio.flush({ ...nuevo, correo: 'bfitzgerald@salsipuedes.gob.ar' });
    http.expectOne('/api/usuarios/7/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();

    expect(texto(f)).toContain('Invitación enviada a bfitzgerald@salsipuedes.gob.ar');
  });

  it('alModificarConElMismoCorreoElAvisoNombraElBotonDeEstaPantalla', () => {
    const f = crear();
    completar(f);
    enviar(f, form(f));
    http.expectOne((r) => r.method === 'POST' && r.url === LISTA_URL).flush(
      { usuario: nuevo, invitacionEnviada: false },
      { status: 201, statusText: 'Created' },
    );
    f.detectChanges();
    boton(f, 'Modificar el correo').click();
    f.detectChanges();
    enviar(f, el(f).querySelector('app-corregir-correo form') as HTMLFormElement);

    http.expectNone('/api/usuarios/7');
    expect(texto(f)).toContain('Es el mismo correo de antes. Si está bien escrito, usá "Reintentar el envío".');
    expect(texto(f)).not.toContain('usá "Reenviar invitación".');
    // Ese botón existe en esta pantalla: aparece al cancelar la modificación.
    boton(f, 'Cancelar').click();
    f.detectChanges();
    expect(boton(f, 'Reintentar el envío')).toBeDefined();
  });

  it('unCorreoRepetidoSeMuestraEnElFormulario', () => {
    const f = crear();
    completar(f);
    enviar(f, form(f));

    http.expectOne((r) => r.method === 'POST' && r.url === LISTA_URL).flush(
      { detail: 'Ya hay un usuario con ese correo en la comuna.', codigo: 'CONFLICTO' },
      { status: 409, statusText: 'Conflict' },
    );
    f.detectChanges();

    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('Ya hay un usuario con ese correo en la comuna.');
    expect(el(f).querySelector('form')).not.toBeNull();
  });
});
