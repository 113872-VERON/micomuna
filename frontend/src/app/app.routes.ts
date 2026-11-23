import { Routes } from '@angular/router';
import { comunaDeSesionGuard } from './auth/comuna-de-sesion.guard';
import { inicioDelVecinoGuard } from './auth/inicio-del-vecino.guard';
import { comunaResolver } from './auth/comuna.resolver';
import { loginDeLaComuna, rolGuard } from './auth/rol.guard';
import { sesionGuard } from './auth/sesion.guard';

// Las pantallas de una comuna reciben `slug`, `comuna` y los datos de la ruta como inputs
// (withComponentInputBinding + paramsInheritanceStrategy 'always' en app.config.ts).
export const routes: Routes = [
  { path: 'inicio', canActivate: [sesionGuard, inicioDelVecinoGuard], loadComponent: () => import('./pages/inicio/inicio').then((m) => m.Inicio) },
  { path: 'sin-permiso', canActivate: [sesionGuard], loadComponent: () => import('./pages/sin-permiso/sin-permiso').then((m) => m.SinPermiso) },
  { path: 'no-encontrada', loadComponent: () => import('./pages/no-encontrada/no-encontrada').then((m) => m.NoEncontrada) },
  // `admin` va antes que `:slug`: el panel del superadmin no es una comuna.
  {
    path: 'admin',
    children: [
      { path: 'login', loadComponent: () => import('./pages/admin/admin-login/admin-login').then((m) => m.AdminLogin) },
      {
        path: '',
        canActivate: [rolGuard('SUPERADMIN', '/admin/login')],
        loadComponent: () => import('./pages/admin/admin-layout/admin-layout').then((m) => m.AdminLayout),
        children: [
          { path: 'comunas', loadComponent: () => import('./pages/admin/comunas/admin-comunas').then((m) => m.AdminComunas) },
          { path: 'comunas/nueva', loadComponent: () => import('./pages/admin/nueva-comuna/nueva-comuna').then((m) => m.NuevaComuna) },
          { path: 'comunas/:id/editar', loadComponent: () => import('./pages/admin/editar-comuna/editar-comuna').then((m) => m.EditarComuna) },
          { path: '', pathMatch: 'full', redirectTo: 'comunas' },
        ],
      },
    ],
  },
  {
    path: ':slug',
    // Una sesión solo vale para su comuna: abrir otra comuna cierra la sesión (ver el guard).
    // Este guard corre antes que los de las rutas hijas: el de rol ya ve la sesión cerrada.
    canActivate: [comunaDeSesionGuard],
    resolve: { comuna: comunaResolver },
    children: [
      // Panel del administrador de la comuna: una barra superior con la marca de la comuna,
      // en lugar de la cabecera grande de las pantallas de entrada (ahí no entra una tabla).
      {
        path: 'panel',
        // El personal administrativo y el administrador ven los reclamos; "Usuarios" es solo del administrador.
        canActivate: [rolGuard(['ADMINISTRATIVO', 'ADMIN_COMUNA'], loginDeLaComuna)],
        loadComponent: () => import('./pages/panel/panel-layout/panel-layout').then((m) => m.PanelLayout),
        children: [
          { path: 'reclamos', loadComponent: () => import('./pages/panel/reclamos/reclamos-panel').then((m) => m.ReclamosPanel) },
          { path: 'reclamos/:numero', loadComponent: () => import('./pages/panel/reclamos/detalle-panel').then((m) => m.DetallePanel) },
          {
            path: 'usuarios',
            canActivate: [rolGuard('ADMIN_COMUNA', loginDeLaComuna)],
            loadComponent: () => import('./pages/panel/usuarios/usuarios').then((m) => m.Usuarios),
          },
          {
            path: 'usuarios/nuevo',
            canActivate: [rolGuard('ADMIN_COMUNA', loginDeLaComuna)],
            loadComponent: () => import('./pages/panel/usuarios/nuevo-usuario/nuevo-usuario').then((m) => m.NuevoUsuario),
          },
          { path: '', pathMatch: 'full', redirectTo: 'reclamos' },
        ],
      },
      // Reportar un problema (MC-15): solo el vecino. Marco propio de una columna, un paso por dirección.
      {
        path: 'reportar',
        canActivate: [rolGuard('VECINO', loginDeLaComuna)],
        loadChildren: () => import('./pages/reportar/reportar.routes').then((m) => m.RUTAS_REPORTAR),
      },
      // Pantallas del vecino con sesión (MC-17): su inicio con el seguimiento y el detalle de cada reclamo.
      {
        path: '',
        canActivate: [rolGuard('VECINO', loginDeLaComuna)],
        loadComponent: () => import('./pages/vecino/marco-vecino').then((m) => m.MarcoVecino),
        children: [
          { path: 'inicio', loadComponent: () => import('./pages/vecino/inicio-vecino/inicio-vecino').then((m) => m.InicioVecino) },
          { path: 'reclamos/:numero', loadComponent: () => import('./pages/vecino/detalle-reclamo/detalle-reclamo').then((m) => m.DetalleReclamo) },
        ],
      },
      {
        path: '',
        loadComponent: () => import('./pages/comuna/comuna-layout').then((m) => m.ComunaLayout),
        children: [
          { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
          { path: 'registro', loadComponent: () => import('./pages/registro/registro').then((m) => m.Registro) },
          { path: 'recuperar', loadComponent: () => import('./pages/recuperar-clave/recuperar-clave').then((m) => m.RecuperarClave) },
          { path: 'restablecer', loadComponent: () => import('./pages/restablecer-clave/restablecer-clave').then((m) => m.RestablecerClave) },
          {
            path: 'invitacion',
            data: { modo: 'invitacion' },
            loadComponent: () => import('./pages/restablecer-clave/restablecer-clave').then((m) => m.RestablecerClave),
          },
          { path: '', pathMatch: 'full', redirectTo: 'login' },
        ],
      },
    ],
  },
  { path: '**', redirectTo: 'no-encontrada' },
];
