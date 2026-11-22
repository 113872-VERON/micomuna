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

// ---- Mis reclamos (MC-17), según MiReclamoResumenDto, MiReclamoDetalleDto y compañía ----

export type EstadoReclamo = 'RECIBIDO' | 'ASIGNADO' | 'EN_REPARACION' | 'RESUELTO' | 'DESCARTADO';

export type MotivoDescarte = 'DUPLICADO' | 'NO_ES_DE_LA_COMUNA' | 'NO_SE_PUEDE_ATENDER' | 'OTRO';

export interface MiReclamoResumenDto {
  numero: number;
  tipo: string;
  tipoNombre: string;
  estado: EstadoReclamo;
  descripcion: string;
  direccion: string | null;
  creadoEn: string;
  estadoCambiadoEn: string;
  idFotoPrincipal: number | null;
}

export interface PaginaMisReclamosDto {
  reclamos: MiReclamoResumenDto[];
  pagina: number;
  tamanio: number;
  total: number;
}

export interface FotoReclamoDto {
  id: number;
  origen: 'VECINO' | 'TRABAJO';
  orden: number;
}

export interface CambioDeEstadoDto {
  estado: EstadoReclamo;
  fecha: string;
}

export interface DescarteDto {
  motivo: MotivoDescarte;
  observacion: string | null;
  numeroOriginal: number | null;
  estadoOriginal: EstadoReclamo | null;
}

export interface MiReclamoDetalleDto {
  numero: number;
  tipo: string;
  tipoNombre: string;
  estado: EstadoReclamo;
  descripcion: string;
  referencia: string | null;
  direccion: string | null;
  latitud: number;
  longitud: number;
  creadoEn: string;
  fotos: FotoReclamoDto[];
  historial: CambioDeEstadoDto[];
  descarte: DescarteDto | null;
  trabajoRealizado: string | null;
}
