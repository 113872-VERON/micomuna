// La prioridad de un reclamo (MC-27, docs/api/openapi.yaml). La IA solo sugiere: lo que vale es lo que fijó el personal.
export type ValorPrioridad = 'ALTA' | 'MEDIA' | 'BAJA';
export type OrigenPrioridad = 'PERSONAL' | 'IA' | 'NINGUNA';

// La prioridad efectiva: la del personal si la fijó; si no, la sugerida por la IA; si no, ninguna.
export interface PrioridadReclamoDto {
  valor: ValorPrioridad | null;
  origen: OrigenPrioridad;
}

// Solo el personal ve quién la fijó y cuándo (origen PERSONAL).
export interface PrioridadPanelDto extends PrioridadReclamoDto {
  fijadaPor?: string | null;
  fijadaEn?: string | null;
}

export const VALORES_DE_PRIORIDAD: readonly ValorPrioridad[] = ['ALTA', 'MEDIA', 'BAJA'];
export const SIN_PRIORIDAD: PrioridadReclamoDto = { valor: null, origen: 'NINGUNA' };

export const NOMBRE_PRIORIDAD: Record<ValorPrioridad, string> = { ALTA: 'Alta', MEDIA: 'Media', BAJA: 'Baja' };

export const nombreDePrioridad = (valor: ValorPrioridad | null | undefined): string => (valor ? NOMBRE_PRIORIDAD[valor] : 'Sin prioridad');

// Para ordenar: Alta primero y, al final, sin prioridad.
export const rangoDePrioridad = (p: PrioridadReclamoDto | null | undefined): number => {
  switch (p?.valor) {
    case 'ALTA':
      return 0;
    case 'MEDIA':
      return 1;
    case 'BAJA':
      return 2;
    default:
      return 3;
  }
};

export interface CambiarPrioridadRequest {
  prioridad: ValorPrioridad;
}
