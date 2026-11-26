import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PrioridadReclamoDto } from '../../reclamos/prioridad';
import { ChipPrioridad } from './chip-prioridad';

describe('ChipPrioridad', () => {
  function crear(prioridad: PrioridadReclamoDto | null | undefined): ComponentFixture<ChipPrioridad> {
    const f = TestBed.createComponent(ChipPrioridad);
    f.componentRef.setInput('prioridad', prioridad);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  // Lo que ve quien mira la pantalla (sin el texto solo para lectores).
  const visible = (f: ComponentFixture<unknown>) =>
    Array.from(el(f).querySelectorAll('.chip-p > span:not(.solo-lectores)')).map((s) => s.textContent?.trim()).join(' ');

  it('muestraLaPalabraDeCadaPrioridad', () => {
    expect(visible(crear({ valor: 'ALTA', origen: 'PERSONAL' }))).toBe('Alta');
    expect(visible(crear({ valor: 'MEDIA', origen: 'PERSONAL' }))).toBe('Media');
    expect(visible(crear({ valor: 'BAJA', origen: 'PERSONAL' }))).toBe('Baja');
  });

  it('sinPrioridadOSinDatoDiceSinPrioridad', () => {
    expect(visible(crear({ valor: null, origen: 'NINGUNA' }))).toBe('Sin prioridad');
    expect(visible(crear(undefined))).toBe('Sin prioridad');
    expect(visible(crear(null))).toBe('Sin prioridad');
  });

  it('laDelPersonalNoLlevaLaMarcaDeLaIA', () => {
    const f = crear({ valor: 'ALTA', origen: 'PERSONAL' });

    expect(el(f).querySelector('.marca')).toBeNull();
    expect(el(f).querySelector('.solo-lectores')).toBeNull();
  });

  it('laSugeridaPorLaIALlevaMarcaVisibleYTextoParaLectoresDePantalla', () => {
    const f = crear({ valor: 'ALTA', origen: 'IA' });

    expect(visible(f)).toBe('Alta Sugerida');
    expect(el(f).querySelector('.chip-p')?.classList.contains('sugerida')).toBe(true);
    // El lector oye "Alta, sugerida por la IA" y no repite la marca visible.
    expect(el(f).querySelector('.marca')?.getAttribute('aria-hidden')).toBe('true');
    expect(el(f).querySelector('.solo-lectores')?.textContent).toContain('sugerida por la IA');
  });

  it('sinValorNoSeMarcaComoSugeridaAunqueElOrigenSeaIA', () => {
    const f = crear({ valor: null, origen: 'IA' });

    expect(el(f).querySelector('.marca')).toBeNull();
  });

  it('elDibujoEsDecorativoYElColorSeSumaALaPalabra', () => {
    const f = crear({ valor: 'MEDIA', origen: 'PERSONAL' });

    expect(el(f).querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(el(f).querySelector('.chip-p')?.getAttribute('data-prioridad')).toBe('MEDIA');
  });
});
