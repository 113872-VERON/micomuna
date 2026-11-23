import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { MiReclamoDetalleDto } from '../../../reclamos/modelos';
import { DetalleReclamo } from './detalle-reclamo';

// Leaflet no se monta en las pruebas: este mapa falso tiene las mismas entradas y salidas.
@Component({ selector: 'app-mapa-ubicacion', template: '<div class="mapa-falso"></div>' })
class MapaFalso {
  readonly vista = input.required<VistaMapa>();
  readonly limite = input<unknown>(null);
  readonly interactivo = input(true);
  readonly altura = input('300px');
  readonly etiqueta = input('Mapa');
  readonly falla = output<void>();
}

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };

const reclamo = (extra: Partial<MiReclamoDetalleDto> = {}): MiReclamoDetalleDto => ({
  numero: 12,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'ASIGNADO',
  descripcion: 'Hay un pozo grande frente a la panadería.',
  referencia: 'Frente a la panadería',
  direccion: 'Av. San Martín 120',
  latitud: -31.123456,
  longitud: -64.299123,
  creadoEn: '2026-11-03T14:05:00',
  fotos: [],
  historial: [
    { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
    { estado: 'ASIGNADO', fecha: '2026-11-04T09:30:00' },
  ],
  descarte: null,
  trabajoRealizado: null,
  ...extra,
});

describe('DetalleReclamo', () => {
  let http: HttpTestingController;
  let liberadas: string[];

  beforeEach(() => {
    liberadas = [];
    let n = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:foto-${++n}`);
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((u) => void liberadas.push(u));
    TestBed.overrideComponent(DetalleReclamo, { remove: { imports: [MapaUbicacion] }, add: { imports: [MapaFalso] } });
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    localStorage.clear();
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  function crear(numero = '12'): ComponentFixture<DetalleReclamo> {
    const f = TestBed.createComponent(DetalleReclamo);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', COMUNA);
    f.componentRef.setInput('numero', numero);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const flushFotos = () => http.match((p) => p.url.includes('/fotos/')).forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
  const seccion = (f: ComponentFixture<unknown>, titulo: string) =>
    Array.from(el(f).querySelectorAll('section')).find((s) => s.querySelector('h2')?.textContent?.trim() === titulo);

  it('pideElReclamoPorNumeroYMuestraSusDatos', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 12');
    expect(texto(f)).toContain('Bache o calle rota');
    expect(texto(f)).toContain('Hay un pozo grande frente a la panadería.');
    expect(texto(f)).toContain('Frente a la panadería');
    expect(texto(f)).toContain('Av. San Martín 120');
    expect(texto(f)).toContain('03/11/2026 14:05');
    expect(el(f).querySelectorAll('.pasos li').length).toBe(4);
    expect(el(f).querySelector('.pasos li[aria-current="step"]')?.textContent).toContain('Asignado');
  });

  it('muestraUnMapaChicoDeSoloLecturaEnElLugarDelReclamo', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    const mapa = f.debugElement.query(By.directive(MapaFalso)).componentInstance as MapaFalso;
    expect(mapa.interactivo()).toBe(false);
    expect(mapa.vista()).toEqual({ centro: { latitud: -31.123456, longitud: -64.299123 }, zoom: 16 });
  });

  it('siElMapaFallaSeOcultaYElRestoSigue', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    (f.debugElement.query(By.directive(MapaFalso)).componentInstance as MapaFalso).falla.emit();
    f.detectChanges();

    expect(f.debugElement.query(By.directive(MapaFalso))).toBeNull();
    expect(texto(f)).toContain('Av. San Martín 120');
  });

  it('separaLasFotosDelVecinoDeLasDelTrabajoYPideCadaImagenConElServicio', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'RESUELTO',
        fotos: [
          { id: 101, origen: 'VECINO', orden: 1 },
          { id: 102, origen: 'VECINO', orden: 2 },
          { id: 205, origen: 'TRABAJO', orden: 1 },
        ],
      }),
    );
    f.detectChanges();
    expect(http.match((p) => p.url.includes('/fotos/')).map((p) => p.request.url).sort()).toEqual([
      '/api/reclamos/mios/12/fotos/101',
      '/api/reclamos/mios/12/fotos/102',
      '/api/reclamos/mios/12/fotos/205',
    ]);
  });

  it('lasFotosDelVecinoYDelTrabajoVanEnSeccionesAparte', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'RESUELTO',
        fotos: [
          { id: 101, origen: 'VECINO', orden: 1 },
          { id: 205, origen: 'TRABAJO', orden: 1 },
          { id: 206, origen: 'TRABAJO', orden: 2 },
        ],
      }),
    );
    f.detectChanges();
    flushFotos();
    f.detectChanges();

    expect(seccion(f, 'Tus fotos')?.querySelectorAll('img').length).toBe(1);
    expect(seccion(f, 'Fotos del trabajo')?.querySelectorAll('img').length).toBe(2);
    expect(seccion(f, 'Fotos del trabajo')?.querySelector('img')?.getAttribute('alt')).toBe('Foto 1 del trabajo');
  });

  it('sinFotosDelTrabajoNoApareceLaSeccion', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }] }));
    f.detectChanges();
    flushFotos();
    f.detectChanges();

    expect(seccion(f, 'Fotos del trabajo')).toBeUndefined();
    expect(seccion(f, 'Tus fotos')).toBeDefined();
  });

  it('lasFotosDelTrabajoSoloSeMuestranConElReclamoResuelto', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({ estado: 'EN_REPARACION', fotos: [{ id: 101, origen: 'VECINO', orden: 1 }, { id: 205, origen: 'TRABAJO', orden: 1 }] }),
    );
    f.detectChanges();

    expect(seccion(f, 'Fotos del trabajo')).toBeUndefined();
    http.expectNone('/api/reclamos/mios/12/fotos/205');
    http.expectOne('/api/reclamos/mios/12/fotos/101').flush(new Blob(['x'], { type: 'image/jpeg' }));
  });

  it('sinNingunaFotoNoApareceNingunaSeccionDeFotos', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    expect(seccion(f, 'Tus fotos')).toBeUndefined();
    expect(seccion(f, 'Fotos del trabajo')).toBeUndefined();
  });

  it('seLiberanLasUrlsDeObjetoAlSalirDeLaPantalla', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }] }));
    f.detectChanges();
    flushFotos();
    f.detectChanges();
    expect(liberadas).toEqual([]);

    f.destroy();

    expect(liberadas).toEqual(['blob:foto-1']);
  });

  it('unaFotoQueNoCargaMuestraUnTextoEnLugarDeUnaImagenRota', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }] }));
    f.detectChanges();
    http.expectOne('/api/reclamos/mios/12/fotos/101').flush(null, { status: 404, statusText: 'No encontrada' });
    f.detectChanges();

    expect(el(f).querySelector('img')).toBeNull();
    expect(texto(f)).toContain('No se pudo cargar la foto');
  });

  it('elHistorialCompletoVaEnOrdenConFechaYHora', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'EN_REPARACION',
        historial: [
          { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
          { estado: 'ASIGNADO', fecha: '2026-11-04T09:30:00' },
          { estado: 'EN_REPARACION', fecha: '2026-11-05T08:00:00' },
        ],
      }),
    );
    f.detectChanges();

    const filas = Array.from(seccion(f, 'Historial')?.querySelectorAll('li') ?? []).map((li) => Array.from(li.children).map((c) => c.textContent?.trim()).join(' '));
    expect(filas).toEqual(['Recibido 03/11/2026 14:05', 'Asignado 04/11/2026 09:30', 'En reparación 05/11/2026 08:00']);
  });

  it('siUnReclamoVolvioAUnEstadoAnteriorElHistorialLoMuestra', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'ASIGNADO',
        historial: [
          { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
          { estado: 'ASIGNADO', fecha: '2026-11-04T09:30:00' },
          { estado: 'EN_REPARACION', fecha: '2026-11-05T08:00:00' },
          { estado: 'ASIGNADO', fecha: '2026-11-06T11:15:00' },
        ],
      }),
    );
    f.detectChanges();

    const filas = Array.from(seccion(f, 'Historial')?.querySelectorAll('li') ?? []).map((li) => Array.from(li.children).map((c) => c.textContent?.trim()).join(' '));
    expect(filas.length).toBe(4);
    expect(filas[3]).toBe('Asignado La comuna lo volvió a este estado 06/11/2026 11:15');
    expect(filas[1]).not.toContain('La comuna lo volvió');
    // La línea de pasos refleja el estado actual: En reparación ya no está hecho.
    expect(Array.from(el(f).querySelectorAll('.pasos li')).map((p) => p.className)).toEqual(['hecho', 'actual', 'pendiente', 'pendiente']);
  });

  it('descartadoMuestraElMotivoYNoLosCuatroPasos', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'DESCARTADO',
        historial: [
          { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
          { estado: 'DESCARTADO', fecha: '2026-11-04T10:00:00' },
        ],
        descarte: { motivo: 'NO_SE_PUEDE_ATENDER', observacion: 'Es un terreno privado.', estadoOriginal: null, historialOriginal: [] },
      }),
    );
    f.detectChanges();

    expect(el(f).querySelector('.pasos')).toBeNull();
    expect(el(f).querySelector('.descartado')?.textContent).toContain('La comuna no lo puede atender');
    expect(el(f).querySelector('.descartado')?.textContent).toContain('Es un terreno privado.');
  });

  it('unDuplicadoExplicaYMuestraElAvanceDelOriginalSinSuNumero', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(
      reclamo({
        estado: 'DESCARTADO',
        historial: [
          { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
          { estado: 'DESCARTADO', fecha: '2026-11-04T10:00:00' },
        ],
        descarte: {
          motivo: 'DUPLICADO',
          observacion: 'Duplicado del n.º 7',
          estadoOriginal: 'ASIGNADO',
          historialOriginal: [
            { estado: 'RECIBIDO', fecha: '2026-11-01T08:00:00' },
            { estado: 'ASIGNADO', fecha: '2026-11-02T09:30:00' },
          ],
        },
      }),
    );
    f.detectChanges();

    const caja = el(f).querySelector('.duplicado') as HTMLElement;
    const texto = caja.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(texto).toContain('Se detectó que este reclamo ya fue reportado');
    expect(texto).toContain('Otro vecino ya avisó de este mismo problema y la comuna se está ocupando. No hace falta que hagas nada más.');
    expect(texto).toContain('Seguí el avance del problema');
    expect(caja.querySelectorAll('.pasos li').length).toBe(4);
    expect(texto).toContain('Recibido 01/11/2026 08:00');
    expect(texto).toContain('Asignado 02/11/2026 09:30');
    expect(texto).not.toContain('7');
    expect(el(f).querySelector('.descartado')).toBeNull();
  });

  it('avisaQueLasNovedadesLlegaranPorCorreo', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    expect(el(f).textContent).toContain('Te avisamos por correo cada vez que haya novedades.');
  });

  it('resueltoMuestraLoQueSeHizo', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo({ estado: 'RESUELTO', trabajoRealizado: 'Se tapó el pozo.' }));
    f.detectChanges();

    expect(el(f).querySelector('.trabajo')?.textContent).toContain('Se tapó el pozo.');
  });

  it('unReclamoQueNoExisteOEsDeOtroDa404ConMensajeYBotonParaVolver', () => {
    const f = crear('999');
    http.expectOne('/api/reclamos/mios/999').flush({ codigo: 'NO_ENCONTRADO' }, { status: 404, statusText: 'No encontrado' });
    f.detectChanges();

    expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
    const volver = Array.from(el(f).querySelectorAll('a.btn')).find((a) => a.textContent?.includes('Volver al inicio'));
    expect(volver?.getAttribute('href')).toBe('/salsipuedes-cordoba/inicio');
  });

  it('unNumeroQueNoEsUnNumeroNoPideNadaYDice404', () => {
    const f = crear('abc');
    f.detectChanges();

    http.expectNone((p) => p.url.startsWith('/api/reclamos/mios'));
    expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
  });

  it('unErrorDeRedNoSeDisfrazaDe404YPermiteReintentar', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(null, { status: 500, statusText: 'Error' });
    f.detectChanges();
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar el reclamo');
    expect(texto(f)).not.toContain('No encontramos ese reclamo');

    Array.from(el(f).querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Reintentar'))
      ?.click();
    f.detectChanges();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 12');
  });

  it('tieneElBotonVolverAlInicioArribaYAbajo', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo());
    f.detectChanges();

    const enlaces = Array.from(el(f).querySelectorAll('a')).filter((a) => a.textContent?.includes('Volver al inicio'));
    expect(enlaces.length).toBe(2);
    expect(enlaces.every((a) => a.getAttribute('href') === '/salsipuedes-cordoba/inicio')).toBe(true);
  });

  it('textoLargoDelVecinoOLaComunaSeMuestraComoTextoPlano', () => {
    const f = crear();
    http.expectOne('/api/reclamos/mios/12').flush(reclamo({ descripcion: '<img src=x onerror=alert(1)> ' + 'pozo '.repeat(100), referencia: '<script>alert(1)</script>' }));
    f.detectChanges();

    expect(el(f).querySelector('img')).toBeNull();
    expect(el(f).querySelector('script')).toBeNull();
    expect(texto(f)).toContain('<script>alert(1)</script>');
  });
});
