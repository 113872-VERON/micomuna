import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

// Un 401 en estos pedidos significa "credenciales incorrectas", no "sesión vencida".
const PEDIDOS_DE_ENTRADA = /\/(login|registro)$/;

export const authInterceptor: HttpInterceptorFn = (pedido, siguiente) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // El token solo viaja al propio backend (rutas relativas), nunca a otro servidor.
  const token = auth.token();
  const propio = pedido.url.startsWith('/');
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
      return throwError(() => error);
    }),
  );
};
