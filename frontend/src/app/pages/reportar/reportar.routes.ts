import { CanDeactivateFn, Routes } from '@angular/router';
import { PASO_INICIAL } from './pasos';
import { Reportar } from './reportar';

// Antes de dejar el recorrido: pasar de un paso a otro no pregunta; salir a otra pantalla
// con algo cargado muestra el aviso "¿Salir sin enviar?".
export const salirDelReporte: CanDeactivateFn<Reportar> = (pantalla, _ruta, _actual, siguiente) =>
  pantalla.puedeSalirHacia(siguiente.url);

// /<slug>/reportar/<paso>. El componente se reutiliza al cambiar de paso: lo cargado no se pierde.
export const RUTAS_REPORTAR: Routes = [
  { path: '', pathMatch: 'full', redirectTo: PASO_INICIAL },
  { path: ':paso', component: Reportar, canDeactivate: [salirDelReporte] },
];
