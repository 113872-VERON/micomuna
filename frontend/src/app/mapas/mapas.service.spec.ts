import { TestBed } from '@angular/core/testing';
import { MapasService } from './mapas.service';

describe('MapasService', () => {
  let servicio: MapasService;

  beforeEach(() => {
    servicio = TestBed.inject(MapasService);
  });

  it('arrancaConMapaPorqueNoHayClaveNiScriptsQueCargar', () => {
    expect(servicio.estado()).toBe('listo');
  });

  it('siElMapaNoPuedeMostrarseQuedaSinMapa', () => {
    servicio.marcarFalla();

    expect(servicio.estado()).toBe('sin-mapa');
  });

  it('reintentarVuelveAIntentarConMapa', () => {
    servicio.marcarFalla();

    servicio.reintentar();

    expect(servicio.estado()).toBe('listo');
  });
});
