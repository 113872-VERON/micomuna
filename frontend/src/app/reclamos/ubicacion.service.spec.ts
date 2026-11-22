import { TestBed } from '@angular/core/testing';
import { SinUbicacion, UbicacionService } from './ubicacion.service';

describe('UbicacionService', () => {
  const original = Object.getOwnPropertyDescriptor(navigator, 'geolocation');

  function simularGps(geo: Partial<Geolocation> | undefined) {
    Object.defineProperty(navigator, 'geolocation', { value: geo, configurable: true });
  }

  afterEach(() => {
    if (original) {
      Object.defineProperty(navigator, 'geolocation', original);
    } else {
      delete (navigator as { geolocation?: unknown }).geolocation;
    }
  });

  it('devuelveLatitudLongitudYPrecision', async () => {
    const pedir = vi.fn((ok: PositionCallback, _falla?: PositionErrorCallback | null, _opciones?: PositionOptions) =>
      ok({ coords: { latitude: -31.1, longitude: -64.3, accuracy: 15 } } as GeolocationPosition),
    );
    simularGps({ getCurrentPosition: pedir });

    await expect(TestBed.inject(UbicacionService).obtener()).resolves.toEqual({ latitud: -31.1, longitud: -64.3, precision: 15 });
    expect(pedir.mock.calls[0][2]).toMatchObject({ enableHighAccuracy: true });
  });

  it('distingueElPermisoNegado', async () => {
    simularGps({
      getCurrentPosition: (_ok: PositionCallback, falla?: PositionErrorCallback | null) =>
        falla?.({ code: 1, message: 'denied' } as GeolocationPositionError),
    });

    await expect(TestBed.inject(UbicacionService).obtener()).rejects.toMatchObject({ motivo: 'permiso' });
  });

  it('otraFallaNoSeInformaComoPermiso', async () => {
    simularGps({
      getCurrentPosition: (_ok: PositionCallback, falla?: PositionErrorCallback | null) =>
        falla?.({ code: 3, message: 'timeout' } as GeolocationPositionError),
    });

    await expect(TestBed.inject(UbicacionService).obtener()).rejects.toMatchObject({ motivo: 'otro' });
  });

  it('sinGpsEnElNavegadorAvisaQueNoEstaDisponible', async () => {
    simularGps(undefined);

    const error = await TestBed.inject(UbicacionService).obtener().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(SinUbicacion);
    expect((error as SinUbicacion).motivo).toBe('no-disponible');
  });
});
