import { AbstractControl } from '@angular/forms';

export const CLAVE_MIN = 8;
export const CLAVE_MAX = 72;

export function mensajeCorreo(control: AbstractControl): string {
  return control.hasError('required')
    ? 'Escribí tu correo.'
    : 'Revisá el correo: tiene que ser como ejemplo@correo.com.';
}

export function mensajeClaveNueva(control: AbstractControl): string {
  return control.hasError('required')
    ? 'Elegí una contraseña.'
    : `La contraseña tiene que tener entre ${CLAVE_MIN} y ${CLAVE_MAX} caracteres.`;
}

export function limpiarCorreo(correo: string): string {
  return correo.trim().toLowerCase();
}
