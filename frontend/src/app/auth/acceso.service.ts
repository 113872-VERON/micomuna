import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthRespuesta } from './modelos';

// Pedidos públicos de una comuna: entrar, registrarse y recuperar la contraseña.
@Injectable({ providedIn: 'root' })
export class AccesoService {
  private readonly http = inject(HttpClient);

  iniciarSesion(slug: string, correo: string, clave: string): Observable<AuthRespuesta> {
    return this.http.post<AuthRespuesta>(this.url(slug, 'login'), { correo, clave });
  }

  registrar(slug: string, nombre: string, correo: string, clave: string): Observable<AuthRespuesta> {
    return this.http.post<AuthRespuesta>(this.url(slug, 'registro'), { nombre, correo, clave });
  }

  recuperar(slug: string, correo: string): Observable<void> {
    return this.http.post<void>(this.url(slug, 'recuperar-clave'), { correo });
  }

  restablecer(slug: string, codigo: string, claveNueva: string): Observable<void> {
    return this.http.post<void>(this.url(slug, 'restablecer-clave'), { codigo, claveNueva });
  }

  private url(slug: string, accion: string): string {
    return `/api/comunas/${encodeURIComponent(slug)}/${accion}`;
  }
}
