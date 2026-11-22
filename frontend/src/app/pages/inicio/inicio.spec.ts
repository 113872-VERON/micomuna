import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Rol } from '../../auth/modelos';
import { Inicio } from './inicio';

describe('Inicio (provisorio)', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [Inicio], providers: [provideRouter([])] }).compileComponents();
  });

  function crearCon(rol: Rol): ComponentFixture<Inicio> {
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol, comunaSlug: 'salsipuedes-cordoba' },
    });
    const f = TestBed.createComponent(Inicio);
    f.detectChanges();
    return f;
  }
  const enlaces = (f: ComponentFixture<Inicio>) => Array.from((f.nativeElement as HTMLElement).querySelectorAll('a'));

  it('elVecinoTieneElBotonParaReportarEnSuComuna', () => {
    const f = crearCon('VECINO');

    const reportar = enlaces(f).find((a) => a.textContent?.trim() === 'Reportar un problema');
    expect(reportar?.getAttribute('href')).toBe('/salsipuedes-cordoba/reportar');
  });

  it('elPersonalDeLaComunaNoLoVe', () => {
    for (const rol of ['AGENTE', 'ADMINISTRATIVO'] as Rol[]) {
      const f = crearCon(rol);
      expect(enlaces(f).some((a) => a.textContent?.includes('Reportar'))).toBe(false);
      f.destroy();
    }
  });
});
