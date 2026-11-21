import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UsuarioComunaDto } from '../auth/modelos';
import { UsuariosService } from './usuarios.service';

const julieta: UsuarioComunaDto = {
  id: 4, nombre: 'Julieta Romero', correo: 'julieta@correo.com', rol: 'AGENTE', estado: 'PENDIENTE', creadoEn: '2026-11-03T10:00:00Z',
};

describe('UsuariosService', () => {
  let http: HttpTestingController;
  let servicio: UsuariosService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(UsuariosService);
  });

  afterEach(() => http.verify());

  it('listarPideLosUsuariosDeLaComunaDelToken', () => {
    let recibidos: UsuarioComunaDto[] | undefined;
    servicio.listar().subscribe((l) => (recibidos = l));

    const pedido = http.expectOne('/api/usuarios');
    expect(pedido.request.method).toBe('GET');
    pedido.flush([julieta]);

    expect(recibidos).toEqual([julieta]);
  });

  it('crearMandaNombreCorreoYRolPorPost', () => {
    let creado: unknown;
    servicio.crear({ nombre: 'Julieta Romero', correo: 'julieta@correo.com', rol: 'AGENTE' }).subscribe((r) => (creado = r));

    const pedido = http.expectOne('/api/usuarios');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ nombre: 'Julieta Romero', correo: 'julieta@correo.com', rol: 'AGENTE' });
    pedido.flush({ usuario: julieta, invitacionEnviada: false }, { status: 201, statusText: 'Created' });

    expect(creado).toEqual({ usuario: julieta, invitacionEnviada: false });
  });

  it('actualizarMandaSoloLoQueCambiaPorPatch', () => {
    let actualizado: UsuarioComunaDto | undefined;
    servicio.actualizar(4, { activo: false }).subscribe((u) => (actualizado = u));

    const pedido = http.expectOne('/api/usuarios/4');
    expect(pedido.request.method).toBe('PATCH');
    expect(pedido.request.body).toEqual({ activo: false });
    pedido.flush({ ...julieta, estado: 'INACTIVO' });

    expect(actualizado?.estado).toBe('INACTIVO');
  });

  it('reenviarInvitacionHaceUnPostSinCuerpo', () => {
    let terminado = false;
    servicio.reenviarInvitacion(4).subscribe({ complete: () => (terminado = true) });

    const pedido = http.expectOne('/api/usuarios/4/reenviar-invitacion');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toBeNull();
    pedido.flush(null, { status: 204, statusText: 'No Content' });

    expect(terminado).toBe(true);
  });
});

describe('UsuariosService.corregirCorreoYReenviar', () => {
  let http: HttpTestingController;
  let servicio: UsuariosService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(UsuariosService);
  });

  afterEach(() => http.verify());

  it('primeroCambiaElCorreoYDespuesReenvia', () => {
    let resultado: unknown;
    servicio.corregirCorreoYReenviar(4, 'julieta.romero@correo.com').subscribe((r) => (resultado = r));

    const cambio = http.expectOne('/api/usuarios/4');
    expect(cambio.request.method).toBe('PATCH');
    expect(cambio.request.body).toEqual({ correo: 'julieta.romero@correo.com' });
    http.expectNone('/api/usuarios/4/reenviar-invitacion');
    cambio.flush({ ...julieta, correo: 'julieta.romero@correo.com' });

    http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(null, { status: 204, statusText: 'No Content' });
    expect(resultado).toEqual({ usuario: { ...julieta, correo: 'julieta.romero@correo.com' }, invitacionEnviada: true, errorEnvio: null });
  });

  it('siElEnvioFallaElCorreoNuevoQuedaIgual', () => {
    let resultado: { invitacionEnviada: boolean; usuario: UsuarioComunaDto } | undefined;
    servicio.corregirCorreoYReenviar(4, 'nuevo@correo.com').subscribe((r) => (resultado = r));

    http.expectOne('/api/usuarios/4').flush({ ...julieta, correo: 'nuevo@correo.com' });
    http.expectOne('/api/usuarios/4/reenviar-invitacion').flush(
      { detail: 'No pudimos enviar el correo.', codigo: 'CORREO_NO_ENVIADO' },
      { status: 502, statusText: 'Bad Gateway' },
    );

    expect(resultado?.invitacionEnviada).toBe(false);
    expect(resultado?.usuario.correo).toBe('nuevo@correo.com');
  });

  it('siElCambioFallaNoReenvia', () => {
    let error: unknown;
    servicio.corregirCorreoYReenviar(4, 'usado@correo.com').subscribe({ error: (e) => (error = e) });

    http.expectOne('/api/usuarios/4').flush({ detail: 'Ya hay un usuario con ese correo.' }, { status: 409, statusText: 'Conflict' });

    http.expectNone('/api/usuarios/4/reenviar-invitacion');
    expect(error).toBeTruthy();
  });
});
