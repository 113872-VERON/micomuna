import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ReclamoAgenteDto } from '../../../agente/modelos-agente';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { SinUbicacion, UbicacionService } from '../../../reclamos/ubicacion.service';
import { MisReclamos } from './mis-reclamos';

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };
const AQUI = { latitud: -31.4201, longitud: -64.1888 };

const reclamo = (numero: number, extra: Partial<ReclamoAgenteDto> = {}): ReclamoAgenteDto => ({
  numero,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'ASIGNADO',
  direccion: 'Av. San Martín 120',
  referencia: null,
  latitud: -31.4201,
  longitud: -64.1888,
  creadoEn: '2026-11-03T14:05:00',
  estadoCambiadoEn: '2026-11-03T14:05:00',
  ...extra,
});

// 1 está a ~1,5 km, 2 a ~400 m y 3 no tiene coordenadas. Por fecha: 3, 2, 1. Por tipo: Bache (3, 1) y Luz (2).
// Por distancia: 2, 1, 3.
const PARA_HACER = [
  reclamo(1, { latitud: -31.4066, creadoEn: '2026-11-01T10:00:00' }),
  reclamo(2, { tipo: 'LUZ', tipoNombre: 'Luz quemada', latitud: -31.4165, creadoEn: '2026-10-20T10:00:00' }),
  reclamo(3, { latitud: null, longitud: null, creadoEn: '2026-10-01T10:00:00', estado: 'EN_REPARACION' }),
];

