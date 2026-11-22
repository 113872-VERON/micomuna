import { Injectable, signal } from '@angular/core';

// Un aviso para la pantalla siguiente del panel (por ejemplo, "Guardamos los cambios de X" al volver a la lista).
// Se muestra una sola vez: quien lo muestra lo consume.
@Injectable({ providedIn: 'root' })
export class AvisoPanelService {
  private readonly pendiente = signal<string | null>(null);

  mostrar(mensaje: string): void {
    this.pendiente.set(mensaje);
  }

  consumir(): string | null {
    const mensaje = this.pendiente();
    this.pendiente.set(null);
    return mensaje;
  }
}
