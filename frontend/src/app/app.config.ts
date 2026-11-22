import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // 'computed': si el vecino toca "atrás" y en el aviso elige "Seguir cargando", el historial
    // del navegador vuelve a quedar como estaba (con 'replace' se perdería una entrada).
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({ paramsInheritanceStrategy: 'always', canceledNavigationResolution: 'computed' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
