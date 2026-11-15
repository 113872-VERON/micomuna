import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { RedirectCommand, ResolveFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ComunaService } from './comuna.service';
import { ComunaDto } from './modelos';

export const comunaResolver: ResolveFn<ComunaDto | RedirectCommand> = (ruta) => {
  const router = inject(Router);
  const slug = ruta.paramMap.get('slug') ?? '';
  return inject(ComunaService)
    .obtener(slug)
    .pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 404
          ? [new RedirectCommand(router.parseUrl('/no-encontrada'))]
          : throwError(() => error),
      ),
    );
};
