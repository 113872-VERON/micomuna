import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { ReclamoPanelDetalleDto } from '../../../reclamos/modelos-panel';
import { DetallePanel } from './detalle-panel';

// Leaflet no se monta en las pruebas: este mapa falso tiene las mismas entradas y salidas.
@Component({ selector: 'app-mapa-ubicacion', template: '<div class="mapa-falso"></div>' })
class MapaFalso {
  readonly vista = input.required<VistaMapa>();
  readonly limite = input<unknown>(null);
  readonly interactivo = input(true);
  readonly altura = input('300px');
  readonly etiqueta = input('Mapa');
  readonly falla = output<void>();
}

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };

const reclamo = (extra: Partial<ReclamoPanelDetalleDto> = {}): ReclamoPanelDetalleDto => ({
  numero: 42,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'ASIGNADO',
  descripcion: 'Hay un pozo grande frente a la panadería.',
  referencia: 'Frente a la panadería',
  direccion: 'San Martín 450',
  latitud: -31.12,
  longitud: -64.3,
  creadoEn: '2026-11-03T09:12:00',
  vecinoNombre: 'Ana López',
  agente: { id: 4, nombre: 'Marcos Díaz' },
  fotos: [],
  historial: [
    { estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: 'Ana López', observacion: null },
    { estadoAnterior: 'RECIBIDO', estado: 'ASIGNADO', fecha: '2026-11-04T10:30:00', usuarioNombre: 'Marta Gil', observacion: 'Urgente' },
  ],
  descarte: null,
  trabajoRealizado: null,
  duplicados: [],
  sugerenciaIa: { estado: 'SIN_ANALIZAR', coincide: false },
  posiblesDuplicados: { candidatos: [] },
  ...extra,
});

const AGENTES = [{ id: 4, nombre: 'Marcos Díaz', enCurso: 3 }, { id: 9, nombre: 'Lucía Paz', enCurso: 0 }];

