// Modelos de "Mis reclamos" del agente (MC-20), según docs/api/openapi.yaml (etiqueta "Agente").
import { EstadoReclamo, FotoReclamoDto } from '../reclamos/modelos';
import { PrioridadReclamoDto } from '../reclamos/prioridad';

export type GrupoAgente = 'PARA_HACER' | 'RESUELTOS';

// Una tarjeta de la lista. Trae latitud y longitud para que el teléfono calcule la distancia.
export interface ReclamoAgenteDto {
  numero: number;
  tipo: string;
  tipoNombre: string;
  estado: EstadoReclamo;
  direccion: string | null;
  referencia: string | null;
  latitud: number | null;
  longitud: number | null;
  creadoEn: string;
  estadoCambiadoEn: string;
  // MC-27: la prioridad efectiva, sin el detalle de quién la fijó.
  prioridad?: PrioridadReclamoDto;
}

export interface PaginaReclamosAgenteDto {
  reclamos: ReclamoAgenteDto[];
  pagina: number;
  tamanio: number;
  total: number;
}

// Sin nombre ni datos del vecino.
export interface ReclamoAgenteDetalleDto {
  numero: number;
  tipo: string;
  tipoNombre: string;
  estado: EstadoReclamo;
  descripcion: string;
  referencia: string | null;
  direccion: string | null;
  latitud: number | null;
  longitud: number | null;
  creadoEn: string;
  fotos: FotoReclamoDto[];
  trabajoRealizado: string | null;
  prioridad?: PrioridadReclamoDto;
}

export const MAX_NOTA = 1000;
