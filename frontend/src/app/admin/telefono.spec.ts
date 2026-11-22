import { soloDigitos, telefonoValido } from './telefono';

describe('telefono', () => {
  it('aceptaVacioPorqueEsOpcional', () => {
    expect(telefonoValido('')).toBe(true);
    expect(telefonoValido('   ')).toBe(true);
  });

  it('aceptaEspaciosGuionesParentesisYMasSiQuedaUnNumeroArgentinoConCodigoDePais', () => {
    expect(telefonoValido('5493543123456')).toBe(true);
    expect(telefonoValido('+54 9 3543 12-3456')).toBe(true);
    expect(telefonoValido('543510000000')).toBe(true);
    expect(telefonoValido('+54 (351) 000-0000')).toBe(true);
  });

  it('rechazaLosQueNoSonArgentinosConCodigoDePaisYCaracteresRaros', () => {
    expect(telefonoValido('(0351) 000-0000')).toBe(false);
    expect(telefonoValido('12345678')).toBe(false);
    expect(telefonoValido('3543123456')).toBe(false);
    expect(telefonoValido('549354312')).toBe(false);
    expect(telefonoValido('5493543123456789')).toBe(false);
    expect(telefonoValido('5693543123456')).toBe(false);
    expect(telefonoValido('3543-abc-1234')).toBe(false);
    expect(telefonoValido('3543.123.456')).toBe(false);
  });

  it('soloDigitosSacaEspaciosGuionesParentesisYMas', () => {
    expect(soloDigitos('+54 9 3543-123456')).toBe('5493543123456');
    expect(soloDigitos('(0351) 000-0000')).toBe('03510000000');
  });
});
