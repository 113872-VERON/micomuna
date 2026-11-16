import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Sin sesión no hay pantalla: se manda a "no encontrada", que explica cómo entrar.
export const sesionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() ? true : inject(Router).parseUrl('/no-encontrada');
};