describe('DetallePanel', () => {
  let http: HttpTestingController;
  let liberadas: string[];

  beforeEach(() => {
    localStorage.clear();
    liberadas = [];
    let n = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:foto-${++n}`);
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((u) => void liberadas.push(u));
    TestBed.overrideComponent(DetallePanel, { remove: { imports: [MapaUbicacion] }, add: { imports: [MapaFalso] } });
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  // Al abrir pide los agentes (para la ventana de cambiar estado) y el reclamo.
  function crear(numero = '42', datos: ReclamoPanelDetalleDto | 'no-existe' | 'falla' = reclamo(), agentes: unknown[] | 'falla' = AGENTES): ComponentFixture<DetallePanel> {
    const f = TestBed.createComponent(DetallePanel);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', COMUNA);
    f.componentRef.setInput('numero', numero);
    f.detectChanges();
    const pedidoAgentes = http.expectOne('/api/panel/agentes');
    if (agentes === 'falla') {
      pedidoAgentes.flush(null, { status: 500, statusText: 'Error' });
    } else {
      pedidoAgentes.flush(agentes);
    }
    if (/^\d+$/.test(numero) && Number(numero) > 0) {
      const pedido = http.expectOne(`/api/panel/reclamos/${numero}`);
      if (datos === 'no-existe') {
        pedido.flush({ codigo: 'NO_ENCONTRADO' }, { status: 404, statusText: 'No encontrado' });
      } else if (datos === 'falla') {
        pedido.flush(null, { status: 500, statusText: 'Error' });
      } else {
        pedido.flush(datos);
      }
    }
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const seccion = (f: ComponentFixture<unknown>, titulo: string) =>
    Array.from(el(f).querySelectorAll('section')).find((s) => s.querySelector('h2')?.textContent?.trim() === titulo);
  const boton = (f: ComponentFixture<unknown>, nombre: string) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.trim() === nombre) as HTMLButtonElement;
  const fotos = () => http.match((p) => p.url.includes('/fotos/'));

  it('muestraLoQueReportoElVecinoYQuienLoHizo', () => {
    const f = crear();

    expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 42');
    expect(texto(f)).toContain('Bache o calle rota');
    expect(texto(f)).toContain('Hay un pozo grande frente a la panadería.');
    expect(texto(f)).toContain('Frente a la panadería');
    expect(texto(f)).toContain('San Martín 450');
    // Quién lo envió y cuándo ya está en el historial: no se repite arriba.
    expect(texto(f)).not.toContain('Reportado por');
    expect(texto(f)).toContain('Agente Marcos Díaz');
    expect(el(f).querySelector('.dato .rotulo')?.textContent).not.toContain('Fecha');
  });

  it('sinAgenteDiceSinAsignar', () => {
    const f = crear('42', reclamo({ agente: null }));

    expect(texto(f)).toContain('Agente Sin asignar');
  });

  it('elHistorialLlevaEstadoFechaYHoraQuienYObservacion', () => {
    const f = crear();

    const filas = Array.from(seccion(f, 'Historial')?.querySelectorAll('li') ?? []).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(filas).toEqual([
      'Recibido 3/11/2026 9:12 Lo envió Ana López',
      'Asignado 4/11/2026 10:30 Lo cambió Marta Gil Observación (interna) Urgente',
    ]);
  });

  it('unCambioDelSistemaDiceElSistema', () => {
    const f = crear('42', reclamo({ historial: [{ estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: null, observacion: null }] }));

    expect(texto(f)).toContain('Lo envió el sistema');
  });

  it('elHistorialMuestraLaObservacionDeLosDescartesComoTextoPlano', () => {
    const f = crear('42', reclamo({
      historial: [
        { estadoAnterior: 'RECIBIDO', estado: 'DESCARTADO', fecha: '2026-11-04T10:30:00', usuarioNombre: 'Marta Gil', observacion: 'Duplicado del n.º 1' },
        { estadoAnterior: 'RECIBIDO', estado: 'DESCARTADO', fecha: '2026-11-05T10:30:00', usuarioNombre: 'Marta Gil', observacion: 'No se puede atender: <b>está en un terreno privado</b>' },
      ],
    }));

    const obs = Array.from((f.nativeElement as HTMLElement).querySelectorAll('.h-obs')).map((e) => e.textContent?.replace(/\s+/g, ' ').trim());
    expect(obs).toEqual(['Motivo del descarte (interno) Duplicado del n.º 1', 'Motivo del descarte (interno) No se puede atender: <b>está en un terreno privado</b>']);
    expect((f.nativeElement as HTMLElement).querySelector('.h-obs b')).toBeNull();
  });

  it('laFotoPrincipalSeBajaPorElPanelConElTokenYSeMuestraGrande', () => {
    const f = crear('42', reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }, { id: 102, origen: 'VECINO', orden: 2 }] }));

    const pedidos = fotos();
    expect(pedidos.map((p) => p.request.url).sort()).toEqual(['/api/panel/reclamos/42/fotos/101', '/api/panel/reclamos/42/fotos/102']);
    expect(pedidos.every((p) => p.request.responseType === 'blob')).toBe(true);
    pedidos.forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
    f.detectChanges();

    expect(el(f).querySelectorAll('.fotos img').length).toBe(2);
    expect(el(f).querySelector('.fotos li.grande img')?.getAttribute('alt')).toBe('Foto 1 que subió el vecino');
  });

  it('elVecinoSinFotosLoDice', () => {
    const f = crear();

    expect(texto(f)).toContain('El vecino no subió fotos.');
  });

  it('lasFotosDelTrabajoSoloConElReclamoResuelto', () => {
    const f = crear('42', reclamo({ estado: 'RESUELTO', fotos: [{ id: 205, origen: 'TRABAJO', orden: 1 }], trabajoRealizado: 'Se tapó el pozo.' }));
    fotos().forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
    f.detectChanges();

    expect(seccion(f, 'Fotos del trabajo')?.querySelectorAll('img').length).toBe(1);
    expect(seccion(f, 'Lo que se hizo')?.textContent).toContain('Se tapó el pozo.');

    const g = crear('43', reclamo({ numero: 43, estado: 'EN_REPARACION', fotos: [{ id: 300, origen: 'TRABAJO', orden: 1 }] }));
    expect(seccion(g, 'Fotos del trabajo')).toBeUndefined();
    http.expectNone('/api/panel/reclamos/43/fotos/300');
  });

  it('seLiberanLasUrlsDeLasFotosAlSalir', () => {
    const f = crear('42', reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }] }));
    fotos().forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
    f.detectChanges();

    f.destroy();

    expect(liberadas).toEqual(['blob:foto-1']);
  });

  it('muestraUnMapaChicoDeSoloLecturaConLaUbicacion', () => {
    const f = crear();

    const mapa = f.debugElement.query(By.directive(MapaFalso)).componentInstance as MapaFalso;
    expect(mapa.interactivo()).toBe(false);
    expect(mapa.vista().centro).toEqual({ latitud: -31.12, longitud: -64.3 });
  });

  it('sinCoordenadasNoHayMapaPeroSigueLaDireccion', () => {
    const f = crear('42', reclamo({ latitud: null, longitud: null }));

    expect(f.debugElement.query(By.directive(MapaFalso))).toBeNull();
    expect(texto(f)).toContain('San Martín 450');
  });

  it('descartadoMuestraMotivoObservacionYElOriginalVinculado', () => {
    const f = crear(
      '42',
      reclamo({
        estado: 'DESCARTADO',
        descarte: { motivo: 'DUPLICADO', observacion: '<b>Es el mismo pozo</b>', numeroOriginal: 7 },
      }),
    );

    const caja = seccion(f, 'Descartado') as HTMLElement;
    expect(caja.textContent).toContain('Ya había un reclamo por el mismo problema');
    expect(caja.textContent).toContain('Duplicado del reclamo n.º 7');
    expect(caja.querySelector('a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/reclamos/7');
    expect(caja.textContent).toContain('<b>Es el mismo pozo</b>');
    expect(caja.querySelector('b')).toBeNull();
  });

  it('elOriginalListaSusDuplicadosVinculados', () => {
    const f = crear('42', reclamo({ duplicados: [43, 51] }));

    const bloque = seccion(f, 'Reclamos vinculados (2)') as HTMLElement;
    expect(Array.from(bloque.querySelectorAll('a')).map((a) => a.getAttribute('href'))).toEqual([
      '/salsipuedes-cordoba/panel/reclamos/43',
      '/salsipuedes-cordoba/panel/reclamos/51',
    ]);
  });

  it('sinDuplicadosNoApareceEsaSeccion', () => {
    const f = crear();

    expect(texto(f)).not.toContain('Reclamos vinculados');
  });

  it('tieneCambiarEstadoYDescartarYAbrenSusVentanasConEseReclamo', () => {
    const f = crear();

    boton(f, 'Cambiar estado').click();
    f.detectChanges();
    expect(el(f).querySelector('app-dialogo-cambiar-estado')?.textContent).toContain('Reclamo n.º 42 (ahora: Asignado)');
    (Array.from(el(f).querySelectorAll('app-dialogo-cambiar-estado button')).find((b) => b.textContent?.trim() === 'Cancelar') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el(f).querySelector('app-dialogo-cambiar-estado')).toBeNull();

    boton(f, 'Descartar').click();
    f.detectChanges();
    expect(el(f).querySelector('app-dialogo-descartar')?.textContent).toContain('Reclamo n.º 42');
  });

  describe('asignar a un agente', () => {
    const select = (f: ComponentFixture<unknown>) => el(f).querySelector('#agente-asignar') as HTMLSelectElement;
    const elegirAgente = (f: ComponentFixture<unknown>, id: string) => {
      select(f).value = id;
      select(f).dispatchEvent(new Event('change'));
      f.detectChanges();
    };
    const URL_AGENTE = '/api/panel/reclamos/42/agente';

    it('muestraElRecuadroConCadaAgenteYSuCargaYElBotonAsignarSiNoTieneAgente', () => {
      const f = crear('42', reclamo({ estado: 'RECIBIDO', agente: null, historial: [] }));

      expect(seccion(f, 'Asignar a un agente')).toBeDefined();
      expect(Array.from(select(f).options).map((o) => o.textContent?.trim())).toEqual(['Elegí un agente', 'Marcos Díaz, 3 en curso', 'Lucía Paz, 0 en curso']);
      expect(boton(f, 'Asignar')).toBeDefined();
      expect(boton(f, 'Reasignar')).toBeUndefined();
    });

    it('asignarMandaElAgenteYActualizaElDetalleConLaRespuesta', () => {
      const f = crear('42', reclamo({ estado: 'RECIBIDO', agente: null }));
      elegirAgente(f, '4');

      boton(f, 'Asignar').click();
      const pedido = http.expectOne(URL_AGENTE);
      expect(pedido.request.method).toBe('PUT');
      expect(pedido.request.body).toEqual({ idAgente: 4 });
      pedido.flush(reclamo({ estado: 'ASIGNADO', agente: { id: 4, nombre: 'Marcos Díaz' } }));
      // Cambió la carga de los agentes: se vuelve a pedir.
      http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 4 }]);
      f.detectChanges();

      expect(texto(f)).toContain('Quedó asignado a Marcos Díaz.');
      expect(texto(f)).toContain('Agente Marcos Díaz');
      expect(boton(f, 'Reasignar')).toBeDefined();
    });

    it('sinElegirAgenteNoEnviaYLoPide', () => {
      const f = crear('42', reclamo({ estado: 'RECIBIDO', agente: null }));

      boton(f, 'Asignar').click();
      f.detectChanges();

      expect(texto(f)).toContain('Elegí a qué agente se lo asignás.');
      http.expectNone(URL_AGENTE);
    });

    it('siYaTieneAgenteElBotonDiceReasignar', () => {
      const f = crear();

      expect(boton(f, 'Reasignar')).toBeDefined();
      elegirAgente(f, '9');
      boton(f, 'Reasignar').click();
      const pedido = http.expectOne(URL_AGENTE);
      expect(pedido.request.body).toEqual({ idAgente: 9 });
      pedido.flush(reclamo({ agente: { id: 9, nombre: 'Lucía Paz' } }));
      http.expectOne('/api/panel/agentes').flush([]);
    });

    const confirmacion = (f: ComponentFixture<unknown>) => el(f).querySelector('[role="alertdialog"]') as HTMLElement | null;
    const botonDe = (c: HTMLElement, nombre: string) => Array.from(c.querySelectorAll('button')).find((b) => b.textContent?.trim() === nombre) as HTMLButtonElement;

    it('reasignarUnReclamoEnReparacionPideConfirmarAntesDeEnviar', () => {
      const f = crear('42', reclamo({ estado: 'EN_REPARACION' }));
      elegirAgente(f, '9');

      boton(f, 'Reasignar').click();
      f.detectChanges();

      http.expectNone(URL_AGENTE);
      expect(confirmacion(f)?.textContent?.replace(/\s+/g, ' ').trim()).toContain('Marcos Díaz ya empezó a trabajar en este reclamo. Si lo reasignás vuelve a Asignado. ¿Reasignar igual?');
    });

    it('cancelarLaConfirmacionNoEnviaNada', () => {
      const f = crear('42', reclamo({ estado: 'EN_REPARACION' }));
      elegirAgente(f, '9');
      boton(f, 'Reasignar').click();
      f.detectChanges();

      botonDe(confirmacion(f)!, 'Cancelar').click();
      f.detectChanges();

      expect(confirmacion(f)).toBeNull();
      http.expectNone(URL_AGENTE);
    });

    it('confirmarLaReasignacionEnReparacionEnvia', () => {
      const f = crear('42', reclamo({ estado: 'EN_REPARACION' }));
      elegirAgente(f, '9');
      boton(f, 'Reasignar').click();
      f.detectChanges();

      botonDe(confirmacion(f)!, 'Reasignar').click();

      const pedido = http.expectOne(URL_AGENTE);
      expect(pedido.request.body).toEqual({ idAgente: 9 });
      pedido.flush(reclamo({ estado: 'ASIGNADO', agente: { id: 9, nombre: 'Lucía Paz' } }));
      http.expectOne('/api/panel/agentes').flush([]);
    });

    it('enOtrosEstadosReasignaSinPreguntar', () => {
      const f = crear();
      elegirAgente(f, '9');

      boton(f, 'Reasignar').click();

      expect(confirmacion(f)).toBeNull();
      http.expectOne(URL_AGENTE).flush(reclamo({ agente: { id: 9, nombre: 'Lucía Paz' } }));
      http.expectOne('/api/panel/agentes').flush([]);
    });

    it('elAgenteActualApareceMarcadoYDeshabilitado', () => {
      const f = crear();

      const opcion = Array.from(select(f).options).find((o) => o.value === '4')!;
      expect(opcion.textContent?.trim()).toBe('Marcos Díaz (actual), 3 en curso');
      expect(opcion.disabled).toBe(true);
      expect(Array.from(select(f).options).find((o) => o.value === '9')!.disabled).toBe(false);
    });

    it('siLaListaDeAgentesFallaLoDiceYSePuedeReintentar', () => {
      const f = crear('42', reclamo(), 'falla');

      expect(seccion(f, 'Asignar a un agente')?.textContent).toContain('No se pudo cargar la lista de agentes.');
      expect(seccion(f, 'Asignar a un agente')?.textContent).not.toContain('No hay agentes disponibles');
      expect(el(f).querySelector('#agente-asignar')).toBeNull();

      boton(f, 'Reintentar').click();
      http.expectOne('/api/panel/agentes').flush(AGENTES);
      f.detectChanges();

      expect(select(f)).not.toBeNull();
      expect(texto(f)).not.toContain('No se pudo cargar la lista de agentes.');
    });

    it('sinAgentesDiceComoAparecerYAlAdministrativoNoLoMandaAUsuarios', () => {
      const f = crear('42', reclamo(), []);

      const t = seccion(f, 'Asignar a un agente')?.textContent?.replace(/\s+/g, ' ');
      expect(t).toContain('No hay agentes disponibles. Para aparecer acá, el agente tiene que estar activo y haber aceptado la invitación.');
      expect(t).not.toContain('Usuarios');
      expect(el(f).querySelector('app-sin-agentes a')).toBeNull();
    });

    it('sinAgentesAlAdministradorDeLaComunaLeOfreceElEnlaceAUsuarios', () => {
      TestBed.inject(AuthService).guardarSesion({
        token: 'tk',
        usuario: { id: 2, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
      });
      const f = crear('42', reclamo(), []);

      expect(seccion(f, 'Asignar a un agente')?.textContent?.replace(/\s+/g, ' ')).toContain('Podés revisarlo en Usuarios.');
      expect(el(f).querySelector('app-sin-agentes a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/usuarios');
    });

    it('enResueltoYDescartadoElRecuadroNoSeMuestra', () => {
      for (const estado of ['RESUELTO', 'DESCARTADO'] as const) {
        const f = crear('42', reclamo({ estado }));
        expect(seccion(f, 'Asignar a un agente')).toBeUndefined();
        f.destroy();
      }
    });

    const casos: [number, string, string][] = [
      [409, 'YA_ASIGNADO', 'Ese reclamo ya está asignado a ese agente.'],
      [409, 'RECLAMO_CERRADO', 'El reclamo ya está cerrado: no se puede asignar.'],
      [409, 'CONFLICTO', 'Otra persona está cambiando este reclamo en este momento.'],
      [400, 'AGENTE_INVALIDO', 'Ese agente ya no está disponible.'],
    ];
    for (const [status, codigo, mensaje] of casos) {
      it('errorDelServidor_' + codigo + '_SeExplicaEnEspanol', () => {
        const f = crear();
        elegirAgente(f, '9');

        boton(f, 'Reasignar').click();
        http.expectOne(URL_AGENTE).flush({ codigo, detail: 'texto del servidor' }, { status, statusText: 'Error' });
        // Si algo cambió del otro lado, se vuelve a mirar el reclamo y los agentes.
        f.detectChanges();
        http.match('/api/panel/reclamos/42').forEach((p) => p.flush(reclamo()));
        http.match('/api/panel/agentes').forEach((p) => p.flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 3 }]));
        f.detectChanges();

        expect(texto(f)).toContain(mensaje);
        expect(el(f).querySelector('.caja-error')).not.toBeNull();
      });
    }
  });

  describe('el historial dice a quién se asignó', () => {
    const historial = (f: ComponentFixture<unknown>) =>
      Array.from(seccion(f, 'Historial')?.querySelectorAll('li') ?? []).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());

    it('asignadoAUnAgente', () => {
      const f = crear('42', reclamo({ historial: [{ estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: 'Ana López', observacion: null }, { estadoAnterior: 'RECIBIDO', estado: 'ASIGNADO', fecha: '2026-11-04T10:30:00', usuarioNombre: 'Marta Gil', observacion: null, agente: { id: 4, nombre: 'Marcos Díaz' }, agenteAnterior: null }] }));

      expect(historial(f)[1]).toBe('Asignado 4/11/2026 10:30 Asignado a Marcos Díaz Lo cambió Marta Gil');
    });

    it('reasignadoDeUnAgenteAOtro', () => {
      const f = crear('42', reclamo({ historial: [{ estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: 'Ana López', observacion: null }, { estadoAnterior: 'ASIGNADO', estado: 'ASIGNADO', fecha: '2026-11-05T08:00:00', usuarioNombre: 'Marta Gil', observacion: null, agente: { id: 4, nombre: 'Marcos Díaz' }, agenteAnterior: { id: 7, nombre: 'Ana Agente' } }] }));

      expect(historial(f)[1]).toBe('Asignado 5/11/2026 8:00 Reasignado de Ana Agente a Marcos Díaz Lo cambió Marta Gil');
    });

    it('sinAgenteNoAgregaNada', () => {
      const f = crear();

      expect(historial(f).join(' ')).not.toContain('Asignado a');
      expect(historial(f).join(' ')).not.toContain('Reasignado');
    });
  });

  it('descartarEstaApagadoSiElReclamoYaEstaCerrado', () => {
    const f = crear('42', reclamo({ estado: 'RESUELTO' }));

    expect(boton(f, 'Descartar').disabled).toBe(true);
    expect(boton(f, 'Cambiar estado').disabled).toBe(false);
  });

  it('descartarTambienEstaApagadoSiEstaEnReparacionYLoExplica', () => {
    const f = crear('42', reclamo({ estado: 'EN_REPARACION' }));

    expect(boton(f, 'Descartar').disabled).toBe(true);
    expect(boton(f, 'Descartar').title).toBe('No se puede descartar: el reclamo está en reparación');
    expect(boton(f, 'Cambiar estado').disabled).toBe(false);
  });

  it('despuesDeCambiarElEstadoSeRecargaElDetalleSinCerrarLaVentana', () => {
    const f = crear();
    boton(f, 'Cambiar estado').click();
    f.detectChanges();
    const ventana = el(f).querySelector('app-dialogo-cambiar-estado') as HTMLElement;
    (Array.from(ventana.querySelectorAll('label.opcion')).find((l) => l.textContent?.trim() === 'En reparación')?.querySelector('input') as HTMLInputElement).click();
    f.detectChanges();
    (Array.from(ventana.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Cambiar estado') as HTMLButtonElement).click();

    http.expectOne('/api/panel/reclamos/cambios-de-estado').flush({ aplicados: [42], omitidos: [] });
    f.detectChanges();
    // Cambió la carga de los agentes: se vuelve a pedir junto con el detalle.
    http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 4 }]);
    http.expectOne('/api/panel/reclamos/42').flush(reclamo({ estado: 'EN_REPARACION' }));
    f.detectChanges();

    expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 42');
    expect(el(f).querySelector('.titulo app-chip-estado')?.textContent).toContain('En reparación');
    expect(el(f).querySelector('app-dialogo-cambiar-estado')?.textContent).toContain('Cambió 1 reclamo: n.º 42');
  });

  it('unReclamoInexistenteODeOtraComunaDa404ConBotonParaVolver', () => {
    const f = crear('999', 'no-existe');

    expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
    const volver = Array.from(el(f).querySelectorAll('a.btn')).find((a) => a.textContent?.includes('Volver a los reclamos'));
    expect(volver?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/reclamos');
  });

  it('unNumeroQueNoEsNumeroNoPideNadaYDice404', () => {
    const f = crear('abc');

    http.expectNone((p) => p.url.startsWith('/api/panel/reclamos/'));
    expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
  });

  it('unErrorDeRedNoSeDisfrazaDe404YPermiteReintentar', () => {
    const f = crear('42', 'falla');
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar el reclamo');
    expect(texto(f)).not.toContain('No encontramos ese reclamo');

    boton(f, 'Reintentar').click();
    f.detectChanges();
    http.expectOne('/api/panel/reclamos/42').flush(reclamo());
    f.detectChanges();

    expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 42');
  });

  it('losTextosDelVecinoSeMuestranComoTextoPlano', () => {
    const f = crear('42', reclamo({ descripcion: '<img src=x onerror=alert(1)>pozo', referencia: '<script>alert(1)</script>', historial: [{ estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: '<b>Ana</b>', observacion: null }] }));

    expect(el(f).querySelector('.bloque img')).toBeNull();
    expect(el(f).querySelector('script')).toBeNull();
    expect(texto(f)).toContain('Lo envió <b>Ana</b>');
  });

  it('datosExtremosNoRompenLaPantalla', () => {
    const largo = 'Avenida Presidente Doctor Don Arturo Umberto Illia y Libertador General San Martín '.repeat(5);
    const f = crear('42', reclamo({ numero: 42, direccion: largo, descripcion: 'x'.repeat(500), historial: Array.from({ length: 40 }, (_, i) => ({ estadoAnterior: null, estado: 'ASIGNADO' as const, fecha: '2026-11-04T10:30:00', usuarioNombre: `Persona ${i}`, observacion: 'o'.repeat(300) })) }));

    expect(seccion(f, 'Historial')?.querySelectorAll('li').length).toBe(40);
  });

  it('unCambioDeTipoSeMuestraComoEventoPropioYUnaReasignacionNo', () => {
    const f = crear('42', reclamo({
      historial: [
        { estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: 'Ana López', observacion: null },
        { estadoAnterior: 'RECIBIDO', estado: 'RECIBIDO', fecha: '2026-11-04T10:30:00', usuarioNombre: 'Marta Gil', observacion: 'Cambió el tipo de Bache o calle rota a Luz quemada', agente: null, agenteAnterior: null },
        { estadoAnterior: 'ASIGNADO', estado: 'ASIGNADO', fecha: '2026-11-05T08:00:00', usuarioNombre: 'Marta Gil', observacion: null, agente: { id: 9, nombre: 'Lucía Paz' }, agenteAnterior: { id: 4, nombre: 'Marcos Díaz' } },
      ],
    }));

    const filas = Array.from(seccion(f, 'Historial')?.querySelectorAll('li') ?? []);
    expect(filas[1].textContent?.replace(/\s+/g, ' ').trim()).toBe('Cambio de tipo 4/11/2026 10:30 Lo cambió Marta Gil Cambió el tipo de Bache o calle rota a Luz quemada');
    expect(filas[1].querySelector('app-chip-estado')).toBeNull();
    expect(filas[1].textContent).not.toContain('easignado');
    expect(filas[2].textContent).toContain('Reasignado de Marcos Díaz a Lucía Paz');
    expect(filas[2].querySelector('app-chip-estado')).not.toBeNull();
  });

  describe('posibles duplicados', () => {
    const CANDIDATOS = [
      { numero: 7, estado: 'RECIBIDO' as const, distanciaMetros: 35, creadoEn: '2026-11-01T08:00:00', direccion: 'San Martín 460', descripcion: 'Pozo grande en la esquina' },
      { numero: 9, estado: 'EN_REPARACION' as const, distanciaMetros: 80, creadoEn: '2026-11-02T08:00:00', direccion: null, descripcion: '<b>Otro pozo</b>' },
    ];
    const conCandidatos = () => reclamo({ posiblesDuplicados: { candidatos: CANDIDATOS } });
    const tarjeta = (f: ComponentFixture<unknown>) => seccion(f, 'Posibles duplicados');
    const textoTarjeta = (f: ComponentFixture<unknown>) => tarjeta(f)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    const URL_NUEVO = '/api/panel/reclamos/42/no-es-duplicado';

    it('sinCandidatosNiMarcaNoMuestraLaTarjeta', () => {
      const f = crear();

      expect(tarjeta(f)).toBeUndefined();
    });

    it('conCandidatosAvisaYListaNumeroEstadoDistanciaFechaYDescripcion', () => {
      const f = crear('42', conCandidatos());

      const t = textoTarjeta(f);
      expect(t).toContain('Hay 2 reclamos parecidos cerca');
      const filas = Array.from(tarjeta(f)!.querySelectorAll('li'));
      expect(filas.length).toBe(2);
      expect(filas[0].querySelector('a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/reclamos/7');
      expect(filas[0].querySelector('app-chip-estado')?.textContent).toContain('Recibido');
      expect(filas[0].textContent).toContain('a 35 m');
      expect(filas[0].textContent).toContain('01/11/2026');
      expect(filas[0].textContent).toContain('Pozo grande en la esquina');
      // El texto del vecino va como texto plano.
      expect(filas[1].textContent).toContain('<b>Otro pozo</b>');
      expect(filas[1].querySelector('b')).toBeNull();
    });

    it('conUnSoloCandidatoUsaElSingular', () => {
      const f = crear('42', reclamo({ posiblesDuplicados: { candidatos: [CANDIDATOS[0]] } }));

      expect(textoTarjeta(f)).toContain('Hay 1 reclamo parecido cerca');
    });

    it('esDuplicadoDeEsteAbreElDescarteConDuplicadoYElOriginalCargados', () => {
      const f = crear('42', conCandidatos());

      (tarjeta(f)!.querySelectorAll('li')[0].querySelector('button') as HTMLButtonElement).click();
      f.detectChanges();

      const ventana = el(f).querySelector('app-dialogo-descartar') as HTMLElement;
      expect(ventana).not.toBeNull();
      expect((ventana.querySelector('input[type="radio"]:checked') as HTMLInputElement)?.parentElement?.textContent).toContain('Ya había un reclamo por el mismo problema');
      expect((ventana.querySelector('#original') as HTMLInputElement).value).toBe('7');
    });

    it('elBotonDescartarDeArribaAbreLaVentanaSinValoresCargados', () => {
      const f = crear('42', conCandidatos());
      (tarjeta(f)!.querySelectorAll('li')[0].querySelector('button') as HTMLButtonElement).click();
      f.detectChanges();
      (el(f).querySelector('app-dialogo-descartar button.sec') as HTMLButtonElement).click();
      f.detectChanges();

      boton(f, 'Descartar').click();
      f.detectChanges();

      expect(el(f).querySelector('app-dialogo-descartar input[type="radio"]:checked')).toBeNull();
      expect(el(f).querySelector('app-dialogo-descartar #original')).toBeNull();
    });

    it('conElReclamoEnReparacionNoSePuedeVincularYLoExplica', () => {
      const f = crear('42', reclamo({ estado: 'EN_REPARACION', posiblesDuplicados: { candidatos: CANDIDATOS } }));

      const botones = Array.from(tarjeta(f)!.querySelectorAll('li button')) as HTMLButtonElement[];
      expect(botones.every((b) => b.disabled)).toBe(true);
      expect(textoTarjeta(f)).toContain('Mientras está en reparación no se puede vincular como duplicado');
    });

    it('esUnReclamoNuevoPideConfirmarYNoLlamaAlPutConElPrimerClic', () => {
      const f = crear('42', conCandidatos());

      boton(f, 'Es un reclamo nuevo').click();
      f.detectChanges();

      http.expectNone(URL_NUEVO);
      expect(textoTarjeta(f)).toContain('¿Confirmás que es un reclamo nuevo? No se puede deshacer y este reclamo deja de mostrar el aviso.');
      expect(boton(f, 'Sí, es nuevo')).toBeTruthy();
      expect(Array.from(tarjeta(f)!.querySelectorAll('li button')).every((b) => (b as HTMLButtonElement).disabled)).toBe(true);
    });

    it('cancelarVuelveAComoEstabaSinLlamarAlPut', () => {
      const f = crear('42', conCandidatos());
      boton(f, 'Es un reclamo nuevo').click();
      f.detectChanges();

      boton(f, 'Cancelar').click();
      f.detectChanges();

      http.expectNone(URL_NUEVO);
      expect(boton(f, 'Es un reclamo nuevo').disabled).toBe(false);
      expect(textoTarjeta(f)).not.toContain('¿Confirmás');
      expect(Array.from(tarjeta(f)!.querySelectorAll('li button')).some((b) => (b as HTMLButtonElement).disabled)).toBe(false);
    });

    it('siEsNuevoMandaElPutSinCuerpoYRefrescaConLaRespuesta', () => {
      const f = crear('42', conCandidatos());

      boton(f, 'Es un reclamo nuevo').click();
      f.detectChanges();
      boton(f, 'Sí, es nuevo').click();
      f.detectChanges();
      const p = http.expectOne(URL_NUEVO);
      expect(p.request.method).toBe('PUT');
      expect(p.request.body).toBeNull();
      expect(boton(f, 'Guardando…').disabled).toBe(true);
      p.flush(reclamo({ posiblesDuplicados: { candidatos: [], marcadoNuevoPor: 'Marta Gil', marcadoNuevoEn: '2026-11-26T11:30:00' } }));
      f.detectChanges();

      expect(textoTarjeta(f)).toContain('Marcado como reclamo nuevo por Marta Gil el 26/11/2026.');
      expect(tarjeta(f)!.querySelectorAll('button').length).toBe(0);
      expect(tarjeta(f)!.querySelectorAll('li').length).toBe(0);
    });

    const casos: [string, string][] = [
      ['RECLAMO_CERRADO', 'El reclamo ya está cerrado'],
      ['YA_MARCADO', 'Este reclamo ya se había dejado como nuevo.'],
      ['SIN_CANDIDATOS', 'Ya no hay reclamos parecidos cerca'],
      ['CONFLICTO', 'Otra persona está cambiando este reclamo'],
    ];
    for (const [codigo, mensaje] of casos) {
      it('errorDelServidor_' + codigo + '_SeExplicaEnEspanolYSeVuelveAMirarElReclamo', () => {
        const f = crear('42', conCandidatos());

        boton(f, 'Es un reclamo nuevo').click();
        f.detectChanges();
        boton(f, 'Sí, es nuevo').click();
        http.expectOne(URL_NUEVO).flush({ codigo }, { status: 409, statusText: 'Conflicto' });
        f.detectChanges();
        http.expectOne('/api/panel/reclamos/42').flush(conCandidatos());
        f.detectChanges();

        expect(tarjeta(f)!.querySelector('[role="alert"]')?.textContent).toContain(mensaje);
        expect(boton(f, 'Es un reclamo nuevo').disabled).toBe(false);
      });
    }
  });

  describe('sugerencia de la IA', () => {
    const TIPOS = [
      { codigo: 'BACHE', nombre: 'Bache o calle rota', consejoFoto: '' },
      { codigo: 'LUZ', nombre: 'Luz quemada', consejoFoto: '' },
      { codigo: 'AGUA', nombre: 'Pérdida de agua', consejoFoto: '' },
    ];
    const sugiere = (extra: Partial<ReclamoPanelDetalleDto['sugerenciaIa']> = {}): ReclamoPanelDetalleDto['sugerenciaIa'] => ({
      estado: 'HECHA',
      tipoSugerido: 'LUZ',
      tipoSugeridoNombre: 'Luz quemada',
      justificacion: 'Se ve un farol apagado.',
      coincide: false,
      decision: null,
      ...extra,
    });
    const tarjeta = (f: ComponentFixture<unknown>) => seccion(f, 'Sugerencia de la IA')!;
    const textoTarjeta = (f: ComponentFixture<unknown>) => tarjeta(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
    const botonesDeTarjeta = (f: ComponentFixture<unknown>) => Array.from(tarjeta(f).querySelectorAll('button')).map((b) => b.textContent?.trim());
    const decidida = (tipo: string, nombre: string, decision: 'ACEPTADA' | 'CORREGIDA') =>
      sugiere({ decision, tipoDecidido: tipo, tipoDecididoNombre: nombre, decididaPor: 'Marta Gil', decididaEn: '2026-11-25T10:05:00' });

    it('siCoincideLoDiceYNoPideDecidir', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere({ coincide: true, tipoSugerido: 'BACHE', tipoSugeridoNombre: 'Bache o calle rota' }) }));

      expect(textoTarjeta(f)).toContain('La IA coincide con el tipo elegido: Bache o calle rota.');
      expect(textoTarjeta(f)).toContain('Se ve un farol apagado.');
      expect(textoTarjeta(f)).toContain('La IA solo sugiere: el tipo lo decide la comuna.');
      expect(botonesDeTarjeta(f)).toEqual(['Cambiar tipo']);
    });

    it('siNoCoincideOfreceAceptarMantenerYElegirOtro', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere() }));

      expect(textoTarjeta(f)).toContain('La IA sugiere: Luz quemada');
      expect(botonesDeTarjeta(f)).toEqual(['Aceptar Luz quemada', 'Mantener Bache o calle rota', 'Elegir otro tipo']);
    });

    it('aceptarMandaElTipoSugeridoYRefrescaTodaLaPantalla', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere() }));

      boton(f, 'Aceptar Luz quemada').click();
      f.detectChanges();
      const p = http.expectOne('/api/panel/reclamos/42/tipo');
      expect(p.request.method).toBe('PUT');
      expect(p.request.body).toEqual({ tipo: 'LUZ' });
      expect(boton(f, 'Guardando…').disabled).toBe(true);
      expect(boton(f, 'Mantener Bache o calle rota').disabled).toBe(true);
      p.flush(reclamo({ tipo: 'LUZ', tipoNombre: 'Luz quemada', sugerenciaIa: { ...decidida('LUZ', 'Luz quemada', 'ACEPTADA'), coincide: true } }));
      f.detectChanges();

      expect(seccion(f, 'Qué reportó el vecino')?.textContent).toContain('Luz quemada');
      expect(textoTarjeta(f)).toContain('Marta Gil aceptó la sugerencia de la IA el 25/11/2026 10:05: Luz quemada.');
      expect(botonesDeTarjeta(f)).toEqual(['Cambiar tipo']);
    });

    it('mantenerMandaElTipoActual', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere() }));

      boton(f, 'Mantener Bache o calle rota').click();
      const p = http.expectOne('/api/panel/reclamos/42/tipo');
      expect(p.request.body).toEqual({ tipo: 'BACHE' });
      p.flush(reclamo({ sugerenciaIa: decidida('BACHE', 'Bache o calle rota', 'CORREGIDA') }));
      f.detectChanges();

      expect(textoTarjeta(f)).toContain('La IA sugería Luz quemada; Marta Gil dejó Bache o calle rota el 25/11/2026 10:05.');
    });

    it('elegirOtroTipoCargaLosTiposExigeUnoYGuarda', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere() }));

      boton(f, 'Elegir otro tipo').click();
      f.detectChanges();
      http.expectOne('/api/tipos-reclamo').flush(TIPOS);
      f.detectChanges();
      boton(f, 'Guardar').click();
      f.detectChanges();
      http.expectNone('/api/panel/reclamos/42/tipo');
      expect(textoTarjeta(f)).toContain('Elegí un tipo.');

      const select = tarjeta(f).querySelector('select') as HTMLSelectElement;
      expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual(['Elegí un tipo', 'Bache o calle rota', 'Luz quemada', 'Pérdida de agua']);
      select.value = 'AGUA';
      select.dispatchEvent(new Event('change'));
      f.detectChanges();
      boton(f, 'Guardar').click();
      const p = http.expectOne('/api/panel/reclamos/42/tipo');
      expect(p.request.body).toEqual({ tipo: 'AGUA' });
      p.flush(reclamo({ tipo: 'AGUA', tipoNombre: 'Pérdida de agua', sugerenciaIa: decidida('AGUA', 'Pérdida de agua', 'CORREGIDA') }));
      f.detectChanges();

      expect(textoTarjeta(f)).toContain('La IA sugería Luz quemada; Marta Gil dejó Pérdida de agua el 25/11/2026 10:05.');
      expect(tarjeta(f).querySelector('select')).toBeNull();
    });

    it('conDecisionPermiteCambiarElTipoOtraVez', () => {
      const f = crear('42', reclamo({ sugerenciaIa: decidida('LUZ', 'Luz quemada', 'ACEPTADA') }));

      expect(botonesDeTarjeta(f)).toEqual(['Cambiar tipo']);
      boton(f, 'Cambiar tipo').click();
      f.detectChanges();
      http.expectOne('/api/tipos-reclamo').flush(TIPOS);
      f.detectChanges();

      expect(tarjeta(f).querySelector('select')).not.toBeNull();
    });

    it('sinAnalizarOfreceActualizarYCambiarTipo', () => {
      const f = crear('42');

      expect(textoTarjeta(f)).toContain('Todavía no hay sugerencia de la IA.');
      expect(botonesDeTarjeta(f)).toEqual(['Actualizar', 'Cambiar tipo']);
      boton(f, 'Actualizar').click();
      http.expectOne('/api/panel/reclamos/42').flush(reclamo({ sugerenciaIa: sugiere() }));
      f.detectChanges();

      expect(textoTarjeta(f)).toContain('La IA sugiere: Luz quemada');
    });

    it('noDisponibleLoDiceYDejaCambiarElTipo', () => {
      const f = crear('42', reclamo({ sugerenciaIa: { estado: 'NO_DISPONIBLE', coincide: false } }));

      expect(textoTarjeta(f)).toContain('La IA no pudo analizar este reclamo.');
      expect(botonesDeTarjeta(f)).toEqual(['Cambiar tipo']);
    });

    it('unConflictoSeExplicaYVuelveAMirarElReclamo', () => {
      const f = crear('42', reclamo({ sugerenciaIa: sugiere() }));

      boton(f, 'Aceptar Luz quemada').click();
      http.expectOne('/api/panel/reclamos/42/tipo').flush({ codigo: 'CONFLICTO' }, { status: 409, statusText: 'Conflicto' });
      f.detectChanges();
      http.expectOne('/api/panel/reclamos/42').flush(reclamo({ sugerenciaIa: sugiere() }));
      f.detectChanges();

      expect(tarjeta(f).querySelector('[role="alert"]')?.textContent).toContain('Otra persona está cambiando este reclamo. Probá de nuevo.');
      expect(boton(f, 'Aceptar Luz quemada').disabled).toBe(false);
    });

    it('conElReclamoCerradoSeVeLaSugerenciaSinAcciones', () => {
      for (const estado of ['RESUELTO', 'DESCARTADO'] as const) {
        const f = crear('42', reclamo({ estado, sugerenciaIa: sugiere() }));

        expect(textoTarjeta(f)).toContain('La IA sugiere: Luz quemada');
        expect(tarjeta(f).querySelectorAll('button').length).toBe(0);
        f.destroy();
      }
    });
  });
});
