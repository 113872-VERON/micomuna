import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';
import { rolGuard } from './rol.guard';

function entrar(rol: Rol) {
  TestBed.inject(AuthService).guardarSesion({
    token: 'tk',
    usuario: { id: 1, nombre: 'Alguien', correo: 'a@correo.com', rol, comunaSlug: rol === 'SUPERADMIN' ? null : 'salsipuedes-cordoba' },
  });
}

function evaluar() {
  return TestBed.runInInjectionContext(() => rolGuard('SUPERADMIN', '/admin/login')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
}

describe('rolGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('dejaPasarAlRolIndicado', () => {
    entrar('SUPERADMIN');
    expect(evaluar()).toBe(true);
  });

  it('sinSesionVaAlDestino', () => {
    const resultado = evaluar();
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/admin/login');
  });

  it('otroRolNoPasa', () => {
    entrar('VECINO');
    expect((evaluar() as UrlTree).toString()).toBe('/admin/login');
    entrar('ADMIN_COMUNA');
    expect((evaluar() as UrlTree).toString()).toBe('/admin/login');
  });
});
