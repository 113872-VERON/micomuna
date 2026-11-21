import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';

// A dónde ir sin sesión: una URL fija (el login del superadmin) o una que se arma con la ruta pedida.
export type DestinoSinSesion = string | ((ruta: ActivatedRouteSnapshot) => string);

// Deja pasar solo a los roles indicados. Sin sesión va a `destinoSinSesion` (el login que corresponda);
// con sesión pero sin el rol adecuado va a "No tenés permiso". El backend vuelve a comprobarlo en cada pedido.
export function rolGuard(roles: Rol | readonly Rol[], destinoSinSesion: DestinoSinSesion): CanActivateFn {
  const permitidos = typeof roles === 'string' ? [roles] : roles;
  return (ruta) => {
    const usuario = inject(AuthService).usuario();
    if (usuario && permitidos.includes(usuario.rol)) {
      return true;
    }
    if (usuario) {
      return inject(Router).parseUrl('/sin-permiso');
    }
    const destino = typeof destinoSinSesion === 'string' ? destinoSinSesion : destinoSinSesion(ruta);
    return inject(Router).parseUrl(destino);
  };
}

// El login de la comuna de la URL (`/:slug/...`). El slug se hereda de la ruta padre.
export function loginDeLaComuna(ruta: ActivatedRouteSnapshot): string {
  const slug = ruta.paramMap.get('slug');
  return slug ? `/${encodeURIComponent(slug)}/login` : '/no-encontrada';
}
