import { Coordenadas } from '../reclamos/modelos';
import { ReclamoAgenteDto } from './modelos-agente';
import { rangoDePrioridad } from '../reclamos/prioridad';

const RADIO_TIERRA_M = 6_371_000;
const aRadianes = (grados: number) => (grados * Math.PI) / 180;

// Distancia en línea recta (haversine) entre dos puntos, en metros. No es el camino por calle.
export function metrosEntre(a: Coordenadas, b: Coordenadas): number {
  const dLat = aRadianes(b.latitud - a.latitud);
  const dLng = aRadianes(b.longitud - a.longitud);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aRadianes(a.latitud)) * Math.cos(aRadianes(b.latitud)) * Math.sin(dLng / 2) ** 2;
  return 2 * RADIO_TIERRA_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

const UN_DECIMAL = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

// "A 400 m", "A 1,2 km". Debajo del kilómetro se redondea de a 10 m (un GPS no da más precisión que eso).
export function textoDeDistancia(metros: number): string {
  if (!Number.isFinite(metros) || metros < 0) {
    return '';
  }
  const redondeado = Math.round(metros / 10) * 10;
  if (redondeado < 10) {
    return 'A menos de 10 m';
  }
  if (redondeado < 1000) {
    return `A ${redondeado} m`;
  }
  return `A ${UN_DECIMAL.format(metros / 1000)} km`;
}

export type OrdenAgente = 'fecha' | 'tipo' | 'distancia' | 'prioridad';

// Un reclamo de la lista con la distancia ya calculada (o null si no hay ubicación o el reclamo no tiene coordenadas).
export interface ReclamoConDistancia {
  reclamo: ReclamoAgenteDto;
  metros: number | null;
}

export interface GrupoDeTarjetas {
  // Solo el orden por tipo agrupa con encabezado.
  titulo: string | null;
  tarjetas: ReclamoConDistancia[];
}

export function conDistancias(reclamos: readonly ReclamoAgenteDto[], desde: Coordenadas | null): ReclamoConDistancia[] {
  return reclamos.map((reclamo) => ({
    reclamo,
    metros: desde && reclamo.latitud !== null && reclamo.longitud !== null ? metrosEntre(desde, { latitud: reclamo.latitud, longitud: reclamo.longitud }) : null,
  }));
}

const tiempo = (fecha: string) => {
  const t = new Date(fecha).getTime();
  return Number.isNaN(t) ? 0 : t;
};
const porFecha = (a: ReclamoConDistancia, b: ReclamoConDistancia) => tiempo(a.reclamo.creadoEn) - tiempo(b.reclamo.creadoEn) || a.reclamo.numero - b.reclamo.numero;

// El más cerca primero; los que no tienen distancia van al final, por fecha.
const porCercania = (a: ReclamoConDistancia, b: ReclamoConDistancia) => {
  if (a.metros === null || b.metros === null) {
    return a.metros === b.metros ? porFecha(a, b) : a.metros === null ? 1 : -1;
  }
  return a.metros - b.metros || porFecha(a, b);
};

// Fecha: el más antiguo primero. Tipo: agrupados por tipo (A a Z), cada grupo por fecha. Distancia: el más cerca
// primero; los que no tienen distancia van al final, por fecha.
export function ordenar(lista: readonly ReclamoConDistancia[], orden: OrdenAgente): GrupoDeTarjetas[] {
  if (orden === 'tipo') {
    const porTipo = new Map<string, ReclamoConDistancia[]>();
    for (const t of [...lista].sort(porFecha)) {
      porTipo.set(t.reclamo.tipoNombre, [...(porTipo.get(t.reclamo.tipoNombre) ?? []), t]);
    }
    return [...porTipo.entries()].sort(([a], [b]) => a.localeCompare(b, 'es')).map(([titulo, tarjetas]) => ({ titulo, tarjetas }));
  }
  if (orden === 'distancia') {
    const tarjetas = [...lista].sort(porCercania);
    return [{ titulo: null, tarjetas }];
  }
  if (orden === 'prioridad') {
    // Alta primero y sin prioridad al final; a igual prioridad, el más cerca (si hay ubicación) y si no, el más viejo.
    const tarjetas = [...lista].sort((a, b) => rangoDePrioridad(a.reclamo.prioridad) - rangoDePrioridad(b.reclamo.prioridad) || porCercania(a, b));
    return [{ titulo: null, tarjetas }];
  }
  return [{ titulo: null, tarjetas: [...lista].sort(porFecha) }];
}

// Enlace "Cómo llegar": abre la app de mapas del teléfono. Sin coordenadas no hay enlace.
export function enlaceComoLlegar(latitud: number | null, longitud: number | null): string | null {
  if (latitud === null || longitud === null || !Number.isFinite(latitud) || !Number.isFinite(longitud)) {
    return null;
  }
  return `https://www.google.com/maps/dir/?api=1&destination=${latitud},${longitud}`;
}
