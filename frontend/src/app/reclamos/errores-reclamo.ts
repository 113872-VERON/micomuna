import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from '../auth/errores';

// Para los errores que conoce, la pantalla usa SIEMPRE su propio texto, que dice qué hacer
// (el `detail` del servidor está escrito para cualquier cliente y no sabe qué botones hay acá).
const POR_CODIGO: Record<string, string> = {
  TIPO_INVALIDO: 'Ese tipo de problema no existe. Tocá "Cambiar" en el tipo y elegí otro.',
  UBICACION_REQUERIDA: 'Falta dónde está el problema. Tocá "Cambiar" en el lugar y usá tu ubicación o escribí la dirección.',
  FOTO_INVALIDA: 'No pudimos usar una de las fotos. Tocá "Cambiar" en las fotos, quitala y sacá otra.',
  ARCHIVO_MUY_GRANDE: 'Las fotos pesan demasiado. Tocá "Cambiar" en las fotos y quitá alguna.',
  CONFLICTO: 'Otra persona estaba reportando al mismo tiempo. Tocá "Enviar reclamo" de nuevo.',
  DEMASIADOS_RECLAMOS: 'Ya enviaste muchos reclamos en la última hora. Esperá un rato y tocá "Enviar reclamo" de nuevo. Si es urgente, llamá a la comuna.',
  OCUPADO: 'En este momento se están recibiendo muchas fotos. Esperá unos segundos y tocá "Enviar reclamo" de nuevo.',
};

interface Problema {
  codigo?: unknown;
  detail?: unknown;
  errores?: unknown;
}

export function mensajeDelEnvio(error: unknown): string {
  if (!(error instanceof HttpErrorResponse) || error.status === 0) {
    return mensajeDeError(error);
  }
  const cuerpo = (error.error ?? {}) as Problema;
  const codigo = typeof cuerpo.codigo === 'string' ? cuerpo.codigo : '';

  // El servidor dice qué foto falló ("La foto 2 ..."): se aprovecha el número, el resto es nuestro.
  if (codigo === 'FOTO_INVALIDA' && typeof cuerpo.detail === 'string') {
    const numero = /\bfoto (\d+)\b/i.exec(cuerpo.detail)?.[1];
    if (numero) {
      return `No pudimos usar la foto ${numero}. Tocá "Cambiar" en las fotos, quitala y sacá otra.`;
    }
  }
  if (POR_CODIGO[codigo]) {
    return POR_CODIGO[codigo];
  }
  // VALIDACION: el motivo de cada campo, todos juntos.
  if (cuerpo.errores && typeof cuerpo.errores === 'object') {
    const motivos = Object.values(cuerpo.errores as Record<string, unknown>).filter(
      (m): m is string => typeof m === 'string' && m.trim() !== '',
    );
    if (motivos.length > 0) {
      return [...new Set(motivos.map((m) => conPunto(m)))].join(' ');
    }
  }
  if (codigo === 'VALIDACION' && !(typeof cuerpo.detail === 'string' && cuerpo.detail)) {
    return 'Revisá los datos del reclamo y probá de nuevo.';
  }
  return mensajeDeError(error);
}

function conPunto(texto: string): string {
  const limpio = texto.trim();
  return /[.!?…]$/.test(limpio) ? limpio : `${limpio}.`;
}
