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

  it('muestraLaComunaYQuienIngresoConSuRolEnTextoClaro', () => {
    const f = crear();
    const texto = el(f).textContent ?? '';
    expect(texto).toContain('Salsipuedes');
    expect(texto).toContain('Sergio Vera');
    expect(texto).toContain('Administrador');
    expect(el(f).querySelector('nav a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/panel/usuarios');
  });

  it('usaElColorDeLaComuna', () => {
    crear();
    expect(document.documentElement.style.getPropertyValue('--p')).toBe('#2F6B3F');
  });

  it('salirCierraLaSesionYVuelveAlLoginDeLaComuna', () => {
    const f = crear();
    (Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Salir') as HTMLButtonElement).click();

    expect(TestBed.inject(AuthService).estaAutenticado()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/salsipuedes-cordoba/login');
  });
});
