import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { UsuarioComunaDto } from '../../../auth/modelos';
import { Usuarios, conPuntoFinal } from './usuarios';

describe('conPuntoFinal', () => {
  it('agregaElPuntoSoloCuandoFalta', () => {
    expect(conPuntoFinal('Probá en unos minutos')).toBe('Probá en unos minutos.');
    expect(conPuntoFinal('Probá en unos minutos.')).toBe('Probá en unos minutos.');
    expect(conPuntoFinal('¿Está bien escrito?')).toBe('¿Está bien escrito?');
    expect(conPuntoFinal('  Sin conexión  ')).toBe('Sin conexión.');
  });
});

const creadoEn = '2026-11-03T10:00:00Z';
const LISTA: UsuarioComunaDto[] = [
  { id: 1, nombre: 'Sergio Vera', correo: 'sergio.vera@salsipuedes.gob.ar', rol: 'ADMIN_COMUNA', estado: 'ACTIVO', creadoEn },
  { id: 2, nombre: 'Marta Gómez', correo: 'marta.gomez@salsipuedes.gob.ar', rol: 'ADMINISTRATIVO', estado: 'ACTIVO', creadoEn },
  { id: 3, nombre: 'Lucía Paz', correo: 'lucia.paz@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'ACTIVO', creadoEn },
  { id: 4, nombre: 'Julieta Romero', correo: 'julieta.romero@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'PENDIENTE', creadoEn },
  { id: 5, nombre: 'Raúl Ibarra', correo: 'raul.ibarra@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'INACTIVO', creadoEn },
  { id: 6, nombre: 'Rosa Fernández', correo: 'rosa@correo.com', rol: 'VECINO', estado: 'ACTIVO', creadoEn },
];

describe('Usuarios', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Usuarios],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    // Sergio (id 1) es el administrador que ingresó.
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Sergio Vera', correo: 'sergio.vera@salsipuedes.gob.ar', rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function crear(lista: UsuarioComunaDto[] = LISTA): ComponentFixture<Usuarios> {
    const f = TestBed.createComponent(Usuarios);
    f.detectChanges();
    http.expectOne('/api/usuarios').flush(lista);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ') ?? '';
  const filas = (f: ComponentFixture<unknown>) =>
    Array.from(el(f).querySelectorAll<HTMLElement>('tr.fila')).map((tr) => Number(tr.dataset['usuario']));
  const fila = (f: ComponentFixture<unknown>, id: number) => el(f).querySelector<HTMLElement>(`tr[data-usuario="${id}"]`)!;

  function boton(contenedor: HTMLElement, etiqueta: string): HTMLButtonElement {
    const encontrado = Array.from(contenedor.querySelectorAll('button')).find((b) => b.textContent?.trim() === etiqueta);
    if (!encontrado) {
      throw new Error(`No hay un botón "${etiqueta}"`);
    }
    return encontrado;
  }
  function tocar(f: ComponentFixture<unknown>, contenedor: HTMLElement, etiqueta: string) {
    boton(contenedor, etiqueta).click();
    f.detectChanges();
  }
  function escribir(f: ComponentFixture<unknown>, selector: string, valor: string) {
    const campo = el(f).querySelector(selector) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    f.detectChanges();
  }
  function elegir(f: ComponentFixture<unknown>, selector: string, valor: string) {
    const campo = el(f).querySelector(selector) as HTMLSelectElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    f.detectChanges();
  }
  function enviar(f: ComponentFixture<unknown>, form: HTMLFormElement) {
    form.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  describe('lista', () => {
    it('muestraLosUsuariosEnElOrdenDelServidorConRolYEstadoEnTextoClaro', () => {
      const f = crear();

      expect(filas(f)).toEqual([1, 2, 3, 4, 5, 6]);
      expect((fila(f, 2).querySelector('select') as HTMLSelectElement).selectedOptions[0].textContent?.trim()).toBe('Personal administrativo');
      expect((fila(f, 6).querySelector('select') as HTMLSelectElement).selectedOptions[0].textContent?.trim()).toBe('Vecino');
      expect((fila(f, 1).querySelector('select') as HTMLSelectElement).selectedOptions[0].textContent?.trim()).toBe('Administrador');
      expect(fila(f, 3).textContent).toContain('Activo');
      expect(fila(f, 4).textContent).toContain('Pendiente');
      expect(fila(f, 5).textContent).toContain('Inactivo');
      expect(fila(f, 3).textContent).toContain('lucia.paz@salsipuedes.gob.ar');
      expect(texto(f)).toContain('6 usuarios');
    });

    it('cadaEstadoTieneSusAcciones', () => {
      const f = crear();

      expect(() => boton(fila(f, 3), 'Desactivar')).not.toThrow();
      expect(() => boton(fila(f, 5), 'Activar')).not.toThrow();
      expect(() => boton(fila(f, 4), 'Reenviar invitación')).not.toThrow();
      expect(() => boton(fila(f, 4), 'Corregir el correo')).not.toThrow();
      expect(() => boton(fila(f, 3), 'Reenviar invitación')).toThrow();
    });

    it('siNoCargaLoExplicaYPermiteReintentar', () => {
      const f = TestBed.createComponent(Usuarios);
      f.detectChanges();
      http.expectOne('/api/usuarios').flush({}, { status: 500, statusText: 'Error' });
      f.detectChanges();
      expect(texto(f)).toContain('No pudimos cargar los usuarios');

      tocar(f, el(f), 'Reintentar');
      http.expectOne('/api/usuarios').flush(LISTA);
      f.detectChanges();
      expect(filas(f).length).toBe(6);
    });
  });

  describe('filtro y búsqueda', () => {
    it('filtraPorRol', () => {
      const f = crear();
      elegir(f, '#filtro-rol', 'AGENTE');

      expect(filas(f)).toEqual([3, 4, 5]);
      expect(texto(f)).toContain('Mostrando 3 de 6 usuarios');
    });

    it('buscaPorNombreSinImportarTildesNiMayusculas', () => {
      const f = crear();
      escribir(f, '#buscar-usuario', 'LUCIA');
      expect(filas(f)).toEqual([3]);

      escribir(f, '#buscar-usuario', 'raúl');
      expect(filas(f)).toEqual([5]);
    });

    it('buscaPorCorreoYSeCombinaConElRol', () => {
      const f = crear();
      escribir(f, '#buscar-usuario', 'salsipuedes.gob.ar');
      expect(filas(f)).toEqual([1, 2, 3, 4, 5]);

      elegir(f, '#filtro-rol', 'ADMINISTRATIVO');
      expect(filas(f)).toEqual([2]);
      expect(texto(f)).toContain('Mostrando 1 de 6 usuarios');
    });

    it('sinCoincidenciasLoDiceYPermiteVerTodos', () => {
      const f = crear();
      escribir(f, '#buscar-usuario', 'bartolomé');

      expect(filas(f)).toEqual([]);
      expect(texto(f)).toContain('No hay usuarios que coincidan');

      tocar(f, el(f), 'Ver todos');
      expect(filas(f).length).toBe(6);
      expect((el(f).querySelector('#buscar-usuario') as HTMLInputElement).value).toBe('');
    });
  });

  describe('rol', () => {
    it('cambiarElRolPideConfirmacionYRecienDespuesLoGuarda', () => {
      const f = crear();
      elegir(f, '#rol-3', 'ADMINISTRATIVO');

      expect(texto(f)).toContain('¿Cambiar el rol de Lucía Paz a Personal administrativo?');
      http.expectNone('/api/usuarios/3');

      tocar(f, el(f), 'Sí, cambiar el rol');
      const pedido = http.expectOne('/api/usuarios/3');
      expect(pedido.request.method).toBe('PATCH');
      expect(pedido.request.body).toEqual({ rol: 'ADMINISTRATIVO' });
      pedido.flush({ ...LISTA[2], rol: 'ADMINISTRATIVO' });
      f.detectChanges();

      expect(texto(f)).toContain('Lucía Paz ahora es Personal administrativo');
      expect((el(f).querySelector('#rol-3') as HTMLSelectElement).value).toBe('ADMINISTRATIVO');
      expect(texto(f)).not.toContain('¿Cambiar el rol');
    });

    it('mientrasSeGuardaMuestraElRolNuevoYSiElServidorLoRechazaVuelveAlAnterior', () => {
      const f = crear();
      elegir(f, '#rol-2', 'VECINO');
      tocar(f, el(f), 'Sí, cambiar el rol');

      const selector = el(f).querySelector('#rol-2') as HTMLSelectElement;
      expect(selector.value).toBe('VECINO');
      expect(selector.disabled).toBe(true);

      http.expectOne('/api/usuarios/2').flush(
        { detail: 'Ya hay un cambio pendiente para este usuario.', codigo: 'CONFLICTO' },
        { status: 409, statusText: 'Conflict' },
      );
      f.detectChanges();

      expect(selector.value).toBe('ADMINISTRATIVO');
      expect(selector.disabled).toBe(false);
      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('Ya hay un cambio pendiente para este usuario.');
    });

    it('cancelarDejaElRolComoEstaba', () => {
      const f = crear();
      elegir(f, '#rol-3', 'ADMIN_COMUNA');
      expect(texto(f)).toContain('¿Cambiar el rol de Lucía Paz a Administrador?');

      tocar(f, el(f), 'Cancelar');

      expect(texto(f)).not.toContain('¿Cambiar el rol');
      expect((el(f).querySelector('#rol-3') as HTMLSelectElement).value).toBe('AGENTE');
      http.expectNone('/api/usuarios/3');
    });
  });

  describe('estado', () => {
    it('desactivarPideConfirmacionYActualizaLaFila', () => {
      const f = crear();
      tocar(f, fila(f, 2), 'Desactivar');

      expect(texto(f)).toContain('¿Desactivar a Marta Gómez?');
      http.expectNone('/api/usuarios/2');

      tocar(f, el(f), 'Sí, desactivar');
      const pedido = http.expectOne('/api/usuarios/2');
      expect(pedido.request.method).toBe('PATCH');
      expect(pedido.request.body).toEqual({ activo: false });
      pedido.flush({ ...LISTA[1], estado: 'INACTIVO' });
      f.detectChanges();

      expect(fila(f, 2).textContent).toContain('Inactivo');
      expect(() => boton(fila(f, 2), 'Activar')).not.toThrow();
      expect(texto(f)).toContain('desactivamos a Marta Gómez');
    });

    it('activarTambienPideConfirmacion', () => {
      const f = crear();
      tocar(f, fila(f, 5), 'Activar');
      expect(texto(f)).toContain('¿Activar a Raúl Ibarra?');

      tocar(f, el(f), 'Sí, activar');
      const pedido = http.expectOne('/api/usuarios/5');
      expect(pedido.request.body).toEqual({ activo: true });
      pedido.flush({ ...LISTA[4], estado: 'ACTIVO' });
      f.detectChanges();

      expect(fila(f, 5).textContent).toContain('Activo');
      expect(el(f).querySelector('[role="status"]')?.textContent).toContain('Listo: activamos a Raúl Ibarra.');
      expect(texto(f)).not.toContain('Todavía no eligió su contraseña: usá');
    });

    it('laConfirmacionDeActivarSirveTambienParaQuienNuncaEligioContrasena', () => {
      const f = crear();
      tocar(f, fila(f, 5), 'Activar');

      expect(texto(f)).toContain(
        'Va a poder ingresar de nuevo. Si todavía no había elegido su contraseña, después vas a tener que reenviarle la invitación.',
      );
      expect(texto(f)).not.toContain('con su contraseña');
      tocar(f, el(f), 'Cancelar');
    });

    it('siAlActivarloVuelvePendienteAvisaQueHayQueReenviarLaInvitacion', () => {
      const f = crear();
      tocar(f, fila(f, 5), 'Activar');
      tocar(f, el(f), 'Sí, activar');

      http.expectOne('/api/usuarios/5').flush({ ...LISTA[4], estado: 'PENDIENTE' });
      f.detectChanges();

      expect(el(f).querySelector('[role="status"]')?.textContent).toContain(
        'Activamos a Raúl Ibarra. Todavía no eligió su contraseña: usá "Reenviar invitación" para mandarle un enlace nuevo.',
      );
      expect(fila(f, 5).textContent).toContain('Pendiente');
      expect(() => boton(fila(f, 5), 'Reenviar invitación')).not.toThrow();
    });

    it('mientrasUnaFilaEsperaRespuestaLosBotonesQueMandanOtroPedidoEstanDeshabilitados', () => {
      const f = crear();
      tocar(f, fila(f, 2), 'Desactivar');
      tocar(f, el(f), 'Sí, desactivar');
      const pendiente = http.expectOne('/api/usuarios/2');

      // Otra fila: reenviar queda deshabilitado, y la confirmación se puede abrir pero no confirmar todavía.
      expect(boton(fila(f, 4), 'Reenviar invitación').disabled).toBe(true);
      tocar(f, fila(f, 3), 'Desactivar');
      expect(boton(el(f), 'Sí, desactivar').disabled).toBe(true);

      pendiente.flush({ ...LISTA[1], estado: 'INACTIVO' });
      f.detectChanges();

      expect(boton(el(f), 'Sí, desactivar').disabled).toBe(false);
      tocar(f, el(f), 'Cancelar');
      expect(boton(fila(f, 4), 'Reenviar invitación').disabled).toBe(false);
    });

    it('elFormularioDeCorreccionTampocoSePuedeGuardarMientrasOtraFilaEspera', () => {
      const f = crear();
      tocar(f, fila(f, 2), 'Desactivar');
      tocar(f, el(f), 'Sí, desactivar');
      const pendiente = http.expectOne('/api/usuarios/2');

      tocar(f, fila(f, 4), 'Corregir el correo');
      const guardar = boton(el(f).querySelector('app-corregir-correo') as HTMLElement, 'Guardar y reenviar');
      expect(guardar.disabled).toBe(true);

      pendiente.flush({ ...LISTA[1], estado: 'INACTIVO' });
      f.detectChanges();
      expect(guardar.disabled).toBe(false);
    });

    it('unConflictoDelServidorSeMuestraEnLaPantalla', () => {
      const alerta = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
      const f = crear();
      tocar(f, fila(f, 2), 'Desactivar');
      tocar(f, el(f), 'Sí, desactivar');

      http.expectOne('/api/usuarios/2').flush(
        { detail: 'La comuna se quedaría sin administradores activos.', codigo: 'CONFLICTO' },
        { status: 409, statusText: 'Conflict' },
      );
      f.detectChanges();

      const aviso = el(f).querySelector('[role="alert"]');
      expect(aviso?.textContent).toContain('La comuna se quedaría sin administradores activos.');
      expect(fila(f, 2).textContent).toContain('Activo');
      expect(alerta).not.toHaveBeenCalled();
    });
  });

  describe('fila propia', () => {
    it('elRolYElEstadoDelPropioAdministradorEstanDeshabilitadosConUnaExplicacion', () => {
      const f = crear();
      const propia = fila(f, 1);

      expect((propia.querySelector('#rol-1') as HTMLSelectElement).disabled).toBe(true);
      expect((propia.querySelector('#estado-1') as HTMLButtonElement).disabled).toBe(true);
      expect(propia.textContent).toContain('(vos)');
      expect(propia.textContent).toContain('Es tu usuario: no podés cambiar tu rol ni desactivarte.');
      expect(propia.querySelector('#rol-1')?.getAttribute('aria-describedby')).toBe('nota-propia');
      // Los demás no quedan bloqueados.
      expect((el(f).querySelector('#rol-2') as HTMLSelectElement).disabled).toBe(false);
    });
  });

  describe('pendientes', () => {
    it('reenviarLaInvitacionAvisaElResultado', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Reenviar invitación');

      const pedido = http.expectOne('/api/usuarios/4/reenviar-invitacion');
      expect(pedido.request.method).toBe('POST');
      pedido.flush(null, { status: 204, statusText: 'No Content' });
      f.detectChanges();

      expect(el(f).querySelector('[role="status"]')?.textContent).toContain('Invitación enviada a julieta.romero@salsipuedes.gob.ar');
    });

    it('siElCorreoNoSaleAlReenviarLoMuestra', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Reenviar invitación');

      http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(
        { detail: 'No pudimos enviar el correo. Probá de nuevo en unos minutos.', codigo: 'CORREO_NO_ENVIADO' },
        { status: 502, statusText: 'Bad Gateway' },
      );
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No pudimos enviar el correo');
    });

    it('corregirElCorreoLoGuardaYDespuesReenviaLaInvitacion', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Corregir el correo');

      const campo = el(f).querySelector('#correo-corregido-4') as HTMLInputElement;
      expect(campo.value).toBe('julieta.romero@salsipuedes.gob.ar');
      escribir(f, '#correo-corregido-4', ' Julieta.Romero@Gmail.com ');
      enviar(f, campo.closest('form')!);

      const cambio = http.expectOne('/api/usuarios/4');
      expect(cambio.request.method).toBe('PATCH');
      expect(cambio.request.body).toEqual({ correo: 'julieta.romero@gmail.com' });
      cambio.flush({ ...LISTA[3], correo: 'julieta.romero@gmail.com' });
      http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
      f.detectChanges();

      expect(fila(f, 4).textContent).toContain('julieta.romero@gmail.com');
      expect(el(f).querySelector('#correo-corregido-4')).toBeNull();
      expect(texto(f)).toContain('Corregimos el correo y enviamos la invitación a julieta.romero@gmail.com');
    });

    it('siElCorreoSeCorrigioPeroLaInvitacionNoSalioArmaBienLasOraciones', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Corregir el correo');
      escribir(f, '#correo-corregido-4', 'julieta.romero@gmail.com');
      enviar(f, el(f).querySelector('#correo-corregido-4')!.closest('form')!);

      http.expectOne('/api/usuarios/4').flush({ ...LISTA[3], correo: 'julieta.romero@gmail.com' });
      // El detail llega sin punto final: igual tiene que quedar "... minutos. Probá ...".
      http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(
        { detail: 'No pudimos enviar el correo. Probá de nuevo en unos minutos', codigo: 'CORREO_NO_ENVIADO' },
        { status: 502, statusText: 'Bad Gateway' },
      );
      f.detectChanges();

      const aviso = el(f).querySelector('[role="alert"]')?.textContent?.replace(/\s+/g, ' ').trim();
      expect(aviso).toBe(
        'Guardamos el correo nuevo (julieta.romero@gmail.com), pero la invitación no salió. ' +
          'No pudimos enviar el correo. Probá de nuevo en unos minutos. Probá con "Reenviar invitación".',
      );
      expect(fila(f, 4).textContent).toContain('julieta.romero@gmail.com');
    });

    it('siElCorreoYaLoUsaOtraPersonaElFormularioSigueAbiertoConElMotivo', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Corregir el correo');
      escribir(f, '#correo-corregido-4', 'lucia.paz@salsipuedes.gob.ar');
      enviar(f, el(f).querySelector('#correo-corregido-4')!.closest('form')!);

      http.expectOne('/api/usuarios/4').flush(
        { detail: 'Ya hay un usuario con ese correo en la comuna.', codigo: 'CONFLICTO' },
        { status: 409, statusText: 'Conflict' },
      );
      http.expectNone('/api/usuarios/4/reenviar-invitacion');
      f.detectChanges();

      expect(el(f).querySelector('#correo-corregido-4')).not.toBeNull();
      expect(texto(f)).toContain('Ya hay un usuario con ese correo en la comuna.');
      expect(fila(f, 4).textContent).toContain('julieta.romero@salsipuedes.gob.ar');
    });

    it('elMismoCorreoNoSeMandaDeNuevo', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Corregir el correo');
      enviar(f, el(f).querySelector('#correo-corregido-4')!.closest('form')!);

      http.expectNone('/api/usuarios/4');
      expect(texto(f)).toContain('Es el mismo correo de antes');
    });
  });

  describe('nuevo usuario', () => {
    function abrirAlta(f: ComponentFixture<Usuarios>) {
      tocar(f, el(f), 'Nuevo usuario');
      return el(f).querySelector('app-nuevo-usuario form') as HTMLFormElement;
    }
    function completar(f: ComponentFixture<Usuarios>, rol = 'AGENTE') {
      escribir(f, '#nuevo-nombre', '  Bartolomé Fitzgerald ');
      escribir(f, '#nuevo-correo', 'Bartolome.Fitzgerald@Salsipuedes.gob.ar');
      const radio = el(f).querySelector(`input[type="radio"][value="${rol}"]`) as HTMLInputElement;
      radio.checked = true;
      radio.dispatchEvent(new Event('change'));
      f.detectChanges();
    }
    const nuevo: UsuarioComunaDto = {
      id: 7, nombre: 'Bartolomé Fitzgerald', correo: 'bartolome.fitzgerald@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'PENDIENTE', creadoEn,
    };

    it('soloOfreceRolesDelPersonalYNoEnviaSiFaltanDatos', () => {
      const f = crear();
      const form = abrirAlta(f);

      const roles = Array.from(form.querySelectorAll<HTMLInputElement>('input[type="radio"]')).map((r) => r.value);
      expect(roles).toEqual(['ADMINISTRATIVO', 'AGENTE', 'ADMIN_COMUNA']);
      expect(form.textContent).toContain('Personal administrativo');
      expect(form.textContent).not.toContain('Vecino');

      enviar(f, form);
      http.expectNone((r) => r.method === 'POST');
      expect(texto(f)).toContain('Escribí el nombre y apellido.');
      expect(texto(f)).toContain('Escribí el correo.');
      expect(texto(f)).toContain('Elegí qué va a hacer.');
    });

    it('conInvitacionEnviadaLoAvisaYElUsuarioApareceEnLaLista', () => {
      const f = crear();
      const form = abrirAlta(f);
      completar(f);
      enviar(f, form);

      const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/usuarios');
      expect(pedido.request.body).toEqual({ nombre: 'Bartolomé Fitzgerald', correo: 'bartolome.fitzgerald@salsipuedes.gob.ar', rol: 'AGENTE' });
      pedido.flush({ usuario: nuevo, invitacionEnviada: true }, { status: 201, statusText: 'Created' });
      f.detectChanges();

      expect(el(f).querySelector('app-nuevo-usuario [role="status"]')?.textContent).toContain(
        'Invitación enviada a bartolome.fitzgerald@salsipuedes.gob.ar',
      );
      expect(filas(f)).toContain(7);
      expect(fila(f, 7).textContent).toContain('Pendiente');

      tocar(f, el(f).querySelector('app-nuevo-usuario')!, 'Listo');
      expect(el(f).querySelector('app-nuevo-usuario')).toBeNull();
      expect(() => boton(el(f), 'Nuevo usuario')).not.toThrow();
    });

    it('siElCorreoNoSalioLoAvisaYPermiteReenviar', () => {
      const f = crear();
      const form = abrirAlta(f);
      completar(f, 'ADMINISTRATIVO');
      enviar(f, form);

      http.expectOne((r) => r.method === 'POST' && r.url === '/api/usuarios').flush(
        { usuario: { ...nuevo, rol: 'ADMINISTRATIVO' }, invitacionEnviada: false },
        { status: 201, statusText: 'Created' },
      );
      f.detectChanges();

      const alta = el(f).querySelector('app-nuevo-usuario') as HTMLElement;
      expect(alta.textContent).toContain('Creamos el usuario, pero el correo no salió');
      expect(alta.textContent).toContain('bartolome.fitzgerald@salsipuedes.gob.ar');
      expect(filas(f)).toContain(7);

      tocar(f, alta, 'Reintentar el envío');
      http.expectOne('/api/usuarios/7/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
      f.detectChanges();

      expect(alta.textContent).toContain('Invitación enviada a bartolome.fitzgerald@salsipuedes.gob.ar');
    });

    it('siElCorreoNoSalioSePuedeCorregirDesdeElAviso', () => {
      const f = crear();
      const form = abrirAlta(f);
      completar(f);
      enviar(f, form);
      http.expectOne((r) => r.method === 'POST' && r.url === '/api/usuarios').flush(
        { usuario: nuevo, invitacionEnviada: false },
        { status: 201, statusText: 'Created' },
      );
      f.detectChanges();

      const alta = el(f).querySelector('app-nuevo-usuario') as HTMLElement;
      tocar(f, alta, 'Corregir el correo');
      escribir(f, '#nuevo-correo-corregido', 'bfitzgerald@salsipuedes.gob.ar');
      enviar(f, alta.querySelector('app-corregir-correo form') as HTMLFormElement);

      const cambio = http.expectOne('/api/usuarios/7');
      expect(cambio.request.body).toEqual({ correo: 'bfitzgerald@salsipuedes.gob.ar' });
      cambio.flush({ ...nuevo, correo: 'bfitzgerald@salsipuedes.gob.ar' });
      http.expectOne('/api/usuarios/7/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
      f.detectChanges();

      expect(alta.textContent).toContain('Invitación enviada a bfitzgerald@salsipuedes.gob.ar');
      expect(fila(f, 7).textContent).toContain('bfitzgerald@salsipuedes.gob.ar');
    });

    it('unCorreoRepetidoSeMuestraEnElFormulario', () => {
      const f = crear();
      const form = abrirAlta(f);
      completar(f);
      enviar(f, form);

      http.expectOne((r) => r.method === 'POST' && r.url === '/api/usuarios').flush(
        { detail: 'Ya hay un usuario con ese correo en la comuna.', codigo: 'CONFLICTO' },
        { status: 409, statusText: 'Conflict' },
      );
      f.detectChanges();

      const alta = el(f).querySelector('app-nuevo-usuario') as HTMLElement;
      expect(alta.querySelector('[role="alert"]')?.textContent).toContain('Ya hay un usuario con ese correo en la comuna.');
      expect(alta.querySelector('form')).not.toBeNull();
      expect(filas(f)).not.toContain(7);
    });
  });
});
