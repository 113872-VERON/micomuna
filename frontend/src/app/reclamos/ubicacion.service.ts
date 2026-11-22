import { Injectable } from '@angular/core';
import { Coordenadas } from './modelos';

// Por qué no se pudo tomar la ubicación: el mensaje cambia si el teléfono no dio permiso.
export type MotivoSinUbicacion = 'permiso' | 'no-disponible' | 'otro';

export class SinUbicacion extends Error {
  constructor(readonly motivo: MotivoSinUbicacion) {
    super(`Sin ubicación: ${motivo}`);
  }
}

// Código del navegador para "el usuario no dio permiso" (GeolocationPositionError.PERMISSION_DENIED).
const PERMISO_DENEGADO = 1;

// Pide una sola vez la ubicación actual del celular. No la sigue ni la guarda.
// Ojo: el navegador solo la da en HTTPS o en localhost.
@Injectable({ providedIn: 'root' })
export class UbicacionService {
  obtener(): Promise<Coordenadas> {
    const geo = typeof navigator === 'undefined' ? undefined : navigator.geolocation;
    if (!geo) {
      return Promise.reject(new SinUbicacion('no-disponible'));
    }
    return new Promise((resolver, rechazar) =>
      geo.getCurrentPosition(
        ({ coords }) => resolver({ latitud: coords.latitude, longitud: coords.longitude, precision: coords.accuracy }),
        (error) => rechazar(new SinUbicacion(error.code === PERMISO_DENEGADO ? 'permiso' : 'otro')),
        { enableHighAccuracy: true, timeout: 20000, maximumAge: 30000 },
      ),
    );
  }
}
