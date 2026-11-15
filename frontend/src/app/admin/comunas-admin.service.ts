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

  reenviarInvitacion(id: number): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/administrador/reenviar-invitacion`, null);
  }
}
