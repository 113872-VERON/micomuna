import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { Coordenadas } from '../../reclamos/modelos';
import {
  ATRIBUCION_ARGENMAP,
  ATRIBUCION_OSM,
  ESPERA_TESELAS_MS,
  RespaldoDeTeselas,
  URL_ARGENMAP,
  URL_OSM,
  ZOOM_MAXIMO,
} from '../capas-base';
import { anillosExterioresParaMascara, bordesParaDibujar } from '../geo';

// Un anillo que cubre todo el mundo: con el borde de la comuna como agujero, queda gris lo de afuera.
const MUNDO: L.LatLngTuple[] = [[-90, -180], [-90, 180], [90, 180], [90, -180]];

// Dónde y a qué altura mira el mapa.
export interface VistaMapa {
  centro: Coordenadas;
  zoom: number;
}

// El mapa de Leaflet con un pin fijo al centro (no es un marcador arrastrable): el vecino mueve el
// mapa y el punto es lo que queda debajo del pin. Es lo único que toca Leaflet en pantalla; las
// pruebas de los pasos lo reemplazan por uno falso con las mismas entradas, salidas y `centroActual()`.
@Component({
  selector: 'app-mapa-ubicacion',
  templateUrl: './mapa-ubicacion.html',
  styleUrl: './mapa-ubicacion.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MapaUbicacion {
  readonly vista = input.required<VistaMapa>();
  // Figura GeoJSON del límite de la comuna, o null.
  readonly limite = input<unknown>(null);
  // false: mapa chico de solo lectura (para el resumen).
  readonly interactivo = input(true);
  readonly altura = input('300px');
  readonly etiqueta = input('Mapa');

  // El mapa se detuvo en un punto distinto del anterior (lo movió el vecino).
  readonly detenido = output<Coordenadas>();
  // El mapa no se pudo mostrar (Leaflet falló o no llegó ninguna tesela): la pantalla sigue sin mapa.
  readonly falla = output<void>();

  private readonly lienzo = viewChild.required<ElementRef<HTMLElement>>('lienzo');
  private mapa: L.Map | null = null;
  private lineas: L.LayerGroup | null = null;
  private mascara: L.Polygon | null = null;
  // Último punto conocido: el que se pidió desde afuera o el último que informó el mapa.
  private conocido: Coordenadas | null = null;
  // Mientras la pantalla mueve el mapa por su cuenta, el fin del movimiento no es cosa del vecino.
  private programatico = false;
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  // El cartel "Arrastrá el mapa" se va apenas el vecino lo mueve.
  protected readonly sugerencia = signal(true);
  protected readonly mostrarCartel = computed(() => this.interactivo() && this.sugerencia());

  constructor() {
    afterNextRender(() => this.iniciar());
    effect(() => {
      const vista = this.vista();
      this.conocido = vista.centro;
      untracked(() => this.mover(vista));
    });
    effect(() => {
      const limite = this.limite();
      untracked(() => this.dibujarLimite(limite));
    });
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.temporizador);
      this.mapa?.remove();
      this.mapa = null;
    });
  }

  // Centro actual del mapa (lo usa el botón "Es acá").
  centroActual(): Coordenadas | null {
    const c = this.mapa?.getCenter();
    return c ? { latitud: c.lat, longitud: c.lng } : null;
  }

  private iniciar(): void {
    try {
      const vista = this.vista();
      const activo = this.interactivo();
      const mapa = L.map(this.lienzo().nativeElement, {
        center: [vista.centro.latitud, vista.centro.longitud],
        zoom: vista.zoom,
        maxZoom: ZOOM_MAXIMO,
        zoomControl: activo,
        dragging: activo,
        touchZoom: activo,
        scrollWheelZoom: activo,
        doubleClickZoom: activo,
        boxZoom: false,
        // Con el teclado las flechas mueven el mapa y + / - hacen zoom.
        keyboard: activo,
      });
      this.mapa = mapa;
      this.ponerCapas(mapa);
      this.dibujarLimite(untracked(() => this.limite()));
      mapa.on('dragstart', () => this.sugerencia.set(false));
      mapa.on('moveend', () => this.alDetenerse());
    } catch {
      this.falla.emit();
    }
  }

  private ponerCapas(mapa: L.Map): void {
    const respaldo = new RespaldoDeTeselas();
    let llegoAlguna = false;
    const llego = () => (llegoAlguna = true);
    const osm = L.tileLayer(URL_OSM, { maxZoom: ZOOM_MAXIMO, attribution: ATRIBUCION_OSM });
    const ign = L.tileLayer(URL_ARGENMAP, { maxZoom: ZOOM_MAXIMO, tms: true, attribution: ATRIBUCION_ARGENMAP });
    ign.on('tileload', () => {
      respaldo.teselaCargada();
      llego();
    });
    ign.on('tileerror', () => {
      if (respaldo.teselaConError()) {
        mapa.removeLayer(ign);
        osm.addTo(mapa);
      }
    });
    osm.on('tileload', llego);
    ign.addTo(mapa);
    this.temporizador = setTimeout(() => {
      if (!llegoAlguna) {
        this.falla.emit();
      }
    }, ESPERA_TESELAS_MS);
  }

  private mover(vista: VistaMapa): void {
    if (!this.mapa) {
      return;
    }
    this.programatico = true;
    try {
      this.mapa.setView([vista.centro.latitud, vista.centro.longitud], vista.zoom, { animate: false });
    } finally {
      this.programatico = false;
    }
  }

  private dibujarLimite(figura: unknown): void {
    if (!this.mapa) {
      return;
    }
    this.lineas?.remove();
    this.mascara?.remove();
    const anillos = bordesParaDibujar(figura);
    if (anillos.length === 0) {
      this.lineas = null;
      this.mascara = null;
      return;
    }
    // La máscara va primero: queda debajo de la línea punteada y no recibe toques.
    const huecos = anillosExterioresParaMascara(figura).map((a) => a.map((p): L.LatLngTuple => [p.lat, p.lng]));
    this.mascara = L.polygon([MUNDO, ...huecos], { stroke: false, fillColor: '#4b5563', fillOpacity: 0.45, fillRule: 'evenodd', interactive: false, className: 'mascara-fuera' }).addTo(this.mapa);
    const color = getComputedStyle(document.documentElement).getPropertyValue('--p').trim() || '#1d6aa8';
    this.lineas = L.layerGroup(
      anillos.map((anillo) =>
        L.polyline(
          anillo.map((p): L.LatLngTuple => [p.lat, p.lng]),
          { color, weight: 3, dashArray: '8 8', interactive: false },
        ),
      ),
    ).addTo(this.mapa);
  }

  private alDetenerse(): void {
    if (!this.mapa || this.programatico || !this.interactivo()) {
      return;
    }
    const c = this.mapa.getCenter();
    const punto: Coordenadas = { latitud: c.lat, longitud: c.lng };
    // Menos de 2 píxeles de diferencia (por ejemplo al hacer zoom) no cuenta como haberlo movido.
    const umbral = (2 * 360) / (256 * 2 ** this.mapa.getZoom());
    const anterior = this.conocido;
    if (anterior && Math.abs(anterior.latitud - punto.latitud) < umbral && Math.abs(anterior.longitud - punto.longitud) < umbral) {
      return;
    }
    this.conocido = punto;
    this.sugerencia.set(false);
    this.detenido.emit(punto);
  }
}
