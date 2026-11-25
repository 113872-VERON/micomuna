import * as L from 'leaflet';
import { anillosExterioresParaMascara } from './geo';

// Un anillo que cubre todo el mundo: con el borde de la comuna como agujero, queda gris lo de afuera.
const MUNDO: L.LatLngTuple[] = [[-90, -180], [-90, 180], [90, 180], [90, -180]];

// La máscara gris de "fuera de la comuna": no recibe toques y no tiene borde. Es la misma en todos los mapas.
export function crearMascara(figura: unknown): L.Polygon {
  const huecos = anillosExterioresParaMascara(figura).map((a) => a.map((p): L.LatLngTuple => [p.lat, p.lng]));
  return L.polygon([MUNDO, ...huecos], { stroke: false, fillColor: '#4b5563', fillOpacity: 0.45, fillRule: 'evenodd', interactive: false, className: 'mascara-fuera' });
}
