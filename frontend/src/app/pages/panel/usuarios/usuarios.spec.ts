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
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
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
      expect(() => boton(fila(f, 4), 'Modificar el correo')).not.toThrow();
      expect(() => boton(fila(f, 3), 'Reenviar invitación')).toThrow();
    });

    it('siNoCargaLoExplicaYPermiteReintentar', () => {
      const f = TestBed.createComponent(Usuarios);
      f.componentRef.setInput('slug', 'salsipuedes-cordoba');
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

      tocar(f, fila(f, 4), 'Modificar el correo');
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

  describe('nombres accesibles', () => {
    const nombre = (b: HTMLButtonElement) => b.getAttribute('aria-label');

    it('losBotonesDeCadaFilaDicenAQuienAfectanSinCambiarElTextoVisible', () => {
      const f = crear();

      expect(nombre(boton(fila(f, 2), 'Desactivar'))).toBe('Desactivar a Marta Gómez');
      expect(nombre(boton(fila(f, 5), 'Activar'))).toBe('Activar a Raúl Ibarra');
      expect(nombre(boton(fila(f, 4), 'Reenviar invitación'))).toBe('Reenviar invitación a Julieta Romero');
      expect(nombre(boton(fila(f, 4), 'Modificar el correo'))).toBe('Modificar el correo de Julieta Romero');
      expect(nombre(boton(fila(f, 4), 'Desactivar'))).toBe('Desactivar a Julieta Romero');
      expect(nombre(boton(fila(f, 1), 'Desactivar'))).toBe('Desactivar a Sergio Vera');
      expect(el(f).querySelector('label[for="rol-3"]')?.textContent?.trim()).toBe('Rol de Lucía Paz');
    });

    it('dosFilasNoTienenBotonesConElMismoNombre', () => {
      const f = crear();
      const nombres = Array.from(el(f).querySelectorAll<HTMLButtonElement>('tr.fila button')).map((b) => b.getAttribute('aria-label'));

      expect(nombres.every((n) => !!n)).toBe(true);
      expect(new Set(nombres).size).toBe(nombres.length);
    });

    it('mientrasReenviaElNombreTambienLoDice', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Reenviar invitación');

      expect(nombre(boton(fila(f, 4), 'Enviando…'))).toBe('Enviando la invitación a Julieta Romero');
      http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('textos sin género', () => {
    it('desactivarNoDependeDeSiEsHombreOMujer', () => {
      const f = crear();
      tocar(f, fila(f, 2), 'Desactivar');
      expect(texto(f)).toContain('¿Desactivar a Marta Gómez? No va a poder ingresar hasta que vuelvas a activar su usuario. No se borra nada.');

      tocar(f, el(f), 'Sí, desactivar');
      http.expectOne('/api/usuarios/2').flush({ ...LISTA[1], estado: 'INACTIVO' });
      f.detectChanges();

      expect(el(f).querySelector('[role="status"]')?.textContent?.trim()).toBe(
        'Listo: desactivamos a Marta Gómez. No va a poder ingresar hasta que vuelvas a activar su usuario.',
      );
      expect(texto(f)).not.toMatch(/\blo actives\b|a sí mismo/);
    });

    it('yaNoHayNotaDebajoDeLaTabla', () => {
      const f = crear();
      expect(texto(f)).not.toContain('Los usuarios no se borran');
      expect(texto(f)).not.toContain('Nadie puede desactivar su propio usuario');
    });
  });

  describe('fila propia', () => {
    const motivo = 'Es tu usuario: no podés cambiar tu rol ni desactivarte.';
    const globito = (id: string) => document.getElementById(id ?? '');

    it('elMotivoEsLaAyudaDelBotonYDelSelectorYNoEstaEnLaGrilla', () => {
      const f = crear();
      const propia = fila(f, 1);
      const desactivar = propia.querySelector('#estado-1') as HTMLButtonElement;
      const selector = propia.querySelector('#rol-1') as HTMLSelectElement;

      expect(propia.textContent).toContain('(vos)');
      expect(propia.textContent).not.toContain('Es tu usuario');
      // Se ven apagados pero reciben foco y mouse (por eso no están "disabled").
      expect(desactivar.getAttribute('aria-disabled')).toBe('true');
      expect(desactivar.disabled).toBe(false);
      expect(selector.getAttribute('aria-disabled')).toBe('true');
      for (const control of [desactivar, selector]) {
        const ayuda = globito(control.getAttribute('aria-describedby')!)!;
        expect(ayuda.textContent).toBe(motivo);
        control.dispatchEvent(new Event('mouseenter'));
        expect(ayuda.style.visibility).toBe('visible');
        control.dispatchEvent(new Event('mouseleave'));
        expect(ayuda.style.visibility).toBe('hidden');
      }
      // Los demás no tienen ayuda ni quedan bloqueados.
      const otro = el(f).querySelector('#rol-2') as HTMLSelectElement;
      expect(otro.disabled).toBe(false);
      expect(otro.getAttribute('aria-disabled')).toBeNull();
      expect(otro.getAttribute('aria-describedby')).toBeNull();
    });

    it('nadaSePuedeCambiarEnLaFilaPropia', () => {
      const f = crear();
      const propia = fila(f, 1);
      (propia.querySelector('#estado-1') as HTMLButtonElement).click();
      f.detectChanges();
      expect(el(f).querySelector('tr.confirmar')).toBeNull();

      const selector = propia.querySelector('#rol-1') as HTMLSelectElement;
      selector.value = 'AGENTE';
      selector.dispatchEvent(new Event('change'));
      f.detectChanges();
      expect(el(f).querySelector('tr.confirmar')).toBeNull();
      expect(selector.value).toBe('ADMIN_COMUNA');
      http.expectNone((r) => r.method === 'PATCH');
    });
  });

  describe('botones de información de la tabla', () => {
    it('enLaVistaDeTarjetasLosDosBotonesTienenTextoVisibleYNombreAccesible', () => {
      const f = crear();
      const fila = el(f).querySelector('[data-ayudas-movil]') as HTMLElement;
      const botones = Array.from(fila.querySelectorAll('button'));

      expect(botones.map((b) => b.textContent?.replace('info', '').trim())).toEqual(['Estados', 'Reglas']);
      expect(botones.map((b) => b.getAttribute('aria-label'))).toEqual(['Qué significa cada estado', 'Reglas para desactivar usuarios']);
      const ayudas = botones.map((b) => document.getElementById(b.getAttribute('aria-describedby')!)?.textContent);
      expect(ayudas[0]).toContain('Pendiente: todavía no eligió su contraseña');
      expect(ayudas[1]).toContain('La comuna siempre tiene que tener al menos un administrador activo.');
      botones[0].dispatchEvent(new Event('focus'));
      expect(document.getElementById(botones[0].getAttribute('aria-describedby')!)?.style.visibility).toBe('visible');
    });

    const globitoDe = (f: ComponentFixture<unknown>, nombre: string) => {
      const boton = el(f).querySelector(`th button[aria-label="${nombre}"]`)!;
      expect(boton).not.toBeNull();
      return { boton, ayuda: document.getElementById(boton.getAttribute('aria-describedby')!)! };
    };

    it('estadoExplicaCadaEstadoYLaFilaYaNoRepiteLaFraseDeLaContrasena', () => {
      const f = crear();
      const { boton, ayuda } = globitoDe(f, 'Qué significa cada estado');

      expect(ayuda.textContent).toBe(
        'Activo: ya eligió su contraseña y puede ingresar.\n' +
          'Pendiente: todavía no eligió su contraseña; podés reenviarle la invitación.\n' +
          'Inactivo: está desactivado y no puede ingresar hasta que lo actives.',
      );
      boton.dispatchEvent(new Event('focus'));
      expect(ayuda.style.visibility).toBe('visible');
      expect(fila(f, 4).textContent).not.toContain('Todavía no eligió su contraseña.');
      expect(el(f).querySelector('tbody')?.textContent).not.toContain('Todavía no eligió');
    });

    it('accionesExplicaLasReglasParaDesactivar', () => {
      const f = crear();
      const { ayuda } = globitoDe(f, 'Reglas para desactivar usuarios');

      expect(ayuda.textContent).toBe(
        'Es tu usuario: no podés cambiar tu rol ni desactivarte.\n' +
          'Los usuarios no se borran: se desactivan.\n' +
          'La comuna siempre tiene que tener al menos un administrador activo.',
      );
    });
  });

  describe('nuevo usuario desde la lista', () => {
    it('elBotonNuevoUsuarioLlevaALaPantallaDeAlta', () => {
      const f = crear();
      const enlace = el(f).querySelector('#boton-nuevo-usuario') as HTMLAnchorElement;

      expect(enlace.textContent?.trim()).toBe('Nuevo usuario');
      expect(enlace.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/usuarios/nuevo');
      expect(el(f).querySelector('app-nuevo-usuario')).toBeNull();
      expect(el(f).querySelector('form')).toBeNull();
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
      tocar(f, fila(f, 4), 'Modificar el correo');

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
      expect(texto(f)).toContain('Modificamos el correo y enviamos la invitación a julieta.romero@gmail.com');
    });

    it('siElCorreoSeCorrigioPeroLaInvitacionNoSalioArmaBienLasOraciones', () => {
      const f = crear();
      tocar(f, fila(f, 4), 'Modificar el correo');
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
      tocar(f, fila(f, 4), 'Modificar el correo');
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
      tocar(f, fila(f, 4), 'Modificar el correo');
      enviar(f, el(f).querySelector('#correo-corregido-4')!.closest('form')!);

      http.expectNone('/api/usuarios/4');
      expect(texto(f)).toContain('Es el mismo correo de antes. Si está bien escrito, usá "Reenviar invitación".');
    });
  });

});
