import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { ATRIBUCION_ARGENMAP, ATRIBUCION_OSM, ESPERA_TESELAS_MS, RespaldoDeTeselas, URL_ARGENMAP, URL_OSM, ZOOM_MAXIMO } from '../../../mapas/capas-base';
import { bordesParaDibujar } from '../../../mapas/geo';
import { COLOR_PIN } from '../../../reclamos/estilo-estados';
import { PuntoReclamoDto } from '../../../reclamos/modelos-panel';
import { nombreDeEstado } from '../../../reclamos/seguimiento';

// Centro de Argentina: solo se usa cuando no hay ni pines ni límite de la comuna.
const CENTRO_POR_DEFECTO: L.LatLngTuple = [-34.6, -64.3];
const ZOOM_POR_DEFECTO = 5;

// El mapa del panel: un pin por reclamo con su número y el color de su estado, y el límite de la comuna si lo
// hay. Tocar un pin avisa cuál se eligió. Es lo único del panel que toca Leaflet; las pruebas lo reemplazan
// por uno falso con las mismas entradas y salidas.
@Component({
  selector: 'app-mapa-reclamos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="mapa" role="group" [attr.aria-label]="etiqueta()"><div #lienzo class="lienzo" [style.height]="altura()"></div></div>`,
  styles: `
    :host { display: block; }
    .mapa { position: relative; overflow: hidden; border: 2px solid #cbd2d9; border-radius: 16px; background: #dce8d6; }
    .lienzo { width: 100%; min-height: 240px; }
    :host ::ng-deep .leaflet-bar a { width: 40px; height: 40px; line-height: 40px; font-size: 20px; }
    :host ::ng-deep .leaflet-control-attribution { font-size: 12px; color: #1f2933; }
    :host ::ng-deep .pin-reclamo { background: transparent; border: 0; }
    :host ::ng-deep .pin-reclamo svg { display: block; filter: drop-shadow(0 2px 2px rgba(0, 0, 0, 0.35)); transition: transform 160ms cubic-bezier(0.23, 1, 0.32, 1); transform-origin: 50% 100%; }
    :host ::ng-deep .pin-reclamo.resaltado svg { transform: scale(1.35); }
    :host ::ng-deep .pin-reclamo:focus-visible { outline: 3px solid #1d4ed8; outline-offset: 2px; border-radius: 8px; }
    @media (prefers-reduced-motion: reduce) { :host ::ng-deep .pin-reclamo svg { transition: none; } }
  `,
})
export class MapaReclamos {
  readonly puntos = input.required<readonly PuntoReclamoDto[]>();
  // Figura GeoJSON del límite de la comuna, o null.
  readonly limite = input<unknown>(null);
  // El número del reclamo resaltado (el de la fila elegida), o null.
  readonly resaltado = input<number | null>(null);
  readonly altura = input('520px');
  readonly etiqueta = input('Mapa con los reclamos');

  // Se tocó el pin de un reclamo.
  readonly elegido = output<number>();
  // El mapa no se pudo mostrar (Leaflet falló o no llegó ninguna tesela).
  readonly falla = output<void>();

