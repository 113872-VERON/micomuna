import { ValidatorFn } from '@angular/forms';

export const MENSAJE_TELEFONO = 'Escribí el número con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456).';
export const AYUDA_TELEFONO = 'Con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456). Podés escribir espacios, guiones o paréntesis.';

// Se aceptan espacios, guiones, paréntesis y "+" al escribir; lo que se manda son solo los dígitos.
export function soloDigitos(texto: string): string {
  return texto.replace(/[\s+()-]/g, '');
}

// Después de sacar espacios, guiones, paréntesis y "+", tiene que ser un número argentino con código de país: 54 y 10 u 11 dígitos más.
// Vacío vale: el teléfono y el WhatsApp son opcionales.
export function telefonoValido(texto: string): boolean {
  const t = texto.trim();
  if (!t) {
    return true;
  }
  return /^[0-9 +()-]+$/.test(t) && /^54\d{10,11}$/.test(soloDigitos(t));
}

export const validarTelefono: ValidatorFn = (control) => (telefonoValido(String(control.value ?? '')) ? null : { telefono: true });
