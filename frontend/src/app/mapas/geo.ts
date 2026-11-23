import { Coordenadas } from '../reclamos/modelos';

// Figura del límite de la comuna, tal como la manda GET /api/mi-comuna/limite (GeoJSON).
// Las posiciones van en grados [longitud, latitud]; el primer anillo es el borde y los demás, huecos.
export type Anillo = readonly (readonly number[])[];
export interface LimiteGeoJson {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: unknown;
}

export interface Punto {
  lat: number;
  lng: number;
}

function esAnillo(anillo: unknown): anillo is Anillo {
  return (
    Array.isArray(anillo) &&
    anillo.length >= 3 &&
    anillo.every((p) => Array.isArray(p) && p.length >= 2 && Number.isFinite(p[0]) && Number.isFinite(p[1]))
  );
}

// Cada polígono es una lista de anillos. Si la figura viene rara, se descarta lo que no sirve:
// un límite ilegible equivale a "sin límite" (el servidor comprueba igual).
export function poligonosDe(figura: unknown): Anillo[][] {
  const f = figura as Partial<LimiteGeoJson> | null;
  if (!f || !Array.isArray(f.coordinates)) {
    return [];
  }
  const crudos: unknown[] = f.type === 'Polygon' ? [f.coordinates] : f.type === 'MultiPolygon' ? f.coordinates : [];
  return crudos
    .map((poligono) => (Array.isArray(poligono) ? poligono.filter(esAnillo) : []))
    .filter((anillos) => anillos.length > 0);
}

// Anillos de cada polígono, listos para dibujar con Leaflet.
export function bordesParaDibujar(figura: unknown): Punto[][] {
  return poligonosDe(figura).flatMap((anillos) => anillos.map((a) => a.map(([lng, lat]) => ({ lat, lng }))));
}

// Anillo exterior de cada polígono de la comuna (sin los huecos): son los agujeros de la máscara gris.
export function anillosExterioresParaMascara(figura: unknown): Punto[][] {
  return poligonosDe(figura).map((anillos) => anillos[0].map(([lng, lat]) => ({ lat, lng })));
}

// Centro de la caja que rodea toda la figura (alcanza para centrar el mapa).
export function centroDelLimite(figura: unknown): Coordenadas | null {
  const puntos = poligonosDe(figura).flatMap((anillos) => anillos[0]);
  if (puntos.length === 0) {
    return null;
  }
  const lngs = puntos.map((p) => p[0]);
  const lats = puntos.map((p) => p[1]);
  return {
    latitud: (Math.min(...lats) + Math.max(...lats)) / 2,
    longitud: (Math.min(...lngs) + Math.max(...lngs)) / 2,
  };
}

const TOLERANCIA = 1e-9;

// Si el punto está sobre el segmento a-b (un punto sobre el borde cuenta como adentro, igual que en el servidor).
function enSegmento(x: number, y: number, a: readonly number[], b: readonly number[]): boolean {
  const cruz = (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
  if (Math.abs(cruz) > TOLERANCIA) {
    return false;
  }
  return (
    x >= Math.min(a[0], b[0]) - TOLERANCIA &&
    x <= Math.max(a[0], b[0]) + TOLERANCIA &&
    y >= Math.min(a[1], b[1]) - TOLERANCIA &&
    y <= Math.max(a[1], b[1]) + TOLERANCIA
  );
}

// 'borde' si está sobre la línea, 'dentro' / 'fuera' si no (algoritmo del rayo).
function posicionEnAnillo(x: number, y: number, anillo: Anillo): 'dentro' | 'fuera' | 'borde' {
  let dentro = false;
  for (let i = 0, j = anillo.length - 1; i < anillo.length; j = i++) {
    const a = anillo[i];
    const b = anillo[j];
    if (enSegmento(x, y, a, b)) {
      return 'borde';
    }
    if (a[1] > y !== b[1] > y && x < ((b[0] - a[0]) * (y - a[1])) / (b[1] - a[1]) + a[0]) {
      dentro = !dentro;
    }
  }
  return dentro ? 'dentro' : 'fuera';
}

// Solo sirve para avisar en el momento: el servidor vuelve a comprobar el punto al enviar.
// Sin figura legible no hay nada que comprobar y se da por adentro.
export function puntoDentroDelLimite(punto: Coordenadas, figura: unknown): boolean {
  const poligonos = poligonosDe(figura);
  if (poligonos.length === 0) {
    return true;
  }
  const x = punto.longitud;
  const y = punto.latitud;
  return poligonos.some(([borde, ...huecos]) => {
    const enBorde = posicionEnAnillo(x, y, borde);
    if (enBorde === 'fuera') {
      return false;
    }
    if (enBorde === 'borde') {
      return true;
    }
    // Adentro del borde: vale si no cae en un hueco (sobre el borde de un hueco cuenta como adentro).
    return huecos.every((h) => posicionEnAnillo(x, y, h) !== 'dentro');
  });
}
