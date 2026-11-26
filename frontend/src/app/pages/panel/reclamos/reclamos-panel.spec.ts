import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { PuntoReclamoDto, ReclamoPanelDto, ResumenPanelDto } from '../../../reclamos/modelos-panel';
import { MapaReclamos } from './mapa-reclamos';
import { ALTO_MINIMO_MAPA, ReclamosPanel, altoDelMapa, textoDelAviso } from './reclamos-panel';

// Leaflet no se monta en las pruebas: este mapa falso tiene las mismas entradas y salidas.
@Component({ selector: 'app-mapa-reclamos', template: '<div class="mapa-falso"></div>' })
class MapaFalso {
  readonly puntos = input.required<readonly PuntoReclamoDto[]>();
  readonly limite = input<unknown>(null);
  readonly resaltado = input<number | null>(null);
  readonly altura = input('520px');
  readonly etiqueta = input('Mapa');
  readonly elegido = output<number>();
  readonly falla = output<void>();
}

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };
const LIMITE = { type: 'Polygon', coordinates: [[[-64.3, -31.1], [-64.2, -31.1], [-64.2, -31.0], [-64.3, -31.1]]] };
const BASE = '/salsipuedes-cordoba/panel/reclamos';
const RUTAS = [
  { path: ':slug/panel/reclamos', data: { comuna: COMUNA }, component: ReclamosPanel },
];

const fila = (numero: number, extra: Partial<ReclamoPanelDto> = {}): ReclamoPanelDto => ({
  numero,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'RECIBIDO',
  descripcion: 'Un pozo',
  direccion: 'San Martín 450',
  referencia: null,
  creadoEn: '2026-11-03T10:00:00',
  agente: null,
  numeroOriginal: null,
  cantidadDuplicados: 0,
  ...extra,
});

const RESUMEN: ResumenPanelDto = {
  porEstado: { RECIBIDO: 6, ASIGNADO: 5, EN_REPARACION: 8, RESUELTO: 5, DESCARTADO: 2 },
  total: 26,
  sinAsignar: 6,
  sinAsignarMasAntiguo: new Date(Date.now() - 5 * 86_400_000).toISOString(),
};

