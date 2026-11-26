import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SIN_FILTROS } from '../reclamos/filtros-panel';
import { ReclamosPanelService } from './reclamos-panel.service';

describe('ReclamosPanelService', () => {
  let http: HttpTestingController;
  let servicio: ReclamosPanelService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ReclamosPanelService);
  });

  afterEach(() => http.verify());

  it('listaConLosFiltrosLaPaginaYElTamanio', () => {
    servicio.listar({ estado: 'ASIGNADO', tipo: 'BACHE', agente: 4, buscar: 'san' }, 2, 25).subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos');
    expect(p.request.method).toBe('GET');
    expect(p.request.params.get('estado')).toBe('ASIGNADO');
    expect(p.request.params.get('tipo')).toBe('BACHE');
    expect(p.request.params.get('agente')).toBe('4');
    expect(p.request.params.get('buscar')).toBe('san');
    expect(p.request.params.get('pagina')).toBe('2');
    expect(p.request.params.get('tamanio')).toBe('25');
    p.flush({ reclamos: [], pagina: 2, tamanio: 25, total: 0 });
  });

  it('sinFiltrosSoloViajanLaPaginaYElTamanio', () => {
    servicio.listar(SIN_FILTROS, 0, 25).subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos');
    expect(p.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
    p.flush({ reclamos: [], pagina: 0, tamanio: 25, total: 0 });
  });

  it('elMapaUsaLosMismosFiltrosSinPaginar', () => {
    servicio.mapa({ ...SIN_FILTROS, tipo: 'LUZ' }).subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos/mapa');
    expect(p.request.params.keys()).toEqual(['tipo']);
    p.flush({ puntos: [], recortado: false });
  });

  it('elResumenNoLlevaElFiltroDeEstado', () => {
    servicio.resumen({ estado: 'RESUELTO', tipo: 'LUZ', agente: null, buscar: '' }).subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos/resumen');
    expect(p.request.params.keys()).toEqual(['tipo']);
    p.flush({});
  });

  it('pideElDetalleLaFotoComoBlobYLosAgentes', () => {
    servicio.detalle(12).subscribe();
    http.expectOne('/api/panel/reclamos/12').flush({});
    servicio.foto(12, 5).subscribe();
    expect(http.expectOne('/api/panel/reclamos/12/fotos/5').request.responseType).toBe('blob');
    servicio.agentes().subscribe();
    http.expectOne('/api/panel/agentes').flush([]);
  });

  it('lasMetricasPidenLasFechasSoloSiVienenLasDos', () => {
    servicio.metricas().subscribe();
    const a = http.expectOne((r) => r.url === '/api/panel/metricas');
    expect(a.request.method).toBe('GET');
    expect(a.request.params.keys()).toEqual([]);
    a.flush({});

    servicio.metricas('2026-10-01').subscribe();
    const b = http.expectOne((r) => r.url === '/api/panel/metricas');
    expect(b.request.params.keys()).toEqual([]);
    b.flush({});

    servicio.metricas('2026-10-01', '2026-11-03').subscribe();
    const c = http.expectOne((r) => r.url === '/api/panel/metricas');
    expect(c.request.params.get('desde')).toBe('2026-10-01');
    expect(c.request.params.get('hasta')).toBe('2026-11-03');
    c.flush({});
  });

  it('cambiaElEstadoYDescartaConPostSinMandarLaComuna', () => {
    servicio.cambiarEstado({ numeros: [1, 2], estado: 'ASIGNADO', idAgente: 9 }).subscribe();
    const c = http.expectOne('/api/panel/reclamos/cambios-de-estado');
    expect(c.request.method).toBe('POST');
    expect(c.request.body).toEqual({ numeros: [1, 2], estado: 'ASIGNADO', idAgente: 9 });
    c.flush({ aplicados: [1, 2], omitidos: [] });

    servicio.descartar({ numeros: [3], motivo: 'OTRO', observacion: 'No corresponde' }).subscribe();
    const d = http.expectOne('/api/panel/reclamos/descartes');
    expect(d.request.method).toBe('POST');
    expect(d.request.body).toEqual({ numeros: [3], motivo: 'OTRO', observacion: 'No corresponde' });
    d.flush({ aplicados: [3], omitidos: [] });
  });
});

describe('ReclamosPanelService, prioridad (MC-27)', () => {
  let http: HttpTestingController;
  let servicio: ReclamosPanelService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ReclamosPanelService);
  });

  afterEach(() => http.verify());

  it('ordenarPorPrioridadMandaElParametroOrden', () => {
    servicio.listar(SIN_FILTROS, 0, 25, 'prioridad').subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos');
    expect(p.request.params.get('orden')).toBe('prioridad');
    expect(p.request.params.keys().sort()).toEqual(['orden', 'pagina', 'tamanio']);
    p.flush({ reclamos: [], pagina: 0, tamanio: 25, total: 0 });
  });

  it('elOrdenPorFechaNoMandaNada', () => {
    servicio.listar(SIN_FILTROS, 0, 25, 'fecha').subscribe();
    const p = http.expectOne((r) => r.url === '/api/panel/reclamos');
    expect(p.request.params.has('orden')).toBe(false);
    p.flush({ reclamos: [], pagina: 0, tamanio: 25, total: 0 });
  });

  it('fijarLaPrioridadHaceUnPutConLaPrioridadYDevuelveElDetalle', () => {
    let detalle: unknown;
    servicio.cambiarPrioridad(42, 'MEDIA').subscribe((d) => (detalle = d));
    const p = http.expectOne('/api/panel/reclamos/42/prioridad');
    expect(p.request.method).toBe('PUT');
    expect(p.request.body).toEqual({ prioridad: 'MEDIA' });
    p.flush({ numero: 42 });
    expect(detalle).toEqual({ numero: 42 });
  });
});