  private readonly lienzo = viewChild.required<ElementRef<HTMLElement>>('lienzo');
  private mapa: L.Map | null = null;
  private pines: L.LayerGroup | null = null;
  private lineas: L.LayerGroup | null = null;
  private readonly marcadores = new Map<number, L.Marker>();
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    afterNextRender(() => this.iniciar());
    effect(() => {
      const puntos = this.puntos();
      untracked(() => this.dibujarPines(puntos));
    });
    effect(() => {
      const limite = this.limite();
      untracked(() => this.dibujarLimite(limite));
    });
    effect(() => {
      const numero = this.resaltado();
      untracked(() => this.resaltar(numero));
    });
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.temporizador);
      this.mapa?.remove();
      this.mapa = null;
    });
  }

  private iniciar(): void {
    try {
      const mapa = L.map(this.lienzo().nativeElement, { center: CENTRO_POR_DEFECTO, zoom: ZOOM_POR_DEFECTO, maxZoom: ZOOM_MAXIMO, keyboard: true });
      this.mapa = mapa;
      this.ponerCapas(mapa);
      this.pines = L.layerGroup().addTo(mapa);
      this.dibujarLimite(untracked(() => this.limite()));
      this.dibujarPines(untracked(() => this.puntos()));
      this.resaltar(untracked(() => this.resaltado()));
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

  // El pin lleva el número (siempre un entero que viene del servidor) y el color del estado; el nombre
  // del estado va en el título y el nombre accesible, para no depender solo del color.
  private icono(p: PuntoReclamoDto): L.DivIcon {
    const color = COLOR_PIN[p.estado] ?? '#52606D';
    const numero = Number.isFinite(p.numero) ? Math.trunc(p.numero) : 0;
    const ancho = numero >= 1000 ? 46 : numero >= 100 ? 40 : 34;
    return L.divIcon({
      className: 'pin-reclamo',
      html: `<svg width="${ancho}" height="42" viewBox="0 0 34 42" aria-hidden="true" focusable="false"><path d="M17 41C5 28 1 22 1 15a16 16 0 1 1 32 0c0 7-4 13-16 26z" fill="${color}" stroke="#fff" stroke-width="2.5"/><text x="17" y="21" text-anchor="middle" font-family="Roboto, Arial, sans-serif" font-size="${numero >= 1000 ? 11 : 14}" font-weight="700" fill="#fff">${numero}</text></svg>`,
      iconSize: [ancho, 42],
      iconAnchor: [ancho / 2, 42],
    });
  }

  private dibujarPines(puntos: readonly PuntoReclamoDto[]): void {
    if (!this.mapa || !this.pines) {
      return;
    }
    this.pines.clearLayers();
    this.marcadores.clear();
    const validos = puntos.filter((p) => Number.isFinite(p.latitud) && Number.isFinite(p.longitud));
    for (const p of validos) {
      const marcador = L.marker([p.latitud, p.longitud], {
        icon: this.icono(p),
        title: `Reclamo n.º ${p.numero}, ${nombreDeEstado(p.estado)}`,
        alt: `Reclamo n.º ${p.numero}, ${nombreDeEstado(p.estado)}`,
        keyboard: true,
      });
      marcador.on('click', () => this.elegido.emit(p.numero));
      marcador.addTo(this.pines);
      this.marcadores.set(p.numero, marcador);
    }
    this.encuadrar(validos);
    this.resaltar(untracked(() => this.resaltado()));
  }

  // Muestra todos los pines; sin pines, la comuna entera (si tiene límite).
  private encuadrar(puntos: readonly PuntoReclamoDto[]): void {
    if (!this.mapa) {
      return;
    }
    if (puntos.length > 0) {
      this.mapa.fitBounds(L.latLngBounds(puntos.map((p): L.LatLngTuple => [p.latitud, p.longitud])), { padding: [40, 40], maxZoom: 17, animate: false });
      return;
    }
    const anillos = bordesParaDibujar(untracked(() => this.limite()));
    if (anillos.length > 0) {
      this.mapa.fitBounds(L.latLngBounds(anillos.flat().map((p): L.LatLngTuple => [p.lat, p.lng])), { padding: [20, 20], animate: false });
    }
  }

  private dibujarLimite(figura: unknown): void {
    if (!this.mapa) {
      return;
    }
    this.lineas?.remove();
    const anillos = bordesParaDibujar(figura);
    if (anillos.length === 0) {
      this.lineas = null;
      return;
    }
    const color = getComputedStyle(document.documentElement).getPropertyValue('--p').trim() || '#1d6aa8';
    this.lineas = L.layerGroup(
      anillos.map((anillo) => L.polyline(anillo.map((p): L.LatLngTuple => [p.lat, p.lng]), { color, weight: 3, dashArray: '8 8', interactive: false })),
    ).addTo(this.mapa);
    if (this.marcadores.size === 0) {
      this.encuadrar([]);
    }
  }

  private resaltar(numero: number | null): void {
    this.marcadores.forEach((m, n) => {
      const icono = m.getElement();
      icono?.classList.toggle('resaltado', n === numero);
      m.setZIndexOffset(n === numero ? 1000 : 0);
    });
  }
}
