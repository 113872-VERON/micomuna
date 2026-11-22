// Modelos de los reclamos según docs/api/openapi.yaml (TipoReclamoDto, CrearReclamoForm, ReclamoCreadoDto).

export type CodigoTipo = 'BACHE' | 'LUZ' | 'BASURA' | 'ARBOL' | 'AGUA' | 'OTRO';

export interface TipoReclamoDto {
  codigo: CodigoTipo;
  nombre: string;
  consejoFoto: string;
}

export interface ReclamoCreadoDto {
  id: number;
  numero: number;
  tipo: string;
  estado: 'RECIBIDO';
  cantidadFotos: number;
  creadoEn: string;
}

// Lo que se toma del GPS del celular. La precisión (en metros) no viaja al servidor.
export interface Coordenadas {
  latitud: number;
  longitud: number;
  precision?: number;
}

// Lo que arma la pantalla para enviar. La ubicación va completa (latitud y longitud juntas) o no va.
export interface NuevoReclamo {
  tipo: CodigoTipo;
  descripcion: string;
  referencia: string;
  // Dirección aproximada que calculó el backend a partir del punto (solo informativa).
  direccion?: string | null;
  // Obligatoria para el servidor: el punto del mapa o del GPS.
  ubicacion: Coordenadas | null;
  fotos: readonly Blob[];
}

// Límites del contrato.
export const MAX_FOTOS = 3;
export const MAX_DESCRIPCION = 500;
export const MAX_REFERENCIA = 200;
export const MAX_DIRECCION = 300;
