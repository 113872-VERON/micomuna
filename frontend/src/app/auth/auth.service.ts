import { Injectable, inject, signal } from '@angular/core';
import { AvisoPanelService } from '../admin/aviso-panel.service';
import { AuthRespuesta, UsuarioDto } from './modelos';

const CLAVE_TOKEN = 'micomuna.token';
const CLAVE_USUARIO = 'micomuna.usuario';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly avisosDelPanel = inject(AvisoPanelService);
  private tokenActual: string | null = null;

  readonly usuario = signal<UsuarioDto | null>(null);

  constructor() {
    const token = this.leer(CLAVE_TOKEN);
    const usuario = this.leerUsuario();
    if (token && usuario) {
      this.tokenActual = token;
      this.usuario.set(usuario);
    }
  }

  token(): string | null {
    return this.tokenActual;
  }

  estaAutenticado(): boolean {
    return this.tokenActual !== null;
  }

  guardarSesion(respuesta: AuthRespuesta): void {
    this.tokenActual = respuesta.token;
    this.usuario.set(respuesta.usuario);
    this.escribir(CLAVE_TOKEN, respuesta.token);
    this.escribir(CLAVE_USUARIO, JSON.stringify(respuesta.usuario));
  }

  cerrarSesion(): void {
    // Un aviso pendiente es de la sesión que se cierra: no tiene que aparecer en otra.
    this.avisosDelPanel.consumir();
    this.tokenActual = null;
    this.usuario.set(null);
    this.borrar(CLAVE_TOKEN);
    this.borrar(CLAVE_USUARIO);
  }

  // localStorage puede fallar (modo privado, datos de sitio bloqueados): la sesión sigue en memoria.
  private leer(clave: string): string | null {
    try {
      return localStorage.getItem(clave);
    } catch {
      return null;
    }
  }

  private escribir(clave: string, valor: string): void {
    try {
      localStorage.setItem(clave, valor);
    } catch {
      // se ignora: la sesión queda solo en memoria
    }
  }

  private borrar(clave: string): void {
    try {
      localStorage.removeItem(clave);
    } catch {
      // se ignora
    }
  }

  private leerUsuario(): UsuarioDto | null {
    const texto = this.leer(CLAVE_USUARIO);
    if (!texto) {
      return null;
    }
    try {
      const usuario = JSON.parse(texto) as UsuarioDto;
      return usuario && typeof usuario.rol === 'string' ? usuario : null;
    } catch {
      return null;
    }
  }
}
