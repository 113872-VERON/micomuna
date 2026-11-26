// Modelos del panel de reclamos del personal (MC-18), según docs/api/openapi.yaml ("Panel de reclamos").
import { EstadoReclamo, FotoReclamoDto, MotivoDescarte } from './modelos';

export interface AgenteDto {
  id: number;
  nombre: string;
}

// Lo que devuelve /api/panel/agentes: el agente y cuántos reclamos tiene en curso (asignados o en reparación).
export interface AgenteConCargaDto extends AgenteDto {
  enCurso: number;
}

export interface AsignarAgenteRequest {
  idAgente: number;
}

// "Marcos Díaz, 3 en curso"
// Con `actualId`, el agente que ya tiene el reclamo se marca: "Marcos Díaz (actual), 1 en curso".
export const textoDeAgenteConCarga = (a: AgenteConCargaDto, actualId?: number | null): string =>
  `${a.nombre}${a.id === actualId ? ' (actual)' : ''}, ${a.enCurso} en curso`;

export interface ReclamoPanelDto {
  numero: number;
  tipo: string;
  tipoNombre: string;
  estado: EstadoReclamo;
  descripcion: string;
  direccion: string | null;
  referencia: string | null;
  creadoEn: string;
  agente: AgenteDto | null;
  numeroOriginal: number | null;
  cantidadDuplicados: number;
}

export interface PaginaReclamosPanelDto {
  reclamos: ReclamoPanelDto[];
  pagina: number;
  tamanio: number;
  total: number;
}

export interface PuntoReclamoDto {
  numero: number;
  estado: EstadoReclamo;
  tipo: string;
  latitud: number;
  longitud: number;
}

export interface MapaReclamosDto {
  puntos: PuntoReclamoDto[];
  recortado: boolean;
}

export interface ResumenPanelDto {
  porEstado: Record<EstadoReclamo, number>;
  total: number;
  sinAsignar: number;
  sinAsignarMasAntiguo: string | null;
}

export interface CambioDeEstadoPanelDto {
  estadoAnterior: EstadoReclamo | null;
  estado: EstadoReclamo;
  fecha: string;
  usuarioNombre: string | null;
  observacion: string | null;
  // Desde MC-19: el agente que quedó asignado con este cambio y, si fue una reasignación, el que tenía antes.
  agente?: AgenteDto | null;
  agenteAnterior?: AgenteDto | null;
}

export interface DescartePanelDto {
  motivo: MotivoDescarte;
  observacion: string | null;
  numeroOriginal: number | null;
}

export interface ReclamoPanelDetalleDto {
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
  vecinoNombre: string;
  agente: AgenteDto | null;
  fotos: FotoReclamoDto[];
  historial: CambioDeEstadoPanelDto[];
  descarte: DescartePanelDto | null;
  trabajoRealizado: string | null;
  duplicados: number[];
  sugerenciaIa: SugerenciaIaDto;
  posiblesDuplicados: PosiblesDuplicadosDto;
}

// Aviso de posibles duplicados cercanos (MC-26, docs/api/openapi.yaml). Siempre viene; es una regla del servidor, sin IA.
export interface PosiblesDuplicadosDto {
  candidatos: PosibleDuplicadoDto[];
  marcadoNuevoPor?: string | null;
  marcadoNuevoEn?: string | null;
}

export interface PosibleDuplicadoDto {
  numero: number;
  estado: EstadoReclamo;
  distanciaMetros: number;
  creadoEn: string;
  direccion?: string | null;
  descripcion?: string;
}

// La sugerencia de tipo de la IA (docs/api/openapi.yaml, SugerenciaIaDto). La IA solo sugiere: decide el personal.
export interface SugerenciaIaDto {
  estado: 'HECHA' | 'NO_DISPONIBLE' | 'SIN_ANALIZAR';
  tipoSugerido?: string | null;
  tipoSugeridoNombre?: string | null;
  justificacion?: string | null;
  coincide: boolean;
  decision?: 'ACEPTADA' | 'CORREGIDA' | null;
  tipoDecidido?: string | null;
  tipoDecididoNombre?: string | null;
  decididaPor?: string | null;
  decididaEn?: string | null;
}

export type EstadoDeCambio = Exclude<EstadoReclamo, 'DESCARTADO'>;

export interface CambioDeEstadoEnLoteRequest {
  numeros: number[];
  estado: EstadoDeCambio;
  idAgente?: number | null;
}

export interface DescarteEnLoteRequest {
  numeros: number[];
  motivo: MotivoDescarte;
  observacion?: string | null;
  numeroOriginal?: number | null;
}

export interface OmitidoDto {
  numero: number;
  // El servidor puede sumar códigos nuevos: se muestra el `motivo` sea cual sea el código.
  codigo: string;
  motivo: string;
}

export interface ResultadoEnLoteDto {
  aplicados: number[];
  omitidos: OmitidoDto[];
}

// Lo mínimo que necesitan las ventanas de cambiar estado y descartar de cada reclamo.
export interface ReclamoElegido {
  numero: number;
  estado: EstadoReclamo;
}

export const MAX_OBSERVACION = 300;
export const ESTADOS_CERRADOS: readonly EstadoReclamo[] = ['RESUELTO', 'DESCARTADO'];
// Un reclamo en reparación tampoco se descarta (regla del servidor): primero se resuelve.
export const ESTADOS_NO_DESCARTABLES: readonly EstadoReclamo[] = ['EN_REPARACION', 'RESUELTO', 'DESCARTADO'];
export const sePuedeDescartar = (estado: EstadoReclamo): boolean => !ESTADOS_NO_DESCARTABLES.includes(estado);
export const ESTADOS_DE_CAMBIO: readonly EstadoDeCambio[] = ['RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO'];

// Métricas simples del panel (MC-21), según GET /api/panel/metricas.
export interface CantidadPorTipoDto {
  tipo: string;
  tipoNombre: string;
  cantidad: number;
}

export interface CantidadPorSemanaDto {
  desde: string;
  hasta: string;
  cantidad: number;
}

export interface MetricasDto {
  desde: string;
  hasta: string;
  dias: number;
  reclamosDelPeriodo: number;
  resueltos: number;
  tiempoPromedioResolucionHoras: number | null;
  sinAsignar: number;
  porTipo: CantidadPorTipoDto[];
  porSemana: CantidadPorSemanaDto[];
}
