import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { codigoDeError, mensajeDeAgente } from './errores-agente';
import { ReclamosAgenteService } from './reclamos-agente.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('ReclamosAgenteService', () => {
  let servicio: ReclamosAgenteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ReclamosAgenteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listaPorGrupoYPagina', () => {
    servicio.listar('RESUELTOS', 2, 50).subscribe();

    const p = http.expectOne((r) => r.url === '/api/agente/reclamos');
    expect(p.request.method).toBe('GET');
    expect(p.request.params.get('grupo')).toBe('RESUELTOS');
    expect(p.request.params.get('pagina')).toBe('2');
    expect(p.request.params.get('tamanio')).toBe('50');
    // Nunca viaja la ubicación del agente.
    expect(p.request.params.keys().sort()).toEqual(['grupo', 'pagina', 'tamanio']);
    p.flush({ reclamos: [], pagina: 2, tamanio: 50, total: 0 });
  });

  it('pideElDetalleYLaFotoComoBlob', () => {
    servicio.detalle(12).subscribe();
    http.expectOne('/api/agente/reclamos/12').flush({});
    servicio.foto(12, 5).subscribe();
    expect(http.expectOne('/api/agente/reclamos/12/fotos/5').request.responseType).toBe('blob');
  });

  it('empezarEsUnPostSinCuerpo', () => {
    servicio.empezar(12).subscribe();

    const p = http.expectOne('/api/agente/reclamos/12/empezar');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toBeNull();
    p.flush({});
  });

  it('resolverSinNadaMandaElFormularioVacio', () => {
    servicio.resolver(12, '   ', null).subscribe();

    const p = http.expectOne('/api/agente/reclamos/12/resolver');
    const datos = p.request.body as FormData;
    expect(datos.has('nota')).toBe(false);
    expect(datos.has('foto')).toBe(false);
    p.flush({});
  });

  it('resolverConNotaYFotoLasMandaEnMultipart', () => {
    servicio.resolver(12, '  Se tapó el pozo.  ', new Blob(['x'], { type: 'image/jpeg' })).subscribe();

    const datos = http.expectOne('/api/agente/reclamos/12/resolver').request.body as FormData;
    expect(datos.get('nota')).toBe('Se tapó el pozo.');
    expect(datos.get('foto')).toBeInstanceOf(Blob);
  });

  it('laNotaSeCortaEnMilCaracteres', () => {
    servicio.resolver(12, 'a'.repeat(1500), null).subscribe();

    expect(((http.expectOne('/api/agente/reclamos/12/resolver').request.body as FormData).get('nota') as string).length).toBe(1000);
  });
});

describe('mensajeDeAgente', () => {
  const error = (status: number, cuerpo: unknown) => new HttpErrorResponse({ status, error: cuerpo });

  it('cadaCodigoTieneSuTextoEnEspanol', () => {
    expect(mensajeDeAgente(error(400, { codigo: 'FOTO_INVALIDA' }))).toContain('No pudimos usar esa foto');
    expect(mensajeDeAgente(error(503, { codigo: 'OCUPADO' }))).toContain('se están recibiendo muchas fotos');
    expect(mensajeDeAgente(error(409, { codigo: 'TRANSICION_INVALIDA' }))).toContain('ya cambió de estado');
    expect(mensajeDeAgente(error(409, { codigo: 'CONFLICTO' }))).toContain('Otra persona está cambiando');
  });

  it('unCincuentaYTresSinCodigoTambienEsOcupado', () => {
    expect(mensajeDeAgente(error(503, null))).toContain('se están recibiendo muchas fotos');
  });

  it('unCodigoDesconocidoUsaElMensajeGeneral', () => {
    expect(mensajeDeAgente(error(500, { codigo: 'RARO', detail: 'Algo pasó' }))).toBe('Algo pasó');
    expect(codigoDeError(error(500, null))).toBe('');
  });
});
