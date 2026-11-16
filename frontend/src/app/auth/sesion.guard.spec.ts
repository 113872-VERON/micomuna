import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { sesionGuard } from './sesion.guard';

function evaluar() {
  return TestBed.runInInjectionContext(() => sesionGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
}

describe('sesionGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('dejaPasarConSesion', () => {
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });
    expect(evaluar()).toBe(true);
  });

  it('sinSesionVaANoEncontrada', () => {
    const resultado = evaluar();
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/no-encontrada');
  });
});
