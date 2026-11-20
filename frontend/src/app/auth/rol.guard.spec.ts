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

function evaluar(roles: Rol | Rol[] = 'SUPERADMIN') {
  return TestBed.runInInjectionContext(() => rolGuard(roles, '/admin/login')({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
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

  it('conSesionPeroOtroRolVaASinPermiso', () => {
    entrar('VECINO');
    expect((evaluar() as UrlTree).toString()).toBe('/sin-permiso');
    entrar('ADMIN_COMUNA');
    expect((evaluar() as UrlTree).toString()).toBe('/sin-permiso');
  });

  it('aceptaVariosRoles', () => {
    entrar('ADMINISTRATIVO');
    expect(evaluar(['ADMINISTRATIVO', 'ADMIN_COMUNA'])).toBe(true);
    entrar('ADMIN_COMUNA');
    expect(evaluar(['ADMINISTRATIVO', 'ADMIN_COMUNA'])).toBe(true);
  });

  it('conVariosRolesRechazaALosQueNoEstan', () => {
    for (const rol of ['VECINO', 'AGENTE', 'SUPERADMIN'] as Rol[]) {
      entrar(rol);
      expect((evaluar(['ADMINISTRATIVO', 'ADMIN_COMUNA']) as UrlTree).toString()).toBe('/sin-permiso');
    }
  });
});
