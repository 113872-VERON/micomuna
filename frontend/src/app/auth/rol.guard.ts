import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';

// Deja pasar solo a los roles indicados. Sin sesión va a `destinoSinSesion` (el login que corresponda);
// con sesión pero sin el rol adecuado va a "No tenés permiso". El backend vuelve a comprobarlo en cada pedido.
export function rolGuard(roles: Rol | readonly Rol[], destinoSinSesion: string): CanActivateFn {
  const permitidos = typeof roles === 'string' ? [roles] : roles;
  return () => {
    const usuario = inject(AuthService).usuario();
    if (usuario && permitidos.includes(usuario.rol)) {
      return true;
    }
    return inject(Router).parseUrl(usuario ? '/sin-permiso' : destinoSinSesion);
  };
}
