import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { MenuUsuario } from './menu-usuario';

const itemsDelMenu = () => Array.from(document.querySelectorAll<HTMLElement>('[role="menuitem"]'));

describe('MenuUsuario', () => {
  let fixture: ComponentFixture<MenuUsuario>;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [MenuUsuario], providers: [provideRouter([])] }).compileComponents();
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Sergio Vera', correo: 'sergio@correo.com', rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
    });
    fixture = TestBed.createComponent(MenuUsuario);
    fixture.componentRef.setInput('zona', ['/', 'salsipuedes-cordoba', 'panel']);
    fixture.componentRef.setInput('alSalir', '/salsipuedes-cordoba/login');
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  const disparador = () => (fixture.nativeElement as HTMLElement).querySelector('button.disparador') as HTMLButtonElement;

  async function abrir(): Promise<void> {
    disparador().click();
    fixture.detectChanges();
    await vi.waitFor(() => expect(itemsDelMenu().length).toBe(3));
  }

  it('muestraElAvatarElNombreYElRolEnElBoton', () => {
    const texto = disparador().textContent ?? '';
    expect(texto).toContain('S');
    expect(texto).toContain('Sergio Vera');
    expect(texto).toContain('Administrador');
    expect(disparador().getAttribute('aria-haspopup')).toBe('menu');
    expect(disparador().getAttribute('aria-expanded')).toBe('false');
  });

  it('alTocarloAbreUnMenuConMisDatosCambiarContrasenaYSalir', async () => {
    await abrir();

    expect(disparador().getAttribute('aria-expanded')).toBe('true');
    expect(itemsDelMenu().map((i) => i.textContent?.replace(/^\s*(person|lock|logout)/, '').replace(/\s+/g, ' ').trim())).toEqual(['Mis datos', 'Cambiar contraseña', 'Salir']);
  });

  it('losEnlacesLlevanALasPantallasDeLaZona', async () => {
    await abrir();

    const hrefs = itemsDelMenu().map((i) => i.getAttribute('href'));
    expect(hrefs).toEqual(['/salsipuedes-cordoba/panel/mi-cuenta', '/salsipuedes-cordoba/panel/cambiar-clave', null]);
  });

  it('seCierraConEscapeYElFocoVuelveAlBoton', async () => {
    await abrir();

    document.querySelector('.mat-mdc-menu-panel')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    fixture.detectChanges();
    await vi.waitFor(() => expect(itemsDelMenu().length).toBe(0));

    expect(disparador().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(disparador());
  });

  it('salirCierraLaSesionYVaAlLogin', async () => {
    await abrir();

    itemsDelMenu().find((i) => i.textContent?.includes('Salir'))!.click();

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/login');
  });

  it('siCambiaElNombreDeLaSesionElBotonLoMuestraAlInstante', () => {
    const auth = TestBed.inject(AuthService);
    auth.actualizarUsuario({ ...auth.usuario()!, nombre: 'Álvaro Vera' });
    fixture.detectChanges();

    expect(disparador().textContent).toContain('Álvaro Vera');
    expect(disparador().textContent).toContain('Á');
  });
});
