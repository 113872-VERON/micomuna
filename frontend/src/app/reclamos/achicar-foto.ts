import { InjectionToken } from '@angular/core';

// La foto se achica en el celular antes de subirla: lado más largo de 1600 px y JPEG al 80 %.
// Así pesa entre 300 y 600 KB, se sube rápido con datos móviles y pierde los datos ocultos
// de la cámara (por ejemplo dónde se sacó). El servidor igual la vuelve a revisar.
export const LADO_MAXIMO = 1600;
export const CALIDAD_JPEG = 0.8;

export type AchicarFoto = (archivo: Blob) => Promise<Blob>;

// Inyectable para que las pruebas no dependan del canvas del navegador.
export const ACHICAR_FOTO = new InjectionToken<AchicarFoto>('ACHICAR_FOTO', {
  providedIn: 'root',
  factory: () => achicarFoto,
});

// Medidas finales: nunca agranda, mantiene la proporción y redondea a píxeles enteros.
export function medidasAchicadas(ancho: number, alto: number, maximo = LADO_MAXIMO): { ancho: number; alto: number } {
  if (!(ancho > 0) || !(alto > 0)) {
    throw new Error('La imagen no tiene tamaño');
  }
  const escala = Math.min(1, maximo / Math.max(ancho, alto));
  return { ancho: Math.max(1, Math.round(ancho * escala)), alto: Math.max(1, Math.round(alto * escala)) };
}

interface ImagenLeida {
  fuente: CanvasImageSource;
  ancho: number;
  alto: number;
  liberar: () => void;
}

export async function achicarFoto(archivo: Blob): Promise<Blob> {
  // Algunos celulares no informan el tipo: en ese caso decide el navegador al leerla.
  if (archivo.type && !archivo.type.startsWith('image/')) {
    throw new Error('El archivo no es una imagen');
  }
  const imagen = await leer(archivo);
  try {
    const { ancho, alto } = medidasAchicadas(imagen.ancho, imagen.alto);
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const contexto = lienzo.getContext('2d');
    if (!contexto) {
      throw new Error('El navegador no puede procesar la imagen');
    }
    // Fondo blanco: un PNG con transparencia quedaría negro en JPEG.
    contexto.fillStyle = '#fff';
    contexto.fillRect(0, 0, ancho, alto);
    contexto.imageSmoothingQuality = 'high';
    contexto.drawImage(imagen.fuente, 0, 0, ancho, alto);
    return await aJpeg(lienzo);
  } finally {
    imagen.liberar();
  }
}

// createImageBitmap con 'from-image' respeta la orientación que guardó la cámara (la foto no queda acostada).
// Si el navegador no lo tiene o no acepta la opción, se lee con <img>, que también la respeta.
async function leer(archivo: Blob): Promise<ImagenLeida> {
  if (typeof createImageBitmap === 'function') {
    try {
      const mapa = await createImageBitmap(archivo, { imageOrientation: 'from-image' });
      return { fuente: mapa, ancho: mapa.width, alto: mapa.height, liberar: () => mapa.close() };
    } catch {
      // se prueba con <img>
    }
  }
  return leerConImg(archivo);
}

function leerConImg(archivo: Blob): Promise<ImagenLeida> {
  return new Promise((resolver, rechazar) => {
    const url = URL.createObjectURL(archivo);
    const img = new Image();
    img.onload = () =>
      resolver({ fuente: img, ancho: img.naturalWidth, alto: img.naturalHeight, liberar: () => URL.revokeObjectURL(url) });
    img.onerror = () => {
      URL.revokeObjectURL(url);
      rechazar(new Error('No se pudo leer la imagen'));
    };
    img.src = url;
  });
}

function aJpeg(lienzo: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolver, rechazar) =>
    lienzo.toBlob(
      (blob) => (blob ? resolver(blob) : rechazar(new Error('No se pudo armar el JPEG'))),
      'image/jpeg',
      CALIDAD_JPEG,
    ),
  );
}
