export const TIPOS_LOGO = ['image/png', 'image/jpeg'];
export const MAX_LOGO_BYTES = 512 * 1024;

// Revisa en el celular, sin llamar al backend, que el logo sea PNG o JPG y no pese de más.
// Devuelve el mensaje para mostrar, o null si el archivo sirve.
export function validarLogo(archivo: File): string | null {
  if (!TIPOS_LOGO.includes(archivo.type)) {
    return 'El logo tiene que ser una imagen PNG o JPG.';
  }
  if (archivo.size > MAX_LOGO_BYTES) {
    return 'El logo pesa más de 512 KB. Elegí una imagen más liviana.';
  }
  return null;
}
