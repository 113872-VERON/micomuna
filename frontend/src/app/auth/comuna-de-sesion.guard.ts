import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

// Una sesión solo vale para su comuna: si alguien con sesión en la comuna A abre una pantalla de la
// comuna B, se cierra la sesión y la pantalla de B se muestra sin sesión. El superadmin no tiene
// comuna y no se ve afectado. Siempre deja pasar; el backend igual rechaza un token de otra comuna.
export const comunaDeSesionGuard: CanActivateFn = (ruta) => {
  const auth = inject(AuthService);
  const propia = auth.usuario()?.comunaSlug;
  const pedida = ruta.paramMap.get('slug') ?? '';
  if (propia && propia.toLowerCase() !== pedida.toLowerCase()) {
    auth.cerrarSesion();
  }
  return true;
};
