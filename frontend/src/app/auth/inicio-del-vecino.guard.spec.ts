import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { inicioDelVecinoGuard } from './inicio-del-vecino.guard';
import { Rol } from './modelos';

function evaluar() {
  return TestBed.runInInjectionContext(() => inicioDelVecinoGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
}

describe('inicioDelVecinoGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function entrar(rol: Rol, comunaSlug: string | null) {
    TestBed.inject(AuthService).guardarSesion({ token: 'tk', usuario: { id: 1, nombre: 'Rosa', correo: 'r@c.com', rol, comunaSlug } });
  }

  it('alVecinoLoLlevaAlInicioDeSuComuna', () => {
    entrar('VECINO', 'salsipuedes-cordoba');
    const destino = evaluar() as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(destino)).toBe('/salsipuedes-cordoba/inicio');
  });

  it('alPersonalAdministrativoYAlAdministradorLosLlevaAlPanelDeReclamos', () => {
    for (const rol of ['ADMINISTRATIVO', 'ADMIN_COMUNA'] as Rol[]) {
      entrar(rol, 'salsipuedes-cordoba');
      expect(TestBed.inject(Router).serializeUrl(evaluar() as UrlTree)).toBe('/salsipuedes-cordoba/panel/reclamos');
    }
  });

  it('alAgenteLoDejaVerLaPantallaProvisoria', () => {
    entrar('AGENTE', 'salsipuedes-cordoba');
    expect(evaluar()).toBe(true);
  });

  it('alSuperadminSinComunaLoDejaPasar', () => {
    entrar('SUPERADMIN', null);
    expect(evaluar()).toBe(true);
  });
});
