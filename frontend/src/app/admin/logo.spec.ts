import { MAX_LOGO_BYTES, validarLogo } from './logo';

const archivo = (tipo: string, bytes: number) => new File([new Uint8Array(bytes)], 'logo', { type: tipo });

describe('validarLogo', () => {
  it('aceptaPngYJpgDentroDelLimite', () => {
    expect(validarLogo(archivo('image/png', 100))).toBeNull();
    expect(validarLogo(archivo('image/jpeg', MAX_LOGO_BYTES))).toBeNull();
  });

  it('rechazaOtrosTipos', () => {
    expect(validarLogo(archivo('image/gif', 100))).toBe('El logo tiene que ser una imagen PNG o JPG.');
    expect(validarLogo(archivo('', 100))).toBe('El logo tiene que ser una imagen PNG o JPG.');
  });

  it('rechazaLoQuePesaMasDe512Kb', () => {
    expect(validarLogo(archivo('image/png', MAX_LOGO_BYTES + 1))).toBe('El logo pesa más de 512 KB. Elegí una imagen más liviana.');
  });
});
