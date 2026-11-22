import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Coordenadas } from './modelos';

export interface DireccionDto {
  direccion?: string;
}

const MAX_DIRECCION = 300;

// Dirección aproximada de un punto. La pide el backend (el celular no habla con servicios externos).
// Es solo informativa: 204, 429, un error o la falta de red dan "sin dirección" y nunca bloquean nada.
@Injectable({ providedIn: 'root' })
export class DireccionService {
  private readonly http = inject(HttpClient);

  async de(punto: Coordenadas): Promise<string | null> {
    try {
      const respuesta = await firstValueFrom(
        this.http.get<DireccionDto | null>('/api/ubicacion/direccion', {
          params: { latitud: punto.latitud.toFixed(6), longitud: punto.longitud.toFixed(6) },
        }),
      );
      const texto = respuesta?.direccion?.trim();
      return texto ? texto.slice(0, MAX_DIRECCION) : null;
    } catch {
      return null;
    }
  }
}
