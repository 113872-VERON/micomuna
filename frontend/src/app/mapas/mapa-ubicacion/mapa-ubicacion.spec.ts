import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as L from 'leaflet';
import { Coordenadas } from '../../reclamos/modelos';
import { ESPERA_TESELAS_MS, ERRORES_PARA_RESPALDO } from '../capas-base';
import { MapaUbicacion, VistaMapa } from './mapa-ubicacion';

const CENTRO: Coordenadas = { latitud: -31.12, longitud: -64.3 };
const VISTA: VistaMapa = { centro: CENTRO, zoom: 14 };
const CUADRADO = {
  type: 'Polygon',
  coordinates: [[[-64.31, -31.13], [-64.29, -31.13], [-64.29, -31.11], [-64.31, -31.11], [-64.31, -31.13]]],
};

// Estas pruebas montan Leaflet de verdad en jsdom. No se bajan teselas: jsdom no carga imágenes,
// y los errores de carga se simulan disparando los eventos de la capa.
describe('MapaUbicacion (con Leaflet)', () => {
  let fixture: ComponentFixture<MapaUbicacion>;
  let componente: MapaUbicacion;

  beforeEach(() => {
    // jsdom no calcula tamaños: el mapa necesita un lienzo con medidas.
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(300);
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  async function montar(entradas: Record<string, unknown> = {}) {
    fixture = TestBed.createComponent(MapaUbicacion);
    componente = fixture.componentInstance;
    fixture.componentRef.setInput('vista', VISTA);
    for (const [nombre, valor] of Object.entries(entradas)) {
      fixture.componentRef.setInput(nombre, valor);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const el = () => fixture.nativeElement as HTMLElement;
  const mapa = () => (componente as unknown as { mapa: L.Map }).mapa;
  const capas = () => {
    const lista: L.TileLayer[] = [];
    mapa().eachLayer((c) => c instanceof L.TileLayer && lista.push(c));
    return lista;
  };
  const urlDe = (capa: L.TileLayer) => (capa as unknown as { _url: string })._url;
  const ign = () => capas().find((c) => urlDe(c).includes('ign.gob.ar'))!;
  const atribucion = () => el().querySelector('.leaflet-control-attribution')?.textContent ?? '';

  it('seMontaYCentraEnLaVistaPedida', async () => {
    await montar();

    expect(el().querySelector('.leaflet-container')).not.toBeNull();
    expect(mapa().getZoom()).toBe(14);
    const centro = componente.centroActual()!;
    expect(centro.latitud).toBeCloseTo(CENTRO.latitud, 4);
    expect(centro.longitud).toBeCloseTo(CENTRO.longitud, 4);
    // El pin fijo es un elemento encima del mapa, no un marcador de Leaflet.
    expect(el().querySelector('.pin')).not.toBeNull();
    expect(el().querySelector('.leaflet-marker-icon')).toBeNull();
  });

  it('elMapaBaseEsArgenmapConLaYInvertidaDelTms', async () => {
    await montar();

    expect(capas().length).toBe(1);
    expect(urlDe(ign())).toContain('capabaseargenmap');
    expect((ign().options as L.TileLayerOptions).tms).toBe(true);
  });

  it('dibujaElLimiteComoLineaPunteada', async () => {
    await montar({ limite: CUADRADO });

    const trazo = el().querySelector('.leaflet-overlay-pane path[stroke-dasharray]');
    expect(trazo).not.toBeNull();
    expect(trazo?.getAttribute('stroke-dasharray')).toBe('8 8');
  });

  it('conLimiteGrisaLoDeAfueraConUnaMascaraSinInteraccionDebajoDeLaLinea', async () => {
    await montar({ limite: CUADRADO });

    const mascara = el().querySelector('path.mascara-fuera') as SVGPathElement;
    expect(mascara).not.toBeNull();
    expect(mascara.getAttribute('fill-rule')).toBe('evenodd');
    expect(mascara.getAttribute('fill-opacity')).toBe('0.45');
    // Dos anillos: el mundo y el agujero de la comuna.
    expect((mascara.getAttribute('d') ?? '').match(/M/g)?.length).toBe(2);
    expect(mascara.classList.contains('leaflet-interactive')).toBe(false);
    // Está antes que la línea punteada: queda por debajo.
    const trazos = Array.from(el().querySelectorAll('.leaflet-overlay-pane path'));
    expect(trazos.indexOf(mascara)).toBeLessThan(trazos.findIndex((p) => p.getAttribute('stroke-dasharray') === '8 8'));
  });

  it('sinLimiteNoDibujaMascaraYSiLlegaDespuesLaDibuja', async () => {
    await montar();
    expect(el().querySelector('path.mascara-fuera')).toBeNull();

    fixture.componentRef.setInput('limite', CUADRADO);
    fixture.detectChanges();
    expect(el().querySelector('path.mascara-fuera')).not.toBeNull();

    fixture.componentRef.setInput('limite', null);
    fixture.detectChanges();
    expect(el().querySelector('path.mascara-fuera')).toBeNull();
  });

  it('sinLimiteNoDibujaNadaYSiLlegaDespuesLoDibuja', async () => {
    await montar();
    expect(el().querySelector('.leaflet-overlay-pane path')).toBeNull();

    fixture.componentRef.setInput('limite', CUADRADO);
    fixture.detectChanges();
    expect(el().querySelector('.leaflet-overlay-pane path[stroke-dasharray]')?.getAttribute('stroke-dasharray')).toBe('8 8');

    fixture.componentRef.setInput('limite', null);
    fixture.detectChanges();
    expect(el().querySelector('.leaflet-overlay-pane path')).toBeNull();
  });

  describe('movimientos', () => {
    it('unMovimientoRealEmiteDetenidoConElCentroNuevo', async () => {
      await montar();
      const emitidos: Coordenadas[] = [];
      componente.detenido.subscribe((p) => emitidos.push(p));

      mapa().setView([-31.125, -64.305], 14, { animate: false });

      expect(emitidos.length).toBe(1);
      expect(emitidos[0].latitud).toBeCloseTo(-31.125, 4);
      expect(emitidos[0].longitud).toBeCloseTo(-64.305, 4);
      expect(componente.centroActual()!.latitud).toBeCloseTo(-31.125, 4);
    });

    it('unMovimientoMenorQueElUmbralNoEmite', async () => {
      await montar();
      const emitidos: Coordenadas[] = [];
      componente.detenido.subscribe((p) => emitidos.push(p));

      // Menos de 2 píxeles (en zoom 14, un píxel son unos 0,00009 grados).
      mapa().setView([CENTRO.latitud + 0.00005, CENTRO.longitud], 14, { animate: false });

      expect(emitidos).toEqual([]);
    });

    it('hacerZoomSinMoverElCentroNoEmite', async () => {
      await montar();
      const emitidos: Coordenadas[] = [];
      componente.detenido.subscribe((p) => emitidos.push(p));

      mapa().setZoom(16, { animate: false });

      expect(emitidos).toEqual([]);
    });

    it('queLaPantallaCambieLaVistaNoCuentaComoQueElVecinoLoMovio', async () => {
      await montar();
      const emitidos: Coordenadas[] = [];
      componente.detenido.subscribe((p) => emitidos.push(p));

      fixture.componentRef.setInput('vista', { centro: { latitud: -31.2, longitud: -64.4 }, zoom: 17 });
      fixture.detectChanges();

      expect(emitidos).toEqual([]);
      expect(mapa().getZoom()).toBe(17);
      expect(componente.centroActual()!.latitud).toBeCloseTo(-31.2, 4);
    });

    it('elCartelArrastraElMapaSeVaCuandoElVecinoLoMueve', async () => {
      await montar();
      expect(el().querySelector('.chip')?.textContent).toContain('Arrastrá el mapa');

      mapa().setView([-31.13, -64.31], 14, { animate: false });
      fixture.detectChanges();

      expect(el().querySelector('.chip')).toBeNull();
    });
  });

  describe('solo lectura', () => {
    it('noSePuedeMoverNiTieneControlesNiCartel', async () => {
      await montar({ interactivo: false, altura: '150px' });
      const emitidos: Coordenadas[] = [];
      componente.detenido.subscribe((p) => emitidos.push(p));

      expect(mapa().dragging.enabled()).toBe(false);
      expect(mapa().touchZoom.enabled()).toBe(false);
      expect(mapa().scrollWheelZoom.enabled()).toBe(false);
      expect(mapa().doubleClickZoom.enabled()).toBe(false);
      expect(mapa().keyboard.enabled()).toBe(false);
      expect(el().querySelector('.leaflet-control-zoom')).toBeNull();
      expect(el().querySelector('.chip')).toBeNull();
      expect(el().querySelector('.lienzo')?.getAttribute('style')).toContain('150px');

      mapa().setView([-31.13, -64.31], 14, { animate: false });
      expect(emitidos).toEqual([]);
    });

    it('elInteractivoSiTieneControlesYTeclado', async () => {
      await montar();

      expect(mapa().dragging.enabled()).toBe(true);
      expect(mapa().keyboard.enabled()).toBe(true);
      expect(el().querySelector('.leaflet-control-zoom')).not.toBeNull();
    });
  });

  describe('atribuciones', () => {
    it('conArgenmapMuestraElIgnYOpenStreetMapConSuEnlace', async () => {
      await montar();

      expect(atribucion()).toContain('Instituto Geográfico Nacional');
      expect(atribucion()).toContain('© colaboradores de OpenStreetMap');
      const enlaces = Array.from(el().querySelectorAll('.leaflet-control-attribution a')).map((a) => a.getAttribute('href'));
      expect(enlaces).toContain('https://www.openstreetmap.org/copyright');
      expect(enlaces).toContain('https://www.ign.gob.ar');
    });
  });

  describe('respaldo', () => {
    const errorDeTesela = (capa: L.TileLayer) => capa.fire('tileerror', { tile: document.createElement('img'), coords: { x: 1, y: 1, z: 14 } });

    it('despuesDeVariosErroresDeArgenmapPasaAOpenStreetMapConSoloSuAtribucion', async () => {
      await montar();
      const argenmap = ign();

      for (let i = 0; i < ERRORES_PARA_RESPALDO; i++) {
        errorDeTesela(argenmap);
      }

      expect(capas().length).toBe(1);
      expect(urlDe(capas()[0])).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
      expect(atribucion()).toContain('© colaboradores de OpenStreetMap');
      expect(atribucion()).not.toContain('Instituto Geográfico Nacional');
    });

    it('conUnErrorSueltoSigueConArgenmap', async () => {
      await montar();

      errorDeTesela(ign());

      expect(urlDe(capas()[0])).toContain('capabaseargenmap');
    });

    it('siArgenmapYaCargoTeselasLosErroresNoCambianDeCapa', async () => {
      await montar();
      const argenmap = ign();
      argenmap.fire('tileload', { tile: document.createElement('img'), coords: { x: 1, y: 1, z: 14 } });

      for (let i = 0; i < ERRORES_PARA_RESPALDO + 2; i++) {
        errorDeTesela(argenmap);
      }

      expect(urlDe(capas()[0])).toContain('capabaseargenmap');
    });
  });

  describe('sin teselas', () => {
    it('siNoLlegaNingunaTeselaEnElTiempoMaximoAvisaQueFalla', async () => {
      vi.useFakeTimers();
      await montar();
      let fallas = 0;
      componente.falla.subscribe(() => fallas++);

      await vi.advanceTimersByTimeAsync(ESPERA_TESELAS_MS + 1);

      expect(fallas).toBe(1);
    });

    it('siLlegoAlgunaTeselaNoFalla', async () => {
      vi.useFakeTimers();
      await montar();
      let fallas = 0;
      componente.falla.subscribe(() => fallas++);
      ign().fire('tileload', { tile: document.createElement('img'), coords: { x: 1, y: 1, z: 14 } });

      await vi.advanceTimersByTimeAsync(ESPERA_TESELAS_MS + 1);

      expect(fallas).toBe(0);
    });

    it('alDestruirElComponenteSeLiberaElMapa', async () => {
      await montar();
      const contenedor = mapa().getContainer();

      fixture.destroy();

      expect(contenedor.querySelector('.leaflet-map-pane')).toBeNull();
      expect(mapa()).toBeNull();
    });
  });
});
