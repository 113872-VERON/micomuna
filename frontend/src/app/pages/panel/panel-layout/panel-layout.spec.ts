import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { PanelLayout } from './panel-layout';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'BOSQUE',
};

describe('PanelLayout', () => {
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [PanelLayout], providers: [provideRouter([])] }).compileComponents();
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Sergio Vera', correo: 'sergio@correo.com', rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.documentElement.style.removeProperty('--p');
  });

  function crear(): ComponentFixture<PanelLayout> {
    const f = TestBed.createComponent(PanelLayout);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', comuna);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const menu = () => Array.from(document.querySelectorAll('[role="menuitem"]')).map((i) => i.getAttribute('href'));

  it('muestraLaComunaYQuienIngresoConSuRolEnTextoClaro', () => {
    const f = crear();
    const texto = el(f).textContent ?? '';
    expect(texto).toContain('Salsipuedes');
    expect(texto).toContain('Sergio Vera');
    expect(texto).toContain('Administrador');
    const enlaces = Array.from(el(f).querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toEqual(['/salsipuedes-cordoba/panel/reclamos', '/salsipuedes-cordoba/panel/metricas', '/salsipuedes-cordoba/panel/usuarios']);
  });

  it('elPersonalAdministrativoVeReclamosYMetricasPeroNoUsuarios', () => {
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 2, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' },
    });
    const f = crear();

    expect(Array.from(el(f).querySelectorAll('nav a')).map((a) => a.textContent?.trim())).toEqual(['Reclamos', 'Métricas']);
    expect(el(f).textContent).toContain('Personal administrativo');
  });

  it('usaElColorDeLaComuna', () => {
    crear();
    expect(document.documentElement.style.getPropertyValue('--p')).toBe('#2F6B3F');
  });

  it('elBotonDelUsuarioAbreUnMenuConMisDatosCambiarContrasenaYSalir', async () => {
    const f = crear();
    (el(f).querySelector('button.disparador') as HTMLButtonElement).click();
    f.detectChanges();
    await vi.waitFor(() => expect(document.querySelectorAll('[role="menuitem"]').length).toBe(3));

    expect(menu()).toEqual(['/salsipuedes-cordoba/panel/mi-cuenta', '/salsipuedes-cordoba/panel/cambiar-clave', null]);
    expect(Array.from(document.querySelectorAll('[role="menuitem"]')).map((i) => i.textContent)).toEqual([
      expect.stringContaining('Mis datos'), expect.stringContaining('Cambiar contraseña'), expect.stringContaining('Salir'),
    ]);
  });

  it('yaNoHayUnBotonSalirSueltoEnLaBarra', () => {
    const f = crear();
    expect(Array.from(el(f).querySelectorAll('header button')).map((b) => b.textContent?.trim())).not.toContain('Salir');
  });

  it('salirDesdeElMenuCierraLaSesionYVuelveAlLoginDeLaComuna', async () => {
    const f = crear();
    (el(f).querySelector('button.disparador') as HTMLButtonElement).click();
    f.detectChanges();
    await vi.waitFor(() => expect(document.querySelectorAll('[role="menuitem"]').length).toBe(3));
    (Array.from(document.querySelectorAll<HTMLElement>('[role="menuitem"]')).find((i) => i.textContent?.includes('Salir')) as HTMLElement).click();

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/login');
  });

  it('laBarraEsUnaGrillaDeTresColumnasConLasOpcionesAlCentro', () => {
    const f = crear();
    const estilo = getComputedStyle(el(f).querySelector('header') as HTMLElement);
    expect(estilo.display).toBe('grid');
    expect(el(f).querySelector('header')!.children.length).toBe(3);
    expect(Array.from(el(f).querySelector('header')!.children).map((c) => c.tagName)).toEqual(['DIV', 'NAV', 'APP-MENU-USUARIO']);
  });
});
