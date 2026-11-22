export const MAX_LIMITE_BYTES = 1024 * 1024;

export type LecturaLimite = { geojson: object; error?: undefined } | { error: string; geojson?: undefined };

// Lee el archivo en el navegador y revisa lo básico, sin llamar al backend: que pese hasta 1 MB y que sea
// un JSON (un objeto). Que sea un polígono válido lo decide el servidor, que responde con su propio mensaje.
export async function leerLimite(archivo: File): Promise<LecturaLimite> {
  if (archivo.size > MAX_LIMITE_BYTES) {
    return { error: 'El archivo del límite pesa más de 1 MB. Elegí uno más liviano.' };
  }
  let texto: string;
  try {
    texto = await archivo.text();
  } catch {
    return { error: 'No pudimos leer el archivo. Probá de nuevo.' };
  }
  try {
    const contenido: unknown = JSON.parse(texto);
    if (contenido === null || typeof contenido !== 'object' || Array.isArray(contenido)) {
      throw new Error('no es un objeto');
    }
    return { geojson: contenido };
  } catch {
    return { error: 'El archivo no es un GeoJSON: tiene que ser un archivo de texto en formato JSON.' };
  }
}
