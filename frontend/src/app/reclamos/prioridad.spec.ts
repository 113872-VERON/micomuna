import { SIN_PRIORIDAD, nombreDePrioridad, rangoDePrioridad } from './prioridad';

describe('prioridad de un reclamo', () => {
  it('losNombresEstanEnEspañol', () => {
    expect(nombreDePrioridad('ALTA')).toBe('Alta');
    expect(nombreDePrioridad('MEDIA')).toBe('Media');
    expect(nombreDePrioridad('BAJA')).toBe('Baja');
    expect(nombreDePrioridad(null)).toBe('Sin prioridad');
    expect(nombreDePrioridad(undefined)).toBe('Sin prioridad');
  });

  it('elRangoVaDeAltaASinPrioridadYUnDatoFaltanteEsSinPrioridad', () => {
    expect(rangoDePrioridad({ valor: 'ALTA', origen: 'IA' })).toBeLessThan(rangoDePrioridad({ valor: 'MEDIA', origen: 'PERSONAL' }));
    expect(rangoDePrioridad({ valor: 'MEDIA', origen: 'PERSONAL' })).toBeLessThan(rangoDePrioridad({ valor: 'BAJA', origen: 'PERSONAL' }));
    expect(rangoDePrioridad({ valor: 'BAJA', origen: 'PERSONAL' })).toBeLessThan(rangoDePrioridad(SIN_PRIORIDAD));
    expect(rangoDePrioridad(undefined)).toBe(rangoDePrioridad(SIN_PRIORIDAD));
  });
});
