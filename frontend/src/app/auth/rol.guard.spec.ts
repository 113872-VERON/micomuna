import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './modelos';
import { DestinoSinSesion, loginDeLaComuna, rolGuard } from './rol.guard';

function entrar(rol: Rol) {
  TestBed.inject(AuthService).guardarSesion({
    token: 'tk',
    usuario: { id: 1, nombre: 'Alguien', correo: 'a@correo.com', rol, comunaSlug: rol === 'SUPERADMIN' ? null : 'salsipuedes-cordoba' },
  });
}

function rutaDe(params: Record<string, string>): ActivatedRouteSnapshot {
  return { paramMap: convertToParamMap(params) } as ActivatedRouteSnapshot;
}

function evaluar(roles: Rol | Rol[] = 'SUPERADMIN', destino: DestinoSinSesion = '/admin/login', ruta = rutaDe({})) {
  return TestBed.runInInjectionContext(() => rolGuard(roles, destino)(ruta, {} as RouterStateSnapshot));
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

  describe('con el destino armado desde la ruta', () => {
    const ruta = rutaDe({ slug: 'salsipuedes-cordoba' });

    it('sinSesionVaAlLoginDeLaComunaDeLaUrl', () => {
      const resultado = evaluar('ADMIN_COMUNA', loginDeLaComuna, ruta);
      expect((resultado as UrlTree).toString()).toBe('/salsipuedes-cordoba/login');
    });

    it('elAdministradorDeLaComunaPasa', () => {
      entrar('ADMIN_COMUNA');
      expect(evaluar('ADMIN_COMUNA', loginDeLaComuna, ruta)).toBe(true);
    });

    it('conSesionDeOtroRolVaASinPermisoYNoAlLogin', () => {
      for (const rol of ['VECINO', 'AGENTE', 'ADMINISTRATIVO', 'SUPERADMIN'] as Rol[]) {
        entrar(rol);
        expect((evaluar('ADMIN_COMUNA', loginDeLaComuna, ruta) as UrlTree).toString()).toBe('/sin-permiso');
      }
    });

    it('laFuncionSoloSeUsaSinSesion', () => {
      const destino = vi.fn(() => '/x/login');
      entrar('VECINO');
      evaluar('ADMIN_COMUNA', destino, ruta);
      expect(destino).not.toHaveBeenCalled();
    });

    it('elSlugSeCodificaYSinSlugVaANoEncontrada', () => {
      expect(loginDeLaComuna(rutaDe({ slug: 'a b' }))).toBe('/a%20b/login');
      expect(loginDeLaComuna(rutaDe({}))).toBe('/no-encontrada');
    });
  });
});
