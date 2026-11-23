import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from '../auth/errores';

// Los errores de empezar y resolver, con el texto de esta pantalla (dice qué botón tocar).
const POR_CODIGO: Record<string, string> = {
  FOTO_INVALIDA: 'No pudimos usar esa foto. Sacá otra o elegí una distinta, o resolvé sin foto.',
  ARCHIVO_MUY_GRANDE: 'La foto pesa demasiado. Sacá otra o resolvé sin foto.',
  OCUPADO: 'En este momento se están recibiendo muchas fotos. Esperá unos segundos y tocá "Resuelto" de nuevo.',
  TRANSICION_INVALIDA: 'Este reclamo ya cambió de estado y no se puede hacer eso ahora. Actualizamos la pantalla con lo último.',
  CONFLICTO: 'Otra persona está cambiando este reclamo en este momento. Esperá un instante y probá de nuevo.',
  NO_ENCONTRADO: 'Este reclamo ya no está asignado a vos.',
};

export function codigoDeError(error: unknown): string {
  const codigo = error instanceof HttpErrorResponse ? (error.error as { codigo?: unknown } | null)?.codigo : undefined;
  return typeof codigo === 'string' ? codigo : '';
}

export function mensajeDeAgente(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 503 && !codigoDeError(error)) {
    return POR_CODIGO['OCUPADO'];
  }
  return POR_CODIGO[codigoDeError(error)] ?? mensajeDeError(error);
}
