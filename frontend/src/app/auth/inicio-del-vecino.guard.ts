import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// /inicio quedó como atajo: el vecino tiene su inicio en /<comuna>/inicio y se lo lleva ahí.
// El resto del personal sigue viendo la pantalla provisoria.
export const inicioDelVecinoGuard: CanActivateFn = () => {
  const usuario = inject(AuthService).usuario();
  return usuario?.rol === 'VECINO' && usuario.comunaSlug
    ? inject(Router).createUrlTree(['/', usuario.comunaSlug, 'inicio'])
    : true;
};
