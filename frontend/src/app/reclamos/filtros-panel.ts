import { ParamMap, Params } from '@angular/router';
import { EstadoReclamo } from './modelos';

// Los filtros del panel de reclamos. Viven en la URL (query params) para poder volver al mismo lugar.
export interface FiltrosPanel {
  estado: EstadoReclamo | null;
  tipo: string | null;
  agente: number | null;
  buscar: string;
}

export type VistaPanel = 'mapa' | 'lista';

// Cómo se ordena la lista (MC-27): por fecha (la más nueva primero) o por prioridad (Alta primero, sin prioridad al final).
export type OrdenPanel = 'fecha' | 'prioridad';

export interface EstadoDePantalla {
  filtros: FiltrosPanel;
  vista: VistaPanel;
  // En la URL solo va "prioridad"; fecha es lo predeterminado.
  orden: OrdenPanel;
  // Desde 0 (en la URL va desde 1 y se omite la primera).
  pagina: number;
}

const ESTADOS: readonly string[] = ['RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO'];
const TIPOS: readonly string[] = ['BACHE', 'LUZ', 'BASURA', 'ARBOL', 'AGUA', 'OTRO'];
export const MAX_BUSCAR = 100;

export const SIN_FILTROS: FiltrosPanel = { estado: null, tipo: null, agente: null, buscar: '' };

// La búsqueda que el servidor acepta: un número, o un texto de 2 a 100 caracteres. Otra cosa no se manda.
export function buscarValido(texto: string): string | null {
  const t = texto.trim();
  if (/^\d{1,9}$/.test(t)) {
    return t;
  }
  return t.length >= 2 && t.length <= MAX_BUSCAR ? t : null;
}

// Lee la URL. Lo que no es válido (un estado inventado, una página negativa) se ignora en lugar de romper.
export function leerEstado(params: ParamMap): EstadoDePantalla {
  const estado = params.get('estado');
  const tipo = params.get('tipo');
  const agente = params.get('agente');
  const pagina = params.get('pagina');
  return {
    filtros: {
      estado: estado && ESTADOS.includes(estado) ? (estado as EstadoReclamo) : null,
      tipo: tipo && TIPOS.includes(tipo) ? tipo : null,
      agente: agente && /^\d{1,15}$/.test(agente) ? Number(agente) : null,
      buscar: (params.get('buscar') ?? '').slice(0, MAX_BUSCAR),
    },
    vista: params.get('vista') === 'lista' ? 'lista' : 'mapa',
    orden: params.get('orden') === 'prioridad' ? 'prioridad' : 'fecha',
    pagina: pagina && /^\d{1,4}$/.test(pagina) && Number(pagina) >= 1 ? Number(pagina) - 1 : 0,
  };
}

// Los query params de la URL: solo lo que se usa (null borra el parámetro al navegar con "merge").
export function aQueryParams(e: EstadoDePantalla): Params {
  return {
    estado: e.filtros.estado,
    tipo: e.filtros.tipo,
    agente: e.filtros.agente,
    buscar: e.filtros.buscar.trim() || null,
    vista: e.vista === 'lista' ? 'lista' : null,
    orden: e.orden === 'prioridad' ? 'prioridad' : null,
    pagina: e.pagina > 0 ? e.pagina + 1 : null,
  };
}

// Los parámetros que viajan a la API (sin la búsqueda inválida y sin valores vacíos).
export function aParamsDeApi(f: FiltrosPanel, incluirEstado = true): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (incluirEstado && f.estado) {
    params['estado'] = f.estado;
  }
  if (f.tipo) {
    params['tipo'] = f.tipo;
  }
  if (f.agente !== null) {
    params['agente'] = f.agente;
  }
  const buscar = buscarValido(f.buscar);
  if (buscar) {
    params['buscar'] = buscar;
  }
  return params;
}

export function hayFiltros(f: FiltrosPanel): boolean {
  return !!(f.estado || f.tipo || f.agente !== null || f.buscar.trim());
}
