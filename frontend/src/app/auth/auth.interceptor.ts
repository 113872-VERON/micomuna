import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

// Un 401 en estos pedidos significa "credenciales incorrectas", no "sesión vencida".
const PEDIDOS_DE_ENTRADA = /\/(login|registro)$/;

// El backend responde ProblemDetail; `codigo` dice qué tipo de 403 es.
function esAccesoDenegado(error: HttpErrorResponse): boolean {
  const cuerpo: unknown = error.error;
  return typeof cuerpo === 'object' && cuerpo !== null && (cuerpo as { codigo?: unknown }).codigo === 'ACCESO_DENEGADO';
}

export const authInterceptor: HttpInterceptorFn = (pedido, siguiente) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // El token solo viaja al propio backend (rutas relativas), nunca a otro servidor.
  const token = auth.token();
  // `//otro-host/...` también empieza con "/" pero apunta a otro servidor.
  const propio = pedido.url.startsWith('/') && !pedido.url.startsWith('//');
  const enviado = token && propio ? pedido.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : pedido;

  return siguiente(enviado).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && propio && !PEDIDOS_DE_ENTRADA.test(pedido.url)) {
        const usuario = auth.usuario();
        auth.cerrarSesion();
        const destino = usuario?.rol === 'SUPERADMIN' || !usuario?.comunaSlug
          ? '/admin/login'
          : `/${encodeURIComponent(usuario.comunaSlug)}/login`;
        void router.navigateByUrl(destino);
      }
      // 403 con `ACCESO_DENEGADO`: la sesión sigue valiendo, pero el rol no alcanza para eso.
      // Otros 403 son errores de negocio y los muestra la pantalla que hizo el pedido.
      if (error instanceof HttpErrorResponse && error.status === 403 && propio && esAccesoDenegado(error)) {
        void router.navigateByUrl('/sin-permiso');
      }
      return throwError(() => error);
    }),
  );
};
