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
  ...extra,
});

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
  function crear(numero = '42', datos: ReclamoPanelDetalleDto | 'no-existe' | 'falla' = reclamo()): ComponentFixture<DetallePanel> {
    const f = TestBed.createComponent(DetallePanel);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', COMUNA);
    f.componentRef.setInput('numero', numero);
    f.detectChanges();
    http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz' }]);
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
      'Asignado 4/11/2026 10:30 Lo cambió Marta Gil Observación Urgente',
    ]);
  });

  it('unCambioDelSistemaDiceElSistema', () => {
    const f = crear('42', reclamo({ historial: [{ estadoAnterior: null, estado: 'RECIBIDO', fecha: '2026-11-03T09:12:00', usuarioNombre: null, observacion: null }] }));

    expect(texto(f)).toContain('Lo envió el sistema');
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
});
