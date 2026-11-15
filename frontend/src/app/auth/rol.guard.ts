import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';

// Deja pasar solo al rol indicado; cualquier otro (o sin sesión) va al destino. El backend vuelve a comprobarlo.
export function rolGuard(rol: Rol, destino: string): CanActivateFn {
  return () => {
    const usuario = inject(AuthService).usuario();
    return usuario?.rol === rol ? true : inject(Router).parseUrl(destino);
  };
}
