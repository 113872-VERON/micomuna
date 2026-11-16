import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { CampoClave } from './campo-clave';

function crear() {
  const control = new FormControl('miclave123', { nonNullable: true });
  const fixture = TestBed.createComponent(CampoClave);
  fixture.componentRef.setInput('control', control);
  fixture.componentRef.setInput('etiqueta', 'Contraseña');
  fixture.componentRef.setInput('idCampo', 'clave');
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    control,
    input: el.querySelector('input') as HTMLInputElement,
    ojo: el.querySelector('button.ojo') as HTMLButtonElement,
  };
}

describe('CampoClave', () => {
  it('empiezaConLaClaveOculta', () => {
    const { input, ojo } = crear();
    expect(input.type).toBe('password');
    expect(ojo.getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(ojo.getAttribute('aria-pressed')).toBe('false');
  });

  it('elOjoAlternaLaVisibilidadDeLaClave', () => {
    const { fixture, input, ojo } = crear();

    ojo.click();
    fixture.detectChanges();
    expect(input.type).toBe('text');
    expect(ojo.getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(ojo.getAttribute('aria-pressed')).toBe('true');

    ojo.click();
    fixture.detectChanges();
    expect(input.type).toBe('password');
  });

  it('elOjoNoEnviaElFormulario', () => {
    expect(crear().ojo.type).toBe('button');
  });

  it('laEtiquetaApuntaAlCampo', () => {
    const { fixture } = crear();
    const label = (fixture.nativeElement as HTMLElement).querySelector('label');
    expect(label?.getAttribute('for')).toBe('clave');
    expect(label?.textContent).toContain('Contraseña');
  });

  it('escribirActualizaElControl', () => {
    const { input, control } = crear();
    input.value = 'otra-clave-1';
    input.dispatchEvent(new Event('input'));
    expect(control.value).toBe('otra-clave-1');
  });
});