describe('Panel de reclamos', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.overrideComponent(ReclamosPanel, { remove: { imports: [MapaReclamos] }, add: { imports: [MapaFalso] } });
    TestBed.configureTestingModule({
      providers: [
        provideRouter(RUTAS, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' },
    });
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const el = () => harness.fixture.nativeElement as HTMLElement;
  const texto = () => el().textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const url = () => TestBed.inject(Router).url;
  // La comuna tiene el límite cargado (hay mapa). Hasta que llega, la pantalla no dibuja el mapa ni pide sus puntos.
  const limiteConMapa = () => {
    http.expectOne('/api/mi-comuna/limite').flush(LIMITE);
    harness.detectChanges();
  };
  const sinLimite = () => http.expectOne('/api/mi-comuna/limite').flush(null, { status: 204, statusText: 'No Content' });
  const lista = () => http.expectOne((p) => p.url === '/api/panel/reclamos');
  const mapa = () => http.expectOne((p) => p.url === '/api/panel/reclamos/mapa');
  const resumen = () => http.expectOne((p) => p.url === '/api/panel/reclamos/resumen');
  const pagina = (reclamos: ReclamoPanelDto[], total = reclamos.length) => ({ reclamos, pagina: 0, tamanio: 25, total });

  interface Datos {
    reclamos?: ReclamoPanelDto[];
    total?: number;
    resumen?: ResumenPanelDto;
    puntos?: PuntoReclamoDto[];
    recortado?: boolean;
    // Si la comuna tiene el límite cargado (por defecto, lo mismo que conMapa).
    conLimite?: boolean;
  }

  // Abre la pantalla y contesta lo que pide al empezar (apoyos, lista, resumen y, si hay mapa, los puntos).
  async function abrir(query = '', datos: Datos = {}, conMapa = true): Promise<void> {
    await harness.navigateByUrl(BASE + query);
    http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 3 }, { id: 9, nombre: 'Lucía Paz', enCurso: 0 }]);
    http.expectOne('/api/tipos-reclamo').flush([
      { codigo: 'BACHE', nombre: 'Bache o calle rota', consejoFoto: '' },
      { codigo: 'LUZ', nombre: 'Luz quemada', consejoFoto: '' },
    ]);
    if (datos.conLimite ?? conMapa) {
      limiteConMapa();
    } else {
      sinLimite();
    }
    resumen().flush(datos.resumen ?? RESUMEN);
    lista().flush(pagina(datos.reclamos ?? [fila(12)], datos.total));
    if (conMapa) {
      mapa().flush({ puntos: datos.puntos ?? [], recortado: datos.recortado ?? false });
    }
    harness.detectChanges();
  }

  async function esperar(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function cambiarSelect(id: string, valor: string): Promise<void> {
    const select = el().querySelector(`#${id}`) as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    await esperar();
  }

  const boton = (nombre: string) =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(nombre)) as HTMLButtonElement;
  const mapaFalso = () => harness.fixture.debugElement.query(By.directive(MapaFalso))?.componentInstance as MapaFalso | undefined;

  describe('alto del mapa en escritorio', () => {
    it('altoDelMapaEsLoQueQuedaDeVentanaMenosLoDeArribaYElMargen', () => {
      expect(altoDelMapa(900, 354)).toBe(530);
      expect(altoDelMapa(900, 0)).toBe(884);
    });

    it('altoDelMapaNuncaBajaDelMinimoDe480', () => {
      expect(altoDelMapa(700, 354)).toBe(ALTO_MINIMO_MAPA);
      expect(altoDelMapa(300, 900)).toBe(480);
    });

    it('elPanelPonePorEscritorioElAltoMedidoYEnAngostoNoLoToca', async () => {
      const original = window.matchMedia as typeof window.matchMedia | undefined;
      let escritorio = true;
      window.matchMedia = (() => ({ matches: escritorio })) as unknown as typeof window.matchMedia;
      try {
        await abrir();
        await esperar();
        window.dispatchEvent(new Event('resize'));
        await esperar();
        const seccion = el().querySelector<HTMLElement>('section.mapa')!;
        const arriba = el().querySelector('.cuerpo')!.getBoundingClientRect().top + window.scrollY;
        expect(seccion.style.getPropertyValue('--alto-mapa')).toBe(`${altoDelMapa(window.innerHeight, arriba)}px`);

        escritorio = false;
        window.dispatchEvent(new Event('resize'));
        await esperar();
        expect(seccion.style.getPropertyValue('--alto-mapa')).toBe('');
      } finally {
        window.matchMedia = original as typeof window.matchMedia;
      }
    });
  });

  describe('al abrir', () => {
    it('pideLaListaElMapaYElResumenSinFiltrosYMuestraListaYMapa', async () => {
      await harness.navigateByUrl(BASE);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      limiteConMapa();

      const l = lista();
      expect(l.request.params.keys().sort()).toEqual(['pagina', 'tamanio']);
      expect(l.request.params.get('pagina')).toBe('0');
      expect(l.request.params.get('tamanio')).toBe('25');
      expect(mapa().request.params.keys()).toEqual([]);
      expect(resumen().request.params.keys()).toEqual([]);
      l.flush(pagina([]));
    });

    it('losFiltrosDeLaUrlSeAplicanALaListaYAlMapaYElResumenNoLlevaElEstado', async () => {
      await harness.navigateByUrl(`${BASE}?estado=ASIGNADO&tipo=LUZ&agente=4&buscar=san%20mart%C3%ADn`);
      http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 3 }]);
      http.expectOne('/api/tipos-reclamo').flush([{ codigo: 'LUZ', nombre: 'Luz quemada', consejoFoto: '' }]);
      limiteConMapa();

      const l = lista();
      for (const q of [l.request, mapa().request]) {
        expect(q.params.get('estado')).toBe('ASIGNADO');
        expect(q.params.get('tipo')).toBe('LUZ');
        expect(q.params.get('agente')).toBe('4');
        expect(q.params.get('buscar')).toBe('san martín');
      }
      const r = resumen();
      expect(r.request.params.has('estado')).toBe(false);
      expect(r.request.params.get('tipo')).toBe('LUZ');
      l.flush(pagina([]));
      r.flush(RESUMEN);
      http.match((p) => p.url === '/api/panel/reclamos/mapa').forEach((m) => m.flush({ puntos: [], recortado: false }));
      harness.detectChanges();

      expect((el().querySelector('#filtro-estado') as HTMLSelectElement).value).toBe('ASIGNADO');
      expect((el().querySelector('#filtro-tipo') as HTMLSelectElement).value).toBe('LUZ');
      expect((el().querySelector('#filtro-agente') as HTMLSelectElement).value).toBe('4');
      expect((el().querySelector('#filtro-buscar') as HTMLInputElement).value).toBe('san martín');
    });

    it('conVistaListaNoPideElMapa', async () => {
      await abrir('?vista=lista', {}, false);

      expect(mapaFalso()).toBeUndefined();
      expect(el().querySelector('table')).not.toBeNull();
      http.expectNone((p) => p.url === '/api/panel/reclamos/mapa');
    });

    it('unaUrlConBasuraNoRompeLaPantalla', async () => {
      await abrir('?estado=NADA&agente=zzz&pagina=-3');

      expect(texto()).toContain('Reclamos');
    });
  });

  describe('filtros en un renglón', () => {
    it('elDesplegableDeEstadoMuestraLaCantidadDeCadaUno', async () => {
      await abrir();

      const opciones = Array.from(el().querySelectorAll('#filtro-estado option')).map((o) => o.textContent?.trim());
      expect(opciones).toEqual(['Todos 26', 'Recibido 6', 'Asignado 5', 'En reparación 8', 'Resuelto 5', 'Descartado 2']);
    });

    it('elegirUnEstadoLoPoneEnLaUrlYVuelveALaPrimeraPagina', async () => {
      await abrir('?pagina=3');

      await cambiarSelect('filtro-estado', 'EN_REPARACION');

      expect(url()).toBe(`${BASE}?estado=EN_REPARACION`);
      const l = lista();
      expect(l.request.params.get('estado')).toBe('EN_REPARACION');
      expect(l.request.params.get('pagina')).toBe('0');
      expect(mapa().request.params.get('estado')).toBe('EN_REPARACION');
      // El resumen no depende del estado: no se vuelve a pedir.
      http.expectNone((p) => p.url === '/api/panel/reclamos/resumen');
      l.flush(pagina([]));
      http.match((p) => p.url === '/api/panel/reclamos/mapa').forEach((m) => m.flush({ puntos: [], recortado: false }));
    });

    it('elegirTipoYAgenteTambienVanALaUrlYAlResumen', async () => {
      await abrir();

      await cambiarSelect('filtro-tipo', 'LUZ');
      expect(url()).toBe(`${BASE}?tipo=LUZ`);
      expect(resumen().request.params.get('tipo')).toBe('LUZ');
      lista().flush(pagina([]));
      mapa().flush({ puntos: [], recortado: false });

      await cambiarSelect('filtro-agente', '9');
      expect(url()).toBe(`${BASE}?tipo=LUZ&agente=9`);
      const l = lista();
      expect(l.request.params.get('agente')).toBe('9');
      l.flush(pagina([]));
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
    });

    it('buscarConEnterPoneLaBusquedaEnLaUrl', async () => {
      await abrir();

      const caja = el().querySelector('#filtro-buscar') as HTMLInputElement;
      caja.value = 'San Martín';
      caja.dispatchEvent(new Event('input'));
      caja.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      await esperar();

      expect(decodeURIComponent(url())).toBe(`${BASE}?buscar=San Martín`);
      expect(lista().request.params.get('buscar')).toBe('San Martín');
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
      http.match((p) => p.url === '/api/panel/reclamos').forEach((p) => p.flush(pagina([])));
    });

    it('buscarUnNumeroEsValido', async () => {
      await abrir();

      const caja = el().querySelector('#filtro-buscar') as HTMLInputElement;
      caja.value = '42';
      caja.dispatchEvent(new Event('input'));
      caja.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      await esperar();

      expect(url()).toBe(`${BASE}?buscar=42`);
      lista().flush(pagina([]));
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
    });

    it('unaSolaLetraNoSeBuscaYSeExplica', async () => {
      await abrir();

      const caja = el().querySelector('#filtro-buscar') as HTMLInputElement;
      caja.value = 'a';
      caja.dispatchEvent(new Event('input'));
      caja.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      await esperar();

      expect(url()).toBe(BASE);
      expect(texto()).toContain('Escribí al menos 2 letras, o un número de reclamo.');
      http.expectNone((p) => p.url === '/api/panel/reclamos');
    });

    it('conLaBusquedaEscritaSeBuscaSolaDespuesDeUnRatito', async () => {
      await abrir();
      const caja = el().querySelector('#filtro-buscar') as HTMLInputElement;
      caja.value = 'luz';
      caja.dispatchEvent(new Event('input'));
      expect(url()).toBe(BASE);

      await new Promise((r) => setTimeout(r, 600));
      await esperar();

      expect(url()).toBe(`${BASE}?buscar=luz`);
      lista().flush(pagina([]));
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
    });

    it('sinResultadosConFiltrosOfreceQuitarlos', async () => {
      await abrir('?tipo=LUZ', { reclamos: [] });

      expect(texto()).toContain('No hay reclamos con esos filtros');
      boton('Quitar los filtros').click();
      await esperar();

      expect(url()).toBe(BASE);
      lista().flush(pagina([]));
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
    });

    it('sinReclamosNiFiltrosDiceQueTodaviaNoHay', async () => {
      await abrir('', { reclamos: [] });

      expect(texto()).toContain('Todavía no hay reclamos');
    });
  });

  describe('aviso de reclamos sin asignar', () => {
    it('diceCuantosEsperanYDesdeCuandoElMasAntiguo', async () => {
      await abrir();

      const aviso = el().querySelector('.aviso.alerta') as HTMLElement;
      expect(aviso.textContent?.replace(/\s+/g, ' ')).toContain('6 reclamos esperan que los asignes. El más antiguo es de hace 5 días.');
    });

    it('sePuedeCerrarConLaX', async () => {
      await abrir();

      (el().querySelector('.aviso button.cerrar') as HTMLButtonElement).click();
      harness.detectChanges();

      expect(el().querySelector('.aviso.alerta')).toBeNull();
    });

    it('conCeroSinAsignarNoAparece', async () => {
      await abrir('', { resumen: { ...RESUMEN, sinAsignar: 0, sinAsignarMasAntiguo: null } });

      expect(el().querySelector('.aviso.alerta')).toBeNull();
    });

    it('elTextoSeAdaptaAUnoYAHoy', () => {
      const ahora = new Date(2026, 10, 10, 12, 0);
      expect(textoDelAviso({ ...RESUMEN, sinAsignar: 1, sinAsignarMasAntiguo: new Date(2026, 10, 10, 8, 0).toISOString() }, ahora)).toBe(
        '1 reclamo espera que lo asignes. El más antiguo es de hoy.',
      );
      expect(textoDelAviso({ ...RESUMEN, sinAsignar: 2, sinAsignarMasAntiguo: new Date(2026, 10, 9, 8, 0).toISOString() }, ahora)).toBe(
        '2 reclamos esperan que los asignes. El más antiguo es de hace 1 día.',
      );
    });
  });

  describe('lista', () => {
    const FILAS = [
      fila(42, { agente: { id: 4, nombre: 'Marcos Díaz' }, estado: 'ASIGNADO', cantidadDuplicados: 1 }),
      fila(43, { estado: 'DESCARTADO', numeroOriginal: 42, direccion: null }),
      fila(44, { estado: 'RESUELTO', cantidadDuplicados: 3 }),
    ];

    it('conMapaMuestraNumeroTipoDireccionEstadoAgenteYFecha', async () => {
      await abrir('', { reclamos: FILAS });

      const primera = el().querySelector('#fila-42') as HTMLElement;
      expect(primera.textContent).toContain('N.º 42');
      expect(primera.textContent).toContain('Bache o calle rota');
      expect(primera.textContent).toContain('San Martín 450');
      expect(primera.textContent).toContain('Asignado');
      expect(primera.textContent).toContain('Marcos Díaz');
      expect(primera.textContent).toContain('3/11/2026');
      expect(primera.querySelector('a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/reclamos/42');
      expect(primera.querySelector('a')?.getAttribute('aria-label')).toBe('Ver detalle del reclamo n.º 42');
    });

    it('marcaLosDuplicadosYLosOriginales', async () => {
      await abrir('', { reclamos: FILAS });

      expect(el().querySelector('#fila-42')?.textContent).toContain('1 duplicado vinculado');
      expect(el().querySelector('#fila-43')?.textContent).toContain('Duplicado de n.º 42');
      expect(el().querySelector('#fila-43')?.textContent).toContain('Sin dirección');
      expect(el().querySelector('#fila-44')?.textContent).toContain('3 duplicados vinculados');
    });

    it('elEstadoLlevaIconoYPalabra', async () => {
      await abrir('', { reclamos: FILAS });

      const chip = el().querySelector('#fila-44 app-chip-estado .chip') as HTMLElement;
      expect(chip.textContent?.trim()).toBe('Resuelto');
      expect(chip.querySelector('svg')).not.toBeNull();
    });

    it('conMuchosReclamosPaginaConAnteriorYSiguiente', async () => {
      await abrir('', { reclamos: [fila(1)], total: 60 });
      expect(texto()).toContain('Página 1 de 3');
      expect(boton('Anterior').disabled).toBe(true);

      boton('Siguiente').click();
      await esperar();

      expect(url()).toBe(`${BASE}?pagina=2`);
      const l = lista();
      expect(l.request.params.get('pagina')).toBe('1');
      l.flush({ reclamos: [fila(2)], pagina: 1, tamanio: 25, total: 60 });
      // Cambiar de página no vuelve a pedir el mapa ni el resumen.
      http.expectNone((p) => p.url === '/api/panel/reclamos/mapa');
      http.expectNone((p) => p.url === '/api/panel/reclamos/resumen');
    });

    it('unaPaginaFueraDeRangoLlevaALaUltimaConReclamos', async () => {
      await harness.navigateByUrl(BASE + '?vista=lista&pagina=5');
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      http.expectOne('/api/mi-comuna/limite').flush(null, { status: 204, statusText: 'No Content' });
      resumen().flush(RESUMEN);
      const primera = lista();
      expect(primera.request.params.get('pagina')).toBe('4');
      primera.flush({ reclamos: [], pagina: 4, tamanio: 25, total: 30 });
      await esperar();

      // 30 reclamos son dos páginas: va a la 2 (la 1 de adentro), sin pasar por "No hay reclamos".
      expect(texto()).not.toContain('No hay reclamos con esos filtros');
      expect(url()).toContain('pagina=2');
      const segunda = lista();
      expect(segunda.request.params.get('pagina')).toBe('1');
      segunda.flush({ reclamos: [fila(30)], pagina: 1, tamanio: 25, total: 30 });
      harness.detectChanges();
      expect(texto()).toContain('Página 2 de 2');
    });

    it('unaListaDeUnaSolaPaginaNoMuestraLosBotonesDePagina', async () => {
      await abrir();

      expect(el().querySelector('nav.paginas')).toBeNull();
    });

    it('siFallaLaListaLoAvisaYReintenta', async () => {
      await harness.navigateByUrl(BASE);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      limiteConMapa();
      resumen().flush(RESUMEN);
      lista().flush(null, { status: 500, statusText: 'Error' });
      mapa().flush({ puntos: [], recortado: false });
      harness.detectChanges();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar los reclamos');
      boton('Reintentar').click();
      harness.detectChanges();
      lista().flush(pagina([fila(12)]));
      resumen().flush(RESUMEN);
      mapa().flush({ puntos: [], recortado: false });
      harness.detectChanges();

      expect(el().querySelector('#fila-12')).not.toBeNull();
    });

    it('losTextosDeLosVecinosSonTextoPlano', async () => {
      await abrir('', { reclamos: [fila(5, { direccion: '<img src=x onerror=alert(1)> Calle', agente: { id: 1, nombre: '<b>Juan</b>' } })] });

      expect(el().querySelector('#fila-5 img')).toBeNull();
      expect(el().querySelector('#fila-5 b:not(.num)')).toBeNull();
      expect(el().querySelector('#fila-5')?.textContent).toContain('<b>Juan</b>');
    });
  });

  describe('mapa', () => {
    it('recibeUnPuntoPorReclamoYElLimiteDeLaComuna', async () => {
      const puntos: PuntoReclamoDto[] = [
        { numero: 12, estado: 'RECIBIDO', tipo: 'BACHE', latitud: -31.1, longitud: -64.3 },
        { numero: 13, estado: 'RESUELTO', tipo: 'LUZ', latitud: -31.2, longitud: -64.4 },
      ];
      await abrir('', { puntos });

      expect(mapaFalso()?.puntos()).toEqual(puntos);
    });

    it('siElMapaFueRecortadoLoAvisa', async () => {
      await abrir('', { recortado: true });

      expect(texto()).toContain('Hay más de 1000 reclamos con estos filtros: el mapa muestra los 1000 más nuevos.');
    });

    it('sinRecorteNoHayAviso', async () => {
      await abrir();

      expect(texto()).not.toContain('Hay más de 1000');
    });

    it('tocarUnPinResaltaLaFilaDeLaPagina', async () => {
      await abrir('', { reclamos: [fila(12), fila(13)] });

      mapaFalso()?.elegido.emit(13);
      harness.detectChanges();

      expect(mapaFalso()?.resaltado()).toBe(13);
      expect(el().querySelector('#fila-13')?.classList.contains('activa')).toBe(true);
      expect(el().querySelector('#fila-13')?.getAttribute('aria-current')).toBe('true');
      expect(el().querySelector('#fila-12')?.classList.contains('activa')).toBe(false);
    });

    it('tocarUnPinDeUnReclamoQueNoEstaEnLaPaginaAbreSuDetalle', async () => {
      await abrir('', { reclamos: [fila(12)] });
      const router = TestBed.inject(Router);
      const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      mapaFalso()?.elegido.emit(99);

      expect(navegar).toHaveBeenCalledWith(['/', 'salsipuedes-cordoba', 'panel', 'reclamos', 99]);
    });

    it('siElMapaNoSePuedeMostrarLaListaSigue', async () => {
      await abrir();

      mapaFalso()?.falla.emit();
      harness.detectChanges();

      expect(mapaFalso()).toBeUndefined();
      expect(texto()).toContain('No pudimos mostrar el mapa');
      expect(el().querySelector('#fila-12')).not.toBeNull();
    });
  });

  describe('orden y prioridad (MC-27)', () => {
    it('porDefectoOrdenaPorFechaYNoMandaElParametro', async () => {
      await abrir();

      const select = el().querySelector('#filtro-orden') as HTMLSelectElement;
      expect(select.value).toBe('fecha');
      expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual(['Fecha (más nuevos primero)', 'Prioridad (alta primero)']);
      expect(el().querySelector('label[for="filtro-orden"]')?.textContent).toBe('Ordenar por');
    });

    it('elegirPrioridadLoGuardaEnLaUrlVuelveALaPrimeraPaginaYPideElOrden', async () => {
      await abrir('?pagina=2', { total: 60 });

      await cambiarSelect('filtro-orden', 'prioridad');

      expect(url()).toBe(`${BASE}?orden=prioridad`);
      const l = lista();
      expect(l.request.params.get('orden')).toBe('prioridad');
      expect(l.request.params.get('pagina')).toBe('0');
      l.flush(pagina([fila(12)]));
      // El orden no cambia el resumen ni el mapa: no se vuelven a pedir (http.verify lo comprueba).
      http.expectNone((p) => p.url.endsWith("/mapa") || p.url.endsWith("/resumen"));
    });

    it('abreConElOrdenDeLaUrlYRespetaLosFiltros', async () => {
      await harness.navigateByUrl(`${BASE}?orden=prioridad&tipo=LUZ`);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([{ codigo: 'LUZ', nombre: 'Luz quemada', consejoFoto: '' }]);
      limiteConMapa();
      resumen().flush(RESUMEN);
      const l = lista();
      expect(l.request.params.get('orden')).toBe('prioridad');
      expect(l.request.params.get('tipo')).toBe('LUZ');
      l.flush(pagina([fila(12)]));
      mapa().flush({ puntos: [], recortado: false });
      harness.detectChanges();

      expect((el().querySelector('#filtro-orden') as HTMLSelectElement).value).toBe('prioridad');
    });

    it('volverAFechaSacaElParametroDeLaUrl', async () => {
      await abrir('?orden=prioridad');

      await cambiarSelect('filtro-orden', 'fecha');

      expect(url()).toBe(BASE);
      const l = lista();
      expect(l.request.params.has('orden')).toBe(false);
      l.flush(pagina([fila(12)]));
    });

    it('cadaFilaMuestraLaPrioridadEnLaVistaConMapa', async () => {
      await abrir('', {
        reclamos: [
          fila(1, { prioridad: { valor: 'ALTA', origen: 'IA' } }),
          fila(2, { prioridad: { valor: 'BAJA', origen: 'PERSONAL' } }),
          fila(3, { prioridad: { valor: null, origen: 'NINGUNA' } }),
          fila(4),
        ],
      });

      const chip = (n: number) => el().querySelector(`#fila-${n} app-chip-prioridad`)?.textContent?.replace(/\s+/g, ' ').trim();
      expect(chip(1)).toBe('Alta Sugerida, sugerida por la IA');
      expect(chip(2)).toBe('Baja');
      expect(chip(3)).toBe('Sin prioridad');
      expect(chip(4)).toBe('Sin prioridad');
    });

    it('enSoloListaLaTablaTieneLaColumnaPrioridad', async () => {
      await abrir('?vista=lista', { reclamos: [fila(1, { prioridad: { valor: 'MEDIA', origen: 'PERSONAL' } })] }, false);

      const titulos = Array.from(el().querySelectorAll('th')).map((t) => t.textContent?.trim());
      expect(titulos).toContain('Prioridad');
      expect(el().querySelector('#fila-1 td app-chip-prioridad')?.textContent?.trim()).toBe('Media');
    });
  });

  describe('vistas', () => {
    it('soloListaPasaALaUrlYMuestraLaTablaSinMapa', async () => {
      await abrir();
      expect(boton('Lista y mapa').getAttribute('aria-pressed')).toBe('true');

      boton('Solo lista').click();
      await esperar();

      expect(url()).toBe(`${BASE}?vista=lista`);
      expect(boton('Solo lista').getAttribute('aria-pressed')).toBe('true');
      expect(mapaFalso()).toBeUndefined();
      expect(el().querySelector('table')).not.toBeNull();
      // Misma lista: no se vuelve a pedir; el mapa tampoco hace falta.
      http.expectNone((p) => p.url === '/api/panel/reclamos');
      http.expectNone((p) => p.url === '/api/panel/reclamos/mapa');
    });

    it('volverAListaYMapaPideLosPuntos', async () => {
      await abrir('?vista=lista', { conLimite: true }, false);

      boton('Lista y mapa').click();
      await esperar();

      expect(url()).toBe(BASE);
      mapa().flush({ puntos: [], recortado: false });
      expect(mapaFalso()).toBeDefined();
    });

    it('losFiltrosSeConservanAlCambiarDeVista', async () => {
      await abrir('?tipo=LUZ');

      boton('Solo lista').click();
      await esperar();

      expect(url()).toBe(`${BASE}?tipo=LUZ&vista=lista`);
    });
  });

describe('comuna sin mapa (sin límite cargado)', () => {
    const TEXTO = 'Debe solicitar a soporte que cargue el mapa para su comuna.';
    const botonMapa = () => boton('Lista y mapa');

    async function abrirSinLimite(query = '') {
      await harness.navigateByUrl(BASE + query);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      sinLimite();
      harness.detectChanges();
      resumen().flush(RESUMEN);
      lista().flush(pagina([fila(12)]));
      harness.detectChanges();
    }

    it('abreEnSoloListaSinPedirLosPuntosDelMapa', async () => {
      await abrirSinLimite();

      expect(boton('Solo lista').getAttribute('aria-pressed')).toBe('true');
      expect(botonMapa().getAttribute('aria-pressed')).toBe('false');
      expect(mapaFalso()).toBeUndefined();
      expect(el().querySelector('table')).not.toBeNull();
      http.expectNone((p) => p.url === '/api/panel/reclamos/mapa');
    });

    it('elBotonListaYMapaQuedaDeshabilitadoPeroEnfocableYNoHaceNada', async () => {
      await abrirSinLimite();

      expect(botonMapa().getAttribute('aria-disabled')).toBe('true');
      expect(botonMapa().disabled).toBe(false);
      botonMapa().click();
      await esperar();

      expect(url()).toBe(BASE);
      expect(mapaFalso()).toBeUndefined();
      expect(boton('Solo lista').getAttribute('aria-pressed')).toBe('true');
    });

    it('elTextoApareceAlPasarElMouseYAlEnfocarYLlegaALosLectoresDePantalla', async () => {
      await abrirSinLimite();
      const globito = () => Array.from(document.querySelectorAll('[role="tooltip"]')).find((g) => g.textContent === TEXTO) as HTMLElement | undefined;

      expect(globito()).toBeDefined();
      expect(globito()!.style.visibility).toBe('hidden');
      botonMapa().dispatchEvent(new Event('mouseenter'));
      expect(globito()!.style.visibility).toBe('visible');
      botonMapa().dispatchEvent(new Event('mouseleave'));
      botonMapa().dispatchEvent(new Event('focus'));
      expect(globito()!.style.visibility).toBe('visible');
      expect(botonMapa().getAttribute('aria-describedby')).toBe(globito()!.id);
    });

    it('unaUrlQueRecordabaLaVistaMapaTampocoVuelveAMostrarElMapa', async () => {
      await abrirSinLimite('?vista=mapa&tipo=LUZ');
      http.match((p) => p.url === '/api/panel/reclamos/mapa');

      expect(mapaFalso()).toBeUndefined();
      expect(boton('Solo lista').getAttribute('aria-pressed')).toBe('true');
    });

    it('unLimiteVacioTambienEsSinMapa', async () => {
      await harness.navigateByUrl(BASE);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      http.expectOne('/api/mi-comuna/limite').flush({});
      harness.detectChanges();
      resumen().flush(RESUMEN);
      lista().flush(pagina([fila(12)]));
      harness.detectChanges();

      expect(botonMapa().getAttribute('aria-disabled')).toBe('true');
      expect(mapaFalso()).toBeUndefined();
    });

    it('conLimiteElBotonNoEstaBloqueadoNiMuestraElTexto', async () => {
      await abrir();

      expect(botonMapa().getAttribute('aria-disabled')).toBeNull();
      expect(botonMapa().getAttribute('aria-describedby')).toBeNull();
      expect(mapaFalso()).toBeDefined();
    });

    it('mientrasSeEsperaElLimiteNoSeDibujaElMapaNiSePidenSusPuntos', async () => {
      await harness.navigateByUrl(BASE);
      http.expectOne('/api/panel/agentes').flush([]);
      http.expectOne('/api/tipos-reclamo').flush([]);
      resumen().flush(RESUMEN);
      lista().flush(pagina([fila(12)]));
      harness.detectChanges();

      expect(mapaFalso()).toBeUndefined();
      http.expectNone((p) => p.url === '/api/panel/reclamos/mapa');
      limiteConMapa();
      mapa().flush({ puntos: [], recortado: false });
      harness.detectChanges();
      expect(mapaFalso()).toBeDefined();
    });
  });

  describe('solo lista: selección y acciones', () => {
    const FILAS = [fila(37), fila(38, { estado: 'EN_REPARACION' }), fila(39, { estado: 'RESUELTO' })];
    const casilla = (n: number) => el().querySelector(`#fila-${n} input[type="checkbox"]`) as HTMLInputElement;
    const todas = () => el().querySelector('thead input[type="checkbox"]') as HTMLInputElement;

    it('cadaFilaTieneSuCasillaYTresBotonesDeSoloIconoConNombreParaLectores', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);

      const botones = Array.from(el().querySelectorAll('#fila-37 .iconos > *'));
      expect(botones.length).toBe(3);
      expect(botones.map((b) => b.getAttribute('aria-label'))).toEqual([
        'Ver detalle del reclamo n.º 37',
        'Cambiar el estado del reclamo n.º 37',
        'Descartar el reclamo n.º 37',
      ]);
      // Solo ícono: ningún texto visible adentro (el ícono es decorativo).
      expect(botones.every((b) => b.querySelector('.material-symbols-outlined')?.getAttribute('aria-hidden') === 'true')).toBe(true);
      expect(casilla(37).getAttribute('aria-label')).toBe('Elegir el reclamo n.º 37');
    });

    it('descartarEstaApagadoEnEnReparacionResueltosYDescartadosYNoAbreLaVentana', async () => {
      await abrir('?vista=lista', { reclamos: [fila(39, { estado: 'RESUELTO' }), fila(40, { estado: 'DESCARTADO' }), fila(41), fila(42, { estado: 'EN_REPARACION' })] }, false);

      const descartar = (n: number) => el().querySelector(`#fila-${n} .icono.peligro`) as HTMLButtonElement;
      expect(descartar(39).getAttribute('aria-disabled')).toBe('true');
      expect(descartar(40).getAttribute('aria-disabled')).toBe('true');
      expect(descartar(41).getAttribute('aria-disabled')).toBeNull();
      expect(descartar(42).getAttribute('aria-disabled')).toBe('true');
      expect(descartar(42).getAttribute('aria-label')).toBe('Descartar el reclamo n.º 42 (no disponible: el reclamo está en reparación)');

      descartar(39).click();
      harness.detectChanges();
      expect(el().querySelector('app-dialogo-descartar')).toBeNull();

      descartar(41).click();
      harness.detectChanges();
      expect(el().querySelector('app-dialogo-descartar')).not.toBeNull();
    });

    it('elBotonDeCambiarEstadoDeLaFilaAbreLaVentanaConEseReclamo', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);

      (el().querySelector('#fila-38 button.icono:not(.peligro)') as HTMLButtonElement).click();
      harness.detectChanges();

      expect(el().querySelector('app-dialogo-cambiar-estado')).not.toBeNull();
      expect(texto()).toContain('Reclamo n.º 38 (ahora: En reparación)');
    });

    it('aparecenLasAccionesAlElegirYDesaparecenAlQuitar', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      expect(el().querySelector('.barra-acciones')).toBeNull();

      casilla(37).click();
      harness.detectChanges();
      expect(el().querySelector('.barra-acciones')?.textContent).toContain('1 reclamo elegido');

      casilla(38).click();
      harness.detectChanges();
      expect(el().querySelector('.barra-acciones')?.textContent).toContain('2 reclamos elegidos');
      expect(todas().indeterminate).toBe(true);

      boton('Quitar la selección').click();
      harness.detectChanges();
      expect(el().querySelector('.barra-acciones')).toBeNull();
    });

    it('laBarraTieneSuLugarReservadoYNoMueveLaTabla', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      const reserva = el().querySelector('.reserva-barra') as HTMLElement;
      expect(reserva).not.toBeNull();
      const tabla = el().querySelector('.tarjeta') as HTMLElement;
      const antes = Array.from(reserva.parentElement!.children).indexOf(reserva);

      casilla(37).click();
      harness.detectChanges();

      // La barra aparece adentro del lugar reservado (y no como un bloque nuevo que empuje la tabla).
      expect(reserva.querySelector('.barra-acciones')).not.toBeNull();
      expect(Array.from(reserva.parentElement!.children).indexOf(reserva)).toBe(antes);
      expect(reserva.nextElementSibling?.contains(tabla)).toBe(true);
    });

    it('elegirTodosEligeTodaLaPaginaYDeNuevoLaQuita', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);

      todas().click();
      harness.detectChanges();
      expect(el().querySelector('.barra-acciones')?.textContent).toContain('3 reclamos elegidos');
      expect(casilla(39).checked).toBe(true);
      expect(todas().checked).toBe(true);

      todas().click();
      harness.detectChanges();
      expect(el().querySelector('.barra-acciones')).toBeNull();
    });

    it('avisaCuantosAplicanParaDescartarAntesDeConfirmar', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      todas().click();
      harness.detectChanges();

      expect(el().querySelector('.barra-acciones .aplican')?.textContent).toContain('Descartar aplica a 1 de 3: el n.º 38 está en reparación y el n.º 39 ya está cerrado.');

      boton('Descartar').click();
      harness.detectChanges();
      expect(el().querySelector('app-dialogo-descartar')?.textContent).toContain('Descartar aplica a 1 de 3: el n.º 38 está en reparación y el n.º 39 ya está cerrado.');
    });

    it('cambiarEstadoDeVariosLoAbreConTodosLosElegidos', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      casilla(37).click();
      casilla(38).click();
      harness.detectChanges();

      boton('Cambiar estado').click();
      harness.detectChanges();

      expect(el().querySelector('app-dialogo-cambiar-estado')?.textContent).toContain('2 reclamos: n.º 37, n.º 38');
    });

    it('cambiarDeFiltroOPaginaLimpiaLaSeleccion', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      casilla(37).click();
      harness.detectChanges();

      await cambiarSelect('filtro-tipo', 'LUZ');
      lista().flush(pagina([fila(50)]));
      resumen().flush(RESUMEN);
      harness.detectChanges();

      expect(el().querySelector('.barra-acciones')).toBeNull();
    });

    it('despuesDeCambiarElEstadoSeVuelveAPedirTodoSinCerrarLaVentana', async () => {
      await abrir('?vista=lista', { reclamos: FILAS }, false);
      casilla(37).click();
      harness.detectChanges();
      boton('Cambiar estado').click();
      harness.detectChanges();
      const radio = Array.from(el().querySelectorAll('app-dialogo-cambiar-estado label.opcion')).find((l) => l.textContent?.trim() === 'En reparación')?.querySelector('input') as HTMLInputElement;
      radio.click();
      harness.detectChanges();
      (Array.from(el().querySelectorAll('app-dialogo-cambiar-estado button')).find((b) => b.textContent?.trim() === 'Cambiar estado') as HTMLButtonElement).click();

      const pedido: TestRequest = http.expectOne('/api/panel/reclamos/cambios-de-estado');
      expect(pedido.request.body).toEqual({ numeros: [37], estado: 'EN_REPARACION' });
      pedido.flush({ aplicados: [37], omitidos: [] });
      harness.detectChanges();

      // La pantalla de atrás recarga su lista, su resumen y los agentes (cambió su carga); la ventana sigue mostrando el resultado.
      http.expectOne('/api/panel/agentes').flush([{ id: 4, nombre: 'Marcos Díaz', enCurso: 4 }]);
      lista().flush(pagina([fila(37, { estado: 'EN_REPARACION' })]));
      resumen().flush(RESUMEN);
      harness.detectChanges();
      expect(el().querySelector('app-dialogo-cambiar-estado')?.textContent).toContain('Cambió 1 reclamo: n.º 37');

      (Array.from(el().querySelectorAll('app-dialogo-cambiar-estado button')).find((b) => b.textContent?.trim() === 'Cerrar') as HTMLButtonElement).click();
      harness.detectChanges();
      expect(el().querySelector('app-dialogo-cambiar-estado')).toBeNull();
    });
  });
});
