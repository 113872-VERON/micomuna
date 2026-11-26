import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UsuarioDto } from './modelos';

// "Mi cuenta": lo que cualquier usuario con sesión puede hacer sobre sí mismo. Quién es sale del token.
@Injectable({ providedIn: 'root' })
export class CuentaService {
  private readonly http = inject(HttpClient);

  misDatos(): Observable<UsuarioDto> {
    return this.http.get<UsuarioDto>('/api/auth/mis-datos');
  }

  cambiarNombre(nombre: string): Observable<UsuarioDto> {
    return this.http.put<UsuarioDto>('/api/auth/mis-datos', { nombre });
  }

  cambiarClave(claveActual: string, claveNueva: string): Observable<void> {
    return this.http.post<void>('/api/auth/cambiar-clave', { claveActual, claveNueva });
  }
}
