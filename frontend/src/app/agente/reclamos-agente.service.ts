import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { GrupoAgente, MAX_NOTA, PaginaReclamosAgenteDto, ReclamoAgenteDetalleDto } from './modelos-agente';

const BASE = '/api/agente/reclamos';

// Los reclamos asignados al agente. La comuna y el agente salen siempre del token. La ubicación del agente
// nunca viaja: la distancia se calcula en el teléfono.
@Injectable({ providedIn: 'root' })
export class ReclamosAgenteService {
  private readonly http = inject(HttpClient);

  listar(grupo: GrupoAgente, pagina: number, tamanio: number): Observable<PaginaReclamosAgenteDto> {
    return this.http.get<PaginaReclamosAgenteDto>(BASE, { params: { grupo, pagina, tamanio } });
  }

  detalle(numero: number): Observable<ReclamoAgenteDetalleDto> {
    return this.http.get<ReclamoAgenteDetalleDto>(`${BASE}/${numero}`);
  }

  // La imagen pide el token: se baja como blob y la pantalla arma la URL de objeto.
  foto(numero: number, idFoto: number): Observable<Blob> {
    return this.http.get(`${BASE}/${numero}/fotos/${idFoto}`, { responseType: 'blob' });
  }

  empezar(numero: number): Observable<ReclamoAgenteDetalleDto> {
    return this.http.post<ReclamoAgenteDetalleDto>(`${BASE}/${numero}/empezar`, null);
  }

  // Nota y foto son opcionales: sin ninguna de las dos se manda el formulario vacío.
  resolver(numero: number, nota: string, foto: Blob | null): Observable<ReclamoAgenteDetalleDto> {
    const datos = new FormData();
    const texto = nota.trim().slice(0, MAX_NOTA);
    if (texto) {
      datos.append('nota', texto);
    }
    if (foto) {
      datos.append('foto', foto, 'trabajo.jpg');
    }
    return this.http.post<ReclamoAgenteDetalleDto>(`${BASE}/${numero}/resolver`, datos);
  }
}
