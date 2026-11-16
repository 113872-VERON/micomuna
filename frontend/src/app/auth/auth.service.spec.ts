import { TestBed } from '@angular/core/testing';
import { AuthRespuesta } from './modelos';
import { AuthService } from './auth.service';

const respuesta: AuthRespuesta = {
  token: 'token-de-prueba',
  usuario: { id: 7, nombre: 'Rosa Gimenez', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
};

function crearServicio(): AuthService {
  TestBed.resetTestingModule();
  return TestBed.inject(AuthService);
}

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('guardaYRecuperaLaSesion', () => {
    crearServicio().guardarSesion(respuesta);

    const otro = crearServicio();
    expect(otro.token()).toBe('token-de-prueba');
    expect(otro.usuario()).toEqual(respuesta.usuario);
    expect(otro.estaAutenticado()).toBe(true);
    expect(localStorage.getItem('micomuna.token')).toBe('token-de-prueba');
  });

  it('cerrarSesionBorraElToken', () => {
    const servicio = crearServicio();
    servicio.guardarSesion(respuesta);

    servicio.cerrarSesion();

    expect(servicio.token()).toBeNull();
    expect(servicio.usuario()).toBeNull();
    expect(servicio.estaAutenticado()).toBe(false);
    expect(localStorage.getItem('micomuna.token')).toBeNull();
  });

  it('ignoraUnUsuarioGuardadoQueNoEsJsonValido', () => {
    localStorage.setItem('micomuna.token', 'abc');
    localStorage.setItem('micomuna.usuario', '{no es json');

    const servicio = crearServicio();

    expect(servicio.estaAutenticado()).toBe(false);
    expect(servicio.usuario()).toBeNull();
  });

  it('funcionaSiLocalStorageFalla', () => {
    const fallo = () => {
      throw new Error('bloqueado');
    };
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(fallo);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(fallo);
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(fallo);

    const servicio = crearServicio();
    servicio.guardarSesion(respuesta);

    expect(servicio.token()).toBe('token-de-prueba');
    expect(servicio.estaAutenticado()).toBe(true);
    expect(() => servicio.cerrarSesion()).not.toThrow();
    expect(servicio.estaAutenticado()).toBe(false);
  });
});
