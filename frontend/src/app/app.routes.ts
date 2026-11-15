import { Routes } from '@angular/router';
import { comunaResolver } from './auth/comuna.resolver';
import { sesionGuard } from './auth/sesion.guard';

// Las pantallas del superadmin (admin/*) se suman en la Tarea 13. Las de una comuna reciben `slug` y `comuna`
// como inputs (withComponentInputBinding + paramsInheritanceStrategy 'always' en app.config.ts).
export const routes: Routes = [
  { path: 'inicio', canActivate: [sesionGuard], loadComponent: () => import('./pages/inicio/inicio').then((m) => m.Inicio) },
  { path: 'no-encontrada', loadComponent: () => import('./pages/no-encontrada/no-encontrada').then((m) => m.NoEncontrada) },
  {
    path: ':slug',
    resolve: { comuna: comunaResolver },
    loadComponent: () => import('./pages/comuna/comuna-layout').then((m) => m.ComunaLayout),
    children: [
      { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
      { path: 'registro', loadComponent: () => import('./pages/registro/registro').then((m) => m.Registro) },
      { path: 'recuperar', loadComponent: () => import('./pages/recuperar-clave/recuperar-clave').then((m) => m.RecuperarClave) },
      { path: 'restablecer', loadComponent: () => import('./pages/restablecer-clave/restablecer-clave').then((m) => m.RestablecerClave) },
      { path: '', pathMatch: 'full', redirectTo: 'login' },
    ],
  },
  { path: '**', redirectTo: 'no-encontrada' },
];
