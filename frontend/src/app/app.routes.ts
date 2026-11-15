import { Routes } from '@angular/router';
import { comunaResolver } from './auth/comuna.resolver';
import { rolGuard } from './auth/rol.guard';
import { sesionGuard } from './auth/sesion.guard';

// Las pantallas de una comuna reciben `slug`, `comuna` y los datos de la ruta como inputs
// (withComponentInputBinding + paramsInheritanceStrategy 'always' en app.config.ts).
export const routes: Routes = [
  { path: 'inicio', canActivate: [sesionGuard], loadComponent: () => import('./pages/inicio/inicio').then((m) => m.Inicio) },
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
          { path: '', pathMatch: 'full', redirectTo: 'comunas' },
        ],
      },
    ],
  },
  {
    path: ':slug',
    resolve: { comuna: comunaResolver },
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
  { path: '**', redirectTo: 'no-encontrada' },
];
