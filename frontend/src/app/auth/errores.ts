import { HttpErrorResponse } from '@angular/common/http';

// El backend responde con ProblemDetail: `detail` en español y, en errores de validación, `errores` por campo.
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No pudimos conectarnos. Revisá tu conexión y probá de nuevo.';
    }
    const porCampo = error.error?.errores;
    if (porCampo && typeof porCampo === 'object') {
      const primero = Object.values(porCampo).find((m) => typeof m === 'string');
      if (primero) {
        return primero as string;
      }
    }
    const detalle = error.error?.detail;
    if (typeof detalle === 'string' && detalle) {
      return detalle;
    }
  }
  return 'Ocurrió un error inesperado. Probá de nuevo en unos minutos.';
}
