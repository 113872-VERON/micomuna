// Cada paso tiene su propia dirección (/<slug>/reportar/<segmento>), así el botón "atrás"
// del celular vuelve un paso en lugar de salir del recorrido.
export type Paso = 1 | 2 | 3 | 4 | 'listo';

const PASOS: readonly Paso[] = [1, 2, 3, 4, 'listo'];

export const SEGMENTO_DE_PASO: Record<Paso, string> = {
  1: 'que-paso',
  2: 'fotos',
  3: 'donde',
  4: 'revisar',
  listo: 'listo',
};

export const PASO_INICIAL = SEGMENTO_DE_PASO[1];

// Una dirección que no es de ningún paso devuelve null (la pantalla manda al primero).
export function pasoDeSegmento(segmento: string | null | undefined): Paso | null {
  return PASOS.find((paso) => SEGMENTO_DE_PASO[paso] === segmento) ?? null;
}
