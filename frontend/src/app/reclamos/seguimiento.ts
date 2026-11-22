import { CambioDeEstadoDto, EstadoReclamo, MotivoDescarte } from './modelos';

// Textos y cuentas del seguimiento de un reclamo. Sin Angular: se prueban solos.

export const NOMBRE_ESTADO: Record<EstadoReclamo, string> = {
  RECIBIDO: 'Recibido',
  ASIGNADO: 'Asignado',
  EN_REPARACION: 'En reparación',
  RESUELTO: 'Resuelto',
  DESCARTADO: 'Descartado',
};

export const TEXTO_MOTIVO: Record<MotivoDescarte, string> = {
  DUPLICADO: 'Ya había un reclamo por el mismo problema',
  NO_ES_DE_LA_COMUNA: 'No le corresponde a la comuna',
  NO_SE_PUEDE_ATENDER: 'La comuna no lo puede atender',
  OTRO: 'Otro motivo',
};

// Los cuatro pasos del camino normal, en orden. Descartado no es un paso: reemplaza a la línea.
export const PASOS: readonly Exclude<EstadoReclamo, 'DESCARTADO'>[] = ['RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO'];

export type SituacionDelPaso = 'hecho' | 'actual' | 'pendiente';

export interface PasoDelReclamo {
  estado: Exclude<EstadoReclamo, 'DESCARTADO'>;
  nombre: string;
  situacion: SituacionDelPaso;
  // Cuándo se llegó a ese paso; null si todavía no, o si el reclamo se saltó ese paso.
  fecha: string | null;
}

export function nombreDeEstado(estado: string): string {
  return NOMBRE_ESTADO[estado as EstadoReclamo] ?? estado;
}

export function textoDeMotivo(motivo: string): string {
  return TEXTO_MOTIVO[motivo as MotivoDescarte] ?? motivo;
}

// La última vez que el reclamo llegó a ese estado (si volvió atrás y avanzó de nuevo, vale la última).
export function fechaDelEstado(historial: readonly CambioDeEstadoDto[], estado: EstadoReclamo): string | null {
  for (let i = historial.length - 1; i >= 0; i--) {
    if (historial[i].estado === estado) {
      return historial[i].fecha;
    }
  }
  return null;
}

// Los 4 pasos para un estado actual. Un reclamo que está en un estado ya pasó por los anteriores,
// aunque el historial no tenga la fecha de alguno. Para DESCARTADO no hay pasos.
export function pasosDelReclamo(estado: EstadoReclamo, historial: readonly CambioDeEstadoDto[]): PasoDelReclamo[] {
  if (estado === 'DESCARTADO') {
    return [];
  }
  const actual = PASOS.indexOf(estado);
  return PASOS.map((paso, i) => ({
    estado: paso,
    nombre: NOMBRE_ESTADO[paso],
    situacion: i < actual ? 'hecho' : i === actual ? 'actual' : 'pendiente',
    fecha: i <= actual ? fechaDelEstado(historial, paso) : null,
  }));
}

// Un cambio "vuelve atrás" si va a un estado anterior del camino (o sale de Descartado).
function posicion(estado: EstadoReclamo): number {
  return estado === 'DESCARTADO' ? PASOS.length : PASOS.indexOf(estado);
}

export function volvioAtras(anterior: EstadoReclamo | undefined, nuevo: EstadoReclamo): boolean {
  return anterior !== undefined && posicion(nuevo) < posicion(anterior);
}
