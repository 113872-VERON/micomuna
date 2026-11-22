import { Injectable, signal } from '@angular/core';

export type EstadoMapa = 'listo' | 'sin-mapa';

// Dice si hay mapa. Los mapas son libres (Leaflet, sin clave): no hay nada que pedir ni cargar de afuera.
// La pantalla pasa a 'sin-mapa' cuando el componente del mapa avisa que no pudo mostrarse
// (Leaflet falló o no llegó ninguna tesela) y sigue con "Usar mi ubicación".
@Injectable({ providedIn: 'root' })
export class MapasService {
  readonly estado = signal<EstadoMapa>('listo');

  marcarFalla(): void {
    this.estado.set('sin-mapa');
  }

  // Al volver a abrir el paso se vuelve a intentar mostrar el mapa (puede haber vuelto la conexión).
  reintentar(): void {
    this.estado.set('listo');
  }
}