describe('MisReclamos', () => {
  let http: HttpTestingController;
  let obtener: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    obtener = vi.fn().mockResolvedValue(AQUI);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: UbicacionService, useValue: { obtener } }],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 5, nombre: 'Juan Agente', correo: 'juan@correo.com', rol: 'AGENTE', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const boton = (f: ComponentFixture<unknown>, nombre: string) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(nombre)) as HTMLButtonElement;
  const numeros = (f: ComponentFixture<unknown>) => Array.from(el(f).querySelectorAll('.tarjeta .n')).map((n) => n.textContent?.replace('Reclamo n.º', '').trim());
  const lista = () => http.expectOne((p) => p.url === '/api/agente/reclamos');

  async function esperar(f: ComponentFixture<unknown>): Promise<void> {
    await new Promise((r) => setTimeout(r));
    f.detectChanges();
  }

  async function crear(reclamos: ReclamoAgenteDto[] = PARA_HACER): Promise<ComponentFixture<MisReclamos>> {
    const f = TestBed.createComponent(MisReclamos);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', COMUNA);
    f.detectChanges();
    lista().flush({ reclamos, pagina: 0, tamanio: 100, total: reclamos.length });
    await esperar(f);
    return f;
  }

  async function elegirOrden(f: ComponentFixture<unknown>, nombre: string): Promise<void> {
    boton(f, 'Ordenar por').click();
    f.detectChanges();
    const radio = Array.from(el(f).querySelectorAll('label.opcion')).find((l) => l.querySelector('b')?.textContent === nombre)?.querySelector('input') as HTMLInputElement;
    radio.click();
    await esperar(f);
  }

  describe('pestañas', () => {
    it('empiezaEnParaHacerYPideEseGrupo', async () => {
      const f = await crear();

      expect(el(f).querySelector('#pestana-para-hacer')?.getAttribute('aria-selected')).toBe('true');
      expect(el(f).querySelector('#pestana-resueltos')?.getAttribute('aria-selected')).toBe('false');
      expect(numeros(f).length).toBe(3);
    });

    it('alAbrirPideElGrupoParaHacerSinMandarLaUbicacion', async () => {
      const f = TestBed.createComponent(MisReclamos);
      f.componentRef.setInput('slug', 'salsipuedes-cordoba');
      f.componentRef.setInput('comuna', COMUNA);
      f.detectChanges();

      const p = lista();
      expect(p.request.params.get('grupo')).toBe('PARA_HACER');
      expect(p.request.params.keys().sort()).toEqual(['grupo', 'pagina', 'tamanio']);
      p.flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
      await esperar(f);
    });

    it('resueltosPideSuGrupoYNoMuestraOrdenNiDistancias', async () => {
      const f = await crear();

      boton(f, 'Resueltos').click();
      f.detectChanges();
      const p = lista();
      expect(p.request.params.get('grupo')).toBe('RESUELTOS');
      p.flush({ reclamos: [reclamo(7, { estado: 'RESUELTO', estadoCambiadoEn: '2026-11-10T09:05:00' })], pagina: 0, tamanio: 100, total: 1 });
      await esperar(f);

      expect(el(f).querySelector('#pestana-resueltos')?.getAttribute('aria-selected')).toBe('true');
      expect(numeros(f)).toEqual(['7']);
      expect(texto(f)).toContain('Resuelto el 10/11/2026');
      expect(boton(f, 'Ordenar por')).toBeUndefined();
      expect(el(f).querySelector('.distancia')).toBeNull();
    });

    it('lasFlechasDelTecladoCambianDePestana', async () => {
      const f = await crear();

      el(f).querySelector('[role="tablist"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      f.detectChanges();

      expect(el(f).querySelector('#pestana-resueltos')?.getAttribute('aria-selected')).toBe('true');
      lista().flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
      await esperar(f);
    });

    it('sinReclamosLoDiceEnCadaPestana', async () => {
      const f = await crear([]);
      expect(texto(f)).toContain('No tenés reclamos para hacer');

      boton(f, 'Resueltos').click();
      f.detectChanges();
      lista().flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
      await esperar(f);

      expect(texto(f)).toContain('Todavía no resolviste ningún reclamo');
    });
  });

  describe('tarjetas', () => {
    it('muestranNumeroEstadoTipoDireccionYDistancia', async () => {
      const f = await crear();

      const tarjetas = Array.from(el(f).querySelectorAll('.tarjeta'));
      // Por defecto, por fecha: la 3 (la más antigua) es la primera.
      expect(tarjetas[0].textContent).toContain('Reclamo n.º 3');
      expect(tarjetas[0].querySelector('app-chip-estado')?.textContent?.trim()).toBe('En reparación');
      expect(tarjetas[0].querySelector('app-chip-estado svg')).not.toBeNull();
      expect(tarjetas[1].textContent).toContain('Luz quemada');
      expect(tarjetas[1].textContent).toContain('Av. San Martín 120');
      expect(tarjetas[1].querySelector('.distancia')?.textContent?.trim()).toMatch(/^A (3|4)\d0 m$/);
      expect(tarjetas[2].querySelector('.distancia')?.textContent?.trim()).toMatch(/^A 1,\d km$/);
      // Sin coordenadas, sin distancia.
      expect(tarjetas[0].querySelector('.distancia')).toBeNull();
      expect(tarjetas[2].getAttribute('href')).toBe('/salsipuedes-cordoba/agente/reclamos/1');
    });

    it('unaDireccionVaciaDiceSinDireccion', async () => {
      const f = await crear([reclamo(1, { direccion: null })]);

      expect(texto(f)).toContain('Sin dirección');
    });

    it('losTextosSeMuestranComoTextoPlano', async () => {
      const f = await crear([reclamo(1, { direccion: '<img src=x onerror=alert(1)> Calle' })]);

      expect(el(f).querySelector('.tarjeta img')).toBeNull();
      expect(texto(f)).toContain('<img src=x onerror=alert(1)> Calle');
    });

    it('siFallaLaCargaOfreceReintentar', async () => {
      const f = TestBed.createComponent(MisReclamos);
      f.componentRef.setInput('slug', 'salsipuedes-cordoba');
      f.componentRef.setInput('comuna', COMUNA);
      f.detectChanges();
      lista().flush(null, { status: 500, statusText: 'Error' });
      await esperar(f);

      expect(texto(f)).toContain('No pudimos cargar tus reclamos');
      boton(f, 'Reintentar').click();
      f.detectChanges();
      lista().flush({ reclamos: [reclamo(1)], pagina: 0, tamanio: 100, total: 1 });
      await esperar(f);

      expect(numeros(f)).toEqual(['1']);
    });
  });

  describe('orden', () => {
    it('porDefectoVaPorFechaElMasAntiguoPrimero', async () => {
      const f = await crear();

      expect(numeros(f)).toEqual(['3', '2', '1']);
      expect(boton(f, 'Ordenar por').textContent).toContain('Fecha');
    });

    it('laHojaTraeCuatroOpcionesConSuExplicacion', async () => {
      const f = await crear();

      boton(f, 'Ordenar por').click();
      f.detectChanges();

      const opciones = Array.from(el(f).querySelectorAll('label.opcion')).map((l) => l.textContent?.replace(/\s+/g, ' ').trim());
      expect(opciones).toEqual([
        'Fecha Los más antiguos primero.',
        'Tipo Agrupados por tipo de problema, con un título por cada grupo.',
        'Distancia Los más cerca de donde estás primero, en línea recta.',
        'Prioridad Los de prioridad alta primero; a igual prioridad, el más cerca si tenemos tu ubicación y si no, el más antiguo. Sin prioridad al final.',
      ]);
    });

    it('porTipoAgrupaConUnTituloPorGrupo', async () => {
      const f = await crear();

      await elegirOrden(f, 'Tipo');

      expect(Array.from(el(f).querySelectorAll('h2.grupo')).map((h) => h.textContent?.trim())).toEqual(['Bache o calle rota', 'Luz quemada']);
      expect(numeros(f)).toEqual(['3', '1', '2']);
      expect(boton(f, 'Ordenar por').textContent).toContain('Tipo');
    });

    it('porPrioridadVaAltaPrimeroSinPrioridadAlFinalYAIgualPrioridadElMasCerca', async () => {
      const f = await crear([
        reclamo(1, { latitud: -31.4066, prioridad: { valor: 'ALTA', origen: 'IA' } }),
        reclamo(2, { latitud: -31.4165, prioridad: { valor: 'ALTA', origen: 'PERSONAL' } }),
        reclamo(3, { latitud: -31.4201, prioridad: { valor: 'BAJA', origen: 'PERSONAL' } }),
        reclamo(4, { prioridad: { valor: null, origen: 'NINGUNA' } }),
      ]);

      await elegirOrden(f, 'Prioridad');

      expect(numeros(f)).toEqual(['2', '1', '3', '4']);
      expect(boton(f, 'Ordenar por').textContent).toContain('Prioridad');
      expect(el(f).querySelector('h2.grupo')).toBeNull();
    });

    it('porPrioridadNoNecesitaLaUbicacionYSinEllaDesempataPorFecha', async () => {
      obtener.mockRejectedValue(new SinUbicacion('permiso'));
      const f = await crear([
        reclamo(1, { creadoEn: '2026-11-05T10:00:00', prioridad: { valor: 'MEDIA', origen: 'PERSONAL' } }),
        reclamo(2, { creadoEn: '2026-11-01T10:00:00', prioridad: { valor: 'MEDIA', origen: 'PERSONAL' } }),
        reclamo(3, { creadoEn: '2026-11-09T10:00:00', prioridad: { valor: 'ALTA', origen: 'IA' } }),
      ]);

      await elegirOrden(f, 'Prioridad');

      expect(numeros(f)).toEqual(['3', '2', '1']);
      expect(el(f).textContent).not.toContain('Sin tu ubicación se ordena por fecha');
    });

    it('cadaTarjetaMuestraLaPrioridadYLaMarcaDeLaIA', async () => {
      const f = await crear([
        reclamo(1, { prioridad: { valor: 'ALTA', origen: 'IA' } }),
        reclamo(2, { prioridad: { valor: 'BAJA', origen: 'PERSONAL' } }),
        reclamo(3),
      ]);

      const chips = Array.from(el(f).querySelectorAll('.tarjeta app-chip-prioridad'));
      expect(chips.map((c) => c.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Alta Sugerida, sugerida por la IA', 'Baja', 'Sin prioridad']);
    });

    it('porDistanciaVaElMasCercaPrimeroYLosSinCoordenadasAlFinal', async () => {
      const f = await crear();

      await elegirOrden(f, 'Distancia');

      expect(numeros(f)).toEqual(['2', '1', '3']);
      expect(el(f).querySelector('h2.grupo')).toBeNull();
    });

    it('laHojaSeCierraAlElegir', async () => {
      const f = await crear();

      await elegirOrden(f, 'Tipo');

      expect(el(f).querySelector('app-dialogo')).toBeNull();
    });

    it('tomaLaUbicacionUnaVezAlAbrirYOtraAlActualizar', async () => {
      const f = await crear();
      expect(obtener).toHaveBeenCalledTimes(1);

      boton(f, 'Actualizar').click();
      f.detectChanges();
      lista().flush({ reclamos: PARA_HACER, pagina: 0, tamanio: 100, total: 3 });
      await esperar(f);

      expect(obtener).toHaveBeenCalledTimes(2);
    });
  });

  describe('sin permiso de ubicación', () => {
    beforeEach(() => {
      obtener.mockRejectedValue(new SinUbicacion('permiso'));
    });

    it('avisaConPermitirUbicacionNoMuestraDistanciasYOrdenaPorFecha', async () => {
      const f = await crear();

      expect(texto(f)).toContain('no diste permiso de ubicación');
      expect(texto(f)).toContain('ordenamos por fecha, los más antiguos primero.');
      expect(boton(f, 'Permitir ubicación')).toBeDefined();
      expect(el(f).querySelector('.distancia')).toBeNull();
      expect(numeros(f)).toEqual(['3', '2', '1']);
      expect(boton(f, 'Ordenar por').textContent).toContain('Fecha');
    });

    it('laOpcionDistanciaQuedaApagadaYLoExplica', async () => {
      const f = await crear();

      boton(f, 'Ordenar por').click();
      f.detectChanges();

      const radio = Array.from(el(f).querySelectorAll('label.opcion')).find((l) => l.querySelector('b')?.textContent === 'Distancia');
      expect((radio?.querySelector('input') as HTMLInputElement).disabled).toBe(true);
      expect(radio?.textContent).toContain('Para usarla, permití la ubicación.');
    });

    it('sinGpsDelTelefonoTambienAvisaYOrdenaPorFecha', async () => {
      obtener.mockRejectedValue(new SinUbicacion('no-disponible'));
      const f = await crear();

      expect(texto(f)).toContain('No pudimos ver dónde estás');
      expect(texto(f)).toContain('ordenamos por fecha, los más antiguos primero.');
      expect(texto(f)).not.toContain('no diste permiso');
      expect(numeros(f)).toEqual(['3', '2', '1']);
    });

    it('alTocarPermitirUbicacionLaVuelveAPedirYApareceLaDistancia', async () => {
      const f = await crear();
      obtener.mockResolvedValue(AQUI);

      boton(f, 'Permitir ubicación').click();
      await esperar(f);

      expect(obtener).toHaveBeenCalledTimes(2);
      expect(el(f).querySelector('.distancia')).not.toBeNull();
      expect(texto(f)).not.toContain('no diste permiso');
    });

    it('enLaPestanaResueltosNoMolestaConElAviso', async () => {
      const f = await crear();

      boton(f, 'Resueltos').click();
      f.detectChanges();
      lista().flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
      await esperar(f);

      expect(texto(f)).not.toContain('Permitir ubicación');
    });
  });

  describe('muchos reclamos', () => {
    const abrir = () => {
      const f = TestBed.createComponent(MisReclamos);
      f.componentRef.setInput('slug', 'salsipuedes-cordoba');
      f.componentRef.setInput('comuna', COMUNA);
      f.detectChanges();
      return f;
    };

    it('paraHacerCargaTodasLasPaginasSolaYNoOfreceVerMas', async () => {
      const f = abrir();
      const p1 = lista();
      expect(p1.request.params.get('pagina')).toBe('0');
      p1.flush({ reclamos: Array.from({ length: 100 }, (_, i) => reclamo(i + 1)), pagina: 0, tamanio: 100, total: 201 });
      const p2 = lista();
      expect(p2.request.params.get('pagina')).toBe('1');
      expect(p2.request.params.get('grupo')).toBe('PARA_HACER');
      p2.flush({ reclamos: Array.from({ length: 100 }, (_, i) => reclamo(i + 101)), pagina: 1, tamanio: 100, total: 201 });
      const p3 = lista();
      expect(p3.request.params.get('pagina')).toBe('2');
      p3.flush({ reclamos: [reclamo(201)], pagina: 2, tamanio: 100, total: 201 });
      await esperar(f);

      expect(numeros(f).length).toBe(201);
      expect(boton(f, 'Ver más')).toBeUndefined();
    });

    it('mientrasCargaLasDemasPaginasNoMuestraLaListaIncompleta', async () => {
      const f = abrir();
      lista().flush({ reclamos: [reclamo(1)], pagina: 0, tamanio: 100, total: 101 });
      await esperar(f);

      expect(texto(f)).toContain('Cargando tus reclamos');
      expect(numeros(f).length).toBe(0);
      lista().flush({ reclamos: [reclamo(2)], pagina: 1, tamanio: 100, total: 101 });
      await esperar(f);
      expect(numeros(f).length).toBe(2);
    });

    it('siFallaUnaPaginaIntermediaMuestraElErrorConReintentar', async () => {
      const f = abrir();
      lista().flush({ reclamos: [reclamo(1)], pagina: 0, tamanio: 100, total: 101 });
      lista().flush(null, { status: 500, statusText: 'Error' });
      await esperar(f);

      expect(texto(f)).toContain('No pudimos cargar tus reclamos');
    });

    it('resueltosSigueConVerMasDeACien', async () => {
      const f = abrir();
      lista().flush({ reclamos: [], pagina: 0, tamanio: 100, total: 0 });
      await esperar(f);
      boton(f, 'Resueltos').click();
      f.detectChanges();
      const r1 = lista();
      expect(r1.request.params.get('grupo')).toBe('RESUELTOS');
      r1.flush({ reclamos: Array.from({ length: 100 }, (_, i) => reclamo(i + 1, { estado: 'RESUELTO' })), pagina: 0, tamanio: 100, total: 101 });
      await esperar(f);
      // Resueltos no se completa solo.
      http.expectNone((q) => q.url === '/api/agente/reclamos');

      boton(f, 'Ver más').click();
      const p = lista();
      expect(p.request.params.get('pagina')).toBe('1');
      p.flush({ reclamos: [reclamo(101, { estado: 'RESUELTO' })], pagina: 1, tamanio: 100, total: 101 });
      await esperar(f);

      expect(numeros(f).length).toBe(101);
      expect(boton(f, 'Ver más')).toBeUndefined();
    });
  });
});
