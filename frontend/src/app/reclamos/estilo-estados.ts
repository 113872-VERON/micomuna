import { EstadoReclamo } from './modelos';

// Colores y dibujos de cada estado según la guía de diseño (sección 6): siempre ícono, palabra y color.
export const COLOR_ESTADO: Record<EstadoReclamo, { texto: string; fondo: string }> = {
  RECIBIDO: { texto: '#323F4B', fondo: '#E4E7EB' },
  ASIGNADO: { texto: '#0F3F6E', fondo: '#DCEBFA' },
  EN_REPARACION: { texto: '#7A4300', fondo: '#FCEBCF' },
  RESUELTO: { texto: '#14532D', fondo: '#D9F0DF' },
  DESCARTADO: { texto: '#3A3A3A', fondo: '#ECECEC' },
};

// Color pleno de los pines del mapa (con el número en blanco encima).
export const COLOR_PIN: Record<EstadoReclamo, string> = {
  RECIBIDO: '#52606D',
  ASIGNADO: '#0F3F6E',
  EN_REPARACION: '#7A4300',
  RESUELTO: '#14532D',
  DESCARTADO: '#3A3A3A',
};

// Trazos de los íconos de la maqueta de roles municipales (viewBox 24).
export const ICONO_ESTADO: Record<EstadoReclamo, string> = {
  RECIBIDO: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M3.5 7l8.5 6 8.5-6"/>',
  ASIGNADO: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4.5 4-6 8-6s7 1.5 8 6"/>',
  EN_REPARACION: '<path d="M14.5 6.5a4 4 0 0 0-5 5L3 18v3h3l1-1v-2h2l1-1v-2l1.5-1.5a4 4 0 0 0 5-5l-3 3-2-2z"/>',
  RESUELTO: '<circle cx="12" cy="12" r="9.5"/><path d="M7.5 12.5l3 3 6-6.5"/>',
  DESCARTADO: '<circle cx="12" cy="12" r="9"/><path d="M6 6l12 12"/>',
};

// Cuántos días de calendario pasaron desde una fecha (nunca negativo).
export function diasDesde(fecha: string, ahora = new Date()): number {
  const desde = new Date(fecha);
  if (Number.isNaN(desde.getTime())) {
    return 0;
  }
  const inicio = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Math.max(0, Math.round((inicio(ahora) - inicio(desde)) / 86_400_000));
}
