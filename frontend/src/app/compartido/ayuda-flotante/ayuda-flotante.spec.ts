import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AyudaFlotante } from './ayuda-flotante';

@Component({
  imports: [AyudaFlotante],
  template: `<button type="button" appAyuda="Texto de ayuda" aria-label="Botón">i</button><button type="button" id="otro">otro</button>`,
})
class Anfitrion {}

describe('AyudaFlotante', () => {
  function crear() {
    const f = TestBed.createComponent(Anfitrion);
    f.detectChanges();
    const boton = (f.nativeElement as HTMLElement).querySelector('button')!;
    const globito = () => document.body.querySelector<HTMLElement>('[role="tooltip"]')!;
    return { f, boton, globito };
  }

  it('apareceAlPasarElMouseYSeVaAlSalir', () => {
    const { boton, globito } = crear();
    expect(globito().textContent).toBe('Texto de ayuda');
    expect(globito().style.visibility).toBe('hidden');

    boton.dispatchEvent(new Event('mouseenter'));
    expect(globito().style.visibility).toBe('visible');
    boton.dispatchEvent(new Event('mouseleave'));
    expect(globito().style.visibility).toBe('hidden');
  });

  it('apareceConElFocoDelTecladoYSeCierraConEscape', () => {
    const { boton, globito } = crear();

    boton.dispatchEvent(new Event('focus'));
    expect(globito().style.visibility).toBe('visible');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(globito().style.visibility).toBe('hidden');
  });

  it('apareceAlTocarYSeDescribeDesdeElBoton', () => {
    const { boton, globito } = crear();

    boton.click();
    expect(globito().style.visibility).toBe('visible');
    expect(boton.getAttribute('aria-describedby')).toBe(globito().id);
  });

  it('alDestruirseQuitaElGlobitoDelDocumento', () => {
    const { f } = crear();
    f.destroy();
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull();
  });
});
