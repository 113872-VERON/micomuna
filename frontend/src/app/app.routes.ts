import { Routes } from '@angular/router';
import { sesionGuard } from './auth/sesion.guard';

// Las rutas por comuna (:slug/login, :slug/registro, ...) y admin/* se suman en las Tareas 12 y 13.
export const routes: Routes = [
  { path: 'inicio', canActivate: [sesionGuard], loadComponent: () => import('./pages/inicio/inicio').then((m) => m.Inicio) },
  { path: 'no-encontrada', loadComponent: () => import('./pages/no-encontrada/no-encontrada').then((m) => m.NoEncontrada) },
  { path: '**', redirectTo: 'no-encontrada' },
];
