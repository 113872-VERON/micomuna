import { sugerirSlug } from './slug';

describe('sugerirSlug', () => {
  it('armaElEnlaceConElNombreYLaProvincia', () => {
    expect(sugerirSlug('Salsipuedes', 'CORDOBA')).toBe('salsipuedes-cordoba');
    expect(sugerirSlug('Salsipuedes', 'NEUQUEN')).toBe('salsipuedes-neuquen');
  });

  it('sacaTildesYEnies', () => {
    expect(sugerirSlug('Villa María', 'CORDOBA')).toBe('villa-maria-cordoba');
    expect(sugerirSlug('Ñandú', 'CHACO')).toBe('nandu-chaco');
    expect(sugerirSlug('Paraná', 'ENTRE_RIOS')).toBe('parana-entre-rios');
  });

  it('limpiaEspaciosYSignos', () => {
    expect(sugerirSlug('  San   Pedro!!  ', 'SALTA')).toBe('san-pedro-salta');
    expect(sugerirSlug('Villa (Nueva) / Sur', 'CORDOBA')).toBe('villa-nueva-sur-cordoba');
  });

  it('noSugiereNadaSiFaltaElNombreOLaProvincia', () => {
    expect(sugerirSlug('', 'CORDOBA')).toBe('');
    expect(sugerirSlug('   ', 'CORDOBA')).toBe('');
    expect(sugerirSlug('!!!', 'CORDOBA')).toBe('');
    expect(sugerirSlug('Salsipuedes', '')).toBe('');
  });
});
