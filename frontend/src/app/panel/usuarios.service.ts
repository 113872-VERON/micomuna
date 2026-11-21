import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { ActualizarUsuarioRequest, CrearUsuarioRequest, UsuarioComunaDto, UsuarioCreadoDto } from '../auth/modelos';

const BASE = '/api/usuarios';

// Resultado de corregir el correo de un pendiente: el correo nuevo ya quedó guardado aunque el envío falle.
export interface CorreoCorregido {
  usuario: UsuarioComunaDto;
  invitacionEnviada: boolean;
  errorEnvio: unknown;
}

// Usuarios de la comuna del administrador. La comuna sale del token, nunca de la URL:
// el backend decide a qué comuna pertenece cada pedido.
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);

  listar(): Observable<UsuarioComunaDto[]> {
    return this.http.get<UsuarioComunaDto[]>(BASE);
  }

  crear(pedido: CrearUsuarioRequest): Observable<UsuarioCreadoDto> {
    return this.http.post<UsuarioCreadoDto>(BASE, pedido);
  }

  actualizar(id: number, cambios: ActualizarUsuarioRequest): Observable<UsuarioComunaDto> {
    return this.http.patch<UsuarioComunaDto>(`${BASE}/${id}`, cambios);
  }

  reenviarInvitacion(id: number): Observable<void> {
    return this.http.post<void>(`${BASE}/${id}/reenviar-invitacion`, null);
  }

  // Corregir el correo deja sin efecto la invitación anterior, así que se reenvía enseguida.
  // Si falla el cambio, el error sigue de largo; si falla solo el envío, se informa en el resultado.
  corregirCorreoYReenviar(id: number, correo: string): Observable<CorreoCorregido> {
    return this.actualizar(id, { correo }).pipe(
      switchMap((usuario) =>
        this.reenviarInvitacion(id).pipe(
          map((): CorreoCorregido => ({ usuario, invitacionEnviada: true, errorEnvio: null })),
          catchError((errorEnvio: unknown) => of<CorreoCorregido>({ usuario, invitacionEnviada: false, errorEnvio })),
        ),
      ),
    );
  }
}
