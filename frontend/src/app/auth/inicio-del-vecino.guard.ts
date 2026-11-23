import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// /inicio quedó como atajo: el vecino tiene su inicio en /<comuna>/inicio, y el personal administrativo y el
// administrador tienen su panel de reclamos. El agente va a Mis reclamos.
export const inicioDelVecinoGuard: CanActivateFn = () => {
  const usuario = inject(AuthService).usuario();
  if (!usuario?.comunaSlug) {
    return true;
  }
  if (usuario.rol === 'VECINO') {
    return inject(Router).createUrlTree(['/', usuario.comunaSlug, 'inicio']);
  }
  if (usuario.rol === 'ADMINISTRATIVO' || usuario.rol === 'ADMIN_COMUNA') {
    return inject(Router).createUrlTree(['/', usuario.comunaSlug, 'panel', 'reclamos']);
  }
  if (usuario.rol === 'AGENTE') {
    return inject(Router).createUrlTree(['/', usuario.comunaSlug, 'agente']);
  }
  return true;
};
