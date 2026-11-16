import { TestBed } from '@angular/core/testing';
import { ComunaDto } from '../../auth/modelos';
import { CabeceraComuna } from './cabecera-comuna';

const base: ComunaDto = {
  nombre: 'Salsipuedes',
  provincia: 'Córdoba',
  departamento: 'Colón',
  slug: 'salsipuedes-cordoba',
  tieneLogo: true,
  color: 'CELESTE',
};

function crear(comuna: ComunaDto) {
  const fixture = TestBed.createComponent(CabeceraComuna);
  fixture.componentRef.setInput('comuna', comuna);
  fixture.detectChanges();
  return fixture;
}

const texto = (f: { nativeElement: unknown }) => (f.nativeElement as HTMLElement).textContent ?? '';

describe('CabeceraComuna', () => {
  it('muestraElNombreDeLaComunaYLaMarca', () => {
    const t = texto(crear(base));
    expect(t).toContain('MiComuna');
    expect(t).toContain('Comuna de');
    expect(t).toContain('Salsipuedes');
  });

  it('muestraElDepartamentoSoloSiExiste', () => {
    expect(texto(crear(base))).toContain('Colón, Córdoba');
    const sin = texto(crear({ ...base, departamento: null }));
    expect(sin).toContain('Córdoba');
    expect(sin).not.toContain(',');
  });

  it('cargaElLogoDesdeElBackend', () => {
    const img = (crear(base).nativeElement as HTMLElement).querySelector('img');
    expect(img?.getAttribute('src')).toBe('/api/comunas/salsipuedes-cordoba/logo');
  });

  it('sinLogoNoMuestraLaImagen', () => {
    const el = crear({ ...base, tieneLogo: false }).nativeElement as HTMLElement;
    expect(el.querySelector('img')).toBeNull();
  });

  it('ocultaElLogoSiFallaLaCarga', () => {
    const fixture = crear(base);
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector('img')?.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(el.querySelector('img')).toBeNull();
  });
});
