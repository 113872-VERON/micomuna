import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ComunaAdminDto, ComunaCreadaDto, NuevaComunaRequest } from '../auth/modelos';

const BASE = '/api/admin/comunas';

// Panel del superadmin: las comunas nunca se borran, se desactivan.
@Injectable({ providedIn: 'root' })
export class ComunasAdminService {
  private readonly http = inject(HttpClient);

  listar(): Observable<ComunaAdminDto[]> {
    return this.http.get<ComunaAdminDto[]>(BASE);
  }

  crear(pedido: NuevaComunaRequest): Observable<ComunaCreadaDto> {
    return this.http.post<ComunaCreadaDto>(BASE, pedido);
  }

  cambiarEstado(id: number, activa: boolean): Observable<ComunaAdminDto> {
    return this.http.patch<ComunaAdminDto>(`${BASE}/${id}`, { activa });
  }

  // Sin Content-Type a mano: el navegador arma el multipart con su boundary.
  subirLogo(id: number, archivo: File): Observable<ComunaAdminDto> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.put<ComunaAdminDto>(`${BASE}/${id}/logo`, datos);
  }

  // El cuerpo es el GeoJSON tal cual (JSON, hasta 1 MB).
  cambiarLimite(id: number, geojson: unknown): Observable<ComunaAdminDto> {
    return this.http.put<ComunaAdminDto>(`${BASE}/${id}/limite`, geojson);
  }

  reenviarInvitacion(id: number): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/administrador/reenviar-invitacion`, null);
  }
}
