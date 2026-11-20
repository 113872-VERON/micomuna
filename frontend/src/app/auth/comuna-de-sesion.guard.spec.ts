import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { comunaDeSesionGuard } from './comuna-de-sesion.guard';
import { UsuarioDto } from './modelos';

const vecino: UsuarioDto = { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' };
const superadmin: UsuarioDto = { id: 2, nombre: 'Andres', correo: 'a@correo.com', rol: 'SUPERADMIN', comunaSlug: null };

function evaluar(slug: string) {
  const ruta = { paramMap: convertToParamMap({ slug }) } as ActivatedRouteSnapshot;
  return TestBed.runInInjectionContext(() => comunaDeSesionGuard(ruta, {} as RouterStateSnapshot));
}

describe('comunaDeSesionGuard', () => {
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    auth = TestBed.inject(AuthService);
  });

  it('enOtraComunaCierraLaSesionYDejaPasar', () => {
    auth.guardarSesion({ token: 'tk', usuario: vecino });
    expect(evaluar('rio-ceballos')).toBe(true);
    expect(auth.estaAutenticado()).toBe(false);
    expect(auth.usuario()).toBeNull();
  });

  it('enSuPropiaComunaNoTocaLaSesion', () => {
    auth.guardarSesion({ token: 'tk', usuario: vecino });
    expect(evaluar('salsipuedes-cordoba')).toBe(true);
    expect(auth.estaAutenticado()).toBe(true);
  });

  it('comparaElSlugSinDistinguirMayusculas', () => {
    auth.guardarSesion({ token: 'tk', usuario: vecino });
    expect(evaluar('Salsipuedes-Cordoba')).toBe(true);
    expect(auth.estaAutenticado()).toBe(true);
  });

  it('alSuperadminNoLeCierraLaSesion', () => {
    auth.guardarSesion({ token: 'tk-admin', usuario: superadmin });
    expect(evaluar('salsipuedes-cordoba')).toBe(true);
    expect(auth.estaAutenticado()).toBe(true);
  });

  it('sinSesionDejaPasar', () => {
    const cerrar = vi.spyOn(auth, 'cerrarSesion');
    expect(evaluar('salsipuedes-cordoba')).toBe(true);
    expect(cerrar).not.toHaveBeenCalled();
  });
});
