import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { MiReclamoDetalleDto, MiReclamoResumenDto } from '../../../reclamos/modelos';
import { InicioVecino, soloDigitos } from './inicio-vecino';

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };

const resumen = (numero: number, extra: Partial<MiReclamoResumenDto> = {}): MiReclamoResumenDto => ({
  numero,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'RECIBIDO',
  descripcion: 'Un pozo',
  direccion: 'Av. San Martín 120',
  creadoEn: '2026-11-03T14:05:00',
  estadoCambiadoEn: '2026-11-03T14:05:00',
  idFotoPrincipal: null,
  ...extra,
});

const detalle = (extra: Partial<MiReclamoDetalleDto> = {}): MiReclamoDetalleDto => ({
  numero: 12,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'EN_REPARACION',
  descripcion: 'Un pozo',
  referencia: null,
  direccion: 'Av. San Martín 120',
  latitud: -31.1,
  longitud: -64.3,
  creadoEn: '2026-11-03T14:05:00',
  fotos: [],
  historial: [
    { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
    { estado: 'ASIGNADO', fecha: '2026-11-04T09:30:00' },
    { estado: 'EN_REPARACION', fecha: '2026-11-05T08:00:00' },
  ],
  descarte: null,
  trabajoRealizado: null,
  ...extra,
});

describe('InicioVecino', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });

  afterEach(() => http.verify());

  function crear(comuna: ComunaDto = COMUNA): ComponentFixture<InicioVecino> {
    const f = TestBed.createComponent(InicioVecino);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', comuna);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const pagina = (reclamos: MiReclamoResumenDto[], total = reclamos.length, n = 0) => ({ reclamos, pagina: n, tamanio: 10, total });
  const listaPedida = (n = 0) => http.expectOne((p) => p.url === '/api/reclamos/mios' && p.params.get('pagina') === String(n));
  const fotos = () => http.match((p) => p.url.includes('/fotos/')).forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
  const enlace = (f: ComponentFixture<unknown>, nombre: string) => Array.from(el(f).querySelectorAll('a')).find((a) => a.textContent?.includes(nombre));
  const botonVerMas = (f: ComponentFixture<unknown>) => Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes('Ver más'));

  describe('con reclamos', () => {
    it('muestraElUltimoEnGrandeConSuNumeroTipoDireccionYMiniatura', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { idFotoPrincipal: 5 }), resumen(9)]));
      http.expectOne('/api/reclamos/mios/12').flush(detalle());
      f.detectChanges();
      fotos();
      f.detectChanges();

      const ultimo = el(f).querySelector('.ultimo') as HTMLElement;
      expect(ultimo.textContent).toContain('Reclamo n.º 12');
      expect(ultimo.textContent).toContain('Bache o calle rota');
      expect(ultimo.textContent).toContain('Av. San Martín 120');
      expect(ultimo.querySelector('app-foto-reclamo img')?.getAttribute('src')).toBe('blob:foto');
      expect(enlace(f, 'Ver el detalle')?.getAttribute('href')).toBe('/salsipuedes-cordoba/reclamos/12');
    });

    it('lineaDeCuatroPasosConFechaDeCadaPasoAlcanzadoYAriaCurrentEnElActual', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'EN_REPARACION' })]));
      http.expectOne('/api/reclamos/mios/12').flush(detalle());
      f.detectChanges();

      const pasos = Array.from(el(f).querySelectorAll('.pasos li'));
      expect(pasos.length).toBe(4);
      expect(pasos[0].textContent).toContain('Recibido');
      expect(pasos[0].textContent).toContain('03/11/2026 14:05');
      expect(pasos[1].textContent).toContain('04/11/2026 09:30');
      expect(pasos[2].textContent).toContain('05/11/2026 08:00');
      expect(pasos[2].textContent).toContain('Estado actual');
      expect(pasos[3].textContent).toContain('Todavía no');
      expect(pasos.map((p) => p.getAttribute('aria-current'))).toEqual([null, null, 'step', null]);
    });

    it('losDemasVanEnTarjetasCompactasConChipDeEstadoFechaYEnlaceAlDetalle', () => {
      const f = crear();
      listaPedida().flush(
        pagina([resumen(12), resumen(9, { estado: 'RESUELTO' }), resumen(4, { estado: 'DESCARTADO', tipoNombre: 'Luz quemada', tipo: 'LUZ' })]),
      );
      http.expectOne('/api/reclamos/mios/12').flush(detalle({ estado: 'RECIBIDO', historial: [] }));
      f.detectChanges();

      const tarjetas = Array.from(el(f).querySelectorAll<HTMLAnchorElement>('a.tarjeta'));
      expect(tarjetas.length).toBe(2);
      expect(tarjetas[0].textContent).toContain('Reclamo n.º 9');
      expect(tarjetas[0].textContent).toContain('Resuelto');
      expect(tarjetas[0].textContent).toContain('03/11/2026 14:05');
      expect(tarjetas[0].getAttribute('href')).toBe('/salsipuedes-cordoba/reclamos/9');
      expect(tarjetas[1].textContent).toContain('Descartado');
      expect(tarjetas[1].textContent).toContain('Luz quemada');
      // El último no se repite entre los demás.
      expect(tarjetas.some((t) => t.textContent?.includes('n.º 12'))).toBe(false);
    });

    it('tieneElBotonDestacadoParaReportarUnProblema', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12)]));
      http.expectOne('/api/reclamos/mios/12').flush(detalle());
      f.detectChanges();

      expect(enlace(f, 'Reportar un problema')?.getAttribute('href')).toBe('/salsipuedes-cordoba/reportar');
    });

    it('verMasPideLaPaginaSiguienteYSeAgregaSinRepetirReclamos', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12), resumen(11)], 4));
      http.expectOne('/api/reclamos/mios/12').flush(detalle({ historial: [] }));
      f.detectChanges();
      expect(botonVerMas(f)).toBeDefined();

      botonVerMas(f)?.click();
      // El 11 se corrió de página porque el vecino reportó otro: no tiene que aparecer dos veces.
      listaPedida(1).flush(pagina([resumen(11), resumen(10)], 4, 1));
      f.detectChanges();
      expect(Array.from(el(f).querySelectorAll('a.tarjeta')).map((a) => a.textContent?.match(/n\.º (\d+)/)?.[1])).toEqual(['11', '10']);

      botonVerMas(f)?.click();
      listaPedida(2).flush(pagina([resumen(8)], 4, 2));
      f.detectChanges();
      expect(el(f).querySelectorAll('a.tarjeta').length).toBe(3);
    });

    it('sinMasReclamosNoHayVerMas', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12), resumen(11)]));
      http.expectOne('/api/reclamos/mios/12').flush(detalle());
      f.detectChanges();

      expect(botonVerMas(f)).toBeUndefined();
    });

    it('siFallaVerMasLoAvisaYDejaLoYaCargado', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12), resumen(11)], 5));
      http.expectOne('/api/reclamos/mios/12').flush(detalle());
      f.detectChanges();

      botonVerMas(f)?.click();
      listaPedida(1).flush(null, { status: 500, statusText: 'Error' });
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No pudimos traer más reclamos');
      expect(el(f).querySelectorAll('a.tarjeta').length).toBe(1);
      expect(botonVerMas(f)).toBeDefined();
    });
  });

  describe('descartado y resuelto', () => {
    it('descartadoMuestraElMotivoEnLenguajeClaroYLaObservacionComoTextoPlano', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({
          estado: 'DESCARTADO',
          historial: [
            { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
            { estado: 'DESCARTADO', fecha: '2026-11-06T10:00:00' },
          ],
          descarte: { motivo: 'NO_ES_DE_LA_COMUNA', observacion: '<b>Es ruta provincial</b>', estadoOriginal: null, historialOriginal: [] },
        }),
      );
      f.detectChanges();

      const caja = el(f).querySelector('.descartado') as HTMLElement;
      expect(caja.textContent).toContain('Descartado');
      expect(caja.textContent).toContain('No le corresponde a la comuna');
      expect(caja.textContent).toContain('06/11/2026 10:00');
      // Texto plano: el HTML de la comuna no se interpreta.
      expect(caja.textContent).toContain('<b>Es ruta provincial</b>');
      expect(caja.querySelector('b')).toBeNull();
      expect(el(f).querySelector('.pasos')).toBeNull();
    });

    it('unDuplicadoDiceQueYaFueReportadoYMuestraLosPasosDelOriginalSinNumero', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({
          estado: 'DESCARTADO',
          descarte: {
            motivo: 'DUPLICADO',
            observacion: 'Duplicado del n.º 7',
            estadoOriginal: 'EN_REPARACION',
            historialOriginal: [
              { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
              { estado: 'ASIGNADO', fecha: '2026-11-04T09:00:00' },
              { estado: 'EN_REPARACION', fecha: '2026-11-05T08:00:00' },
            ],
          },
        }),
      );
      f.detectChanges();

      const caja = el(f).querySelector('.duplicado') as HTMLElement;
      const texto = caja.textContent?.replace(/\s+/g, ' ') ?? '';
      expect(texto).toContain('Se detectó que este reclamo ya fue reportado');
      expect(texto).toContain('La comuna ya se está ocupando. Seguís el avance acá.');
      expect(caja.querySelectorAll('.pasos li').length).toBe(4);
      expect(texto).toContain('Asignado 04/11/2026 09:00');
      expect(texto).toContain('Resuelto Todavía no');
      expect(texto).not.toContain('n.º');
      expect(el(f).querySelector('.descartado')).toBeNull();
      expect(caja.querySelector('a')).toBeNull();
    });

    it('siElOriginalEstaDescartadoSeDiceEnPalabras', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({ estado: 'DESCARTADO', descarte: { motivo: 'DUPLICADO', observacion: null, estadoOriginal: 'DESCARTADO', historialOriginal: [{ estado: 'DESCARTADO', fecha: '2026-11-05T08:00:00' }] } }),
      );
      f.detectChanges();

      const caja = el(f).querySelector('.duplicado') as HTMLElement;
      expect(caja.textContent).toContain('La comuna descartó el reclamo que estaba siguiendo');
      expect(caja.querySelector('.pasos')).toBeNull();
    });

    it('unPasoSaltadoMuestraSoloLaTildeSinLaPalabraHecho', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'EN_REPARACION' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({ historial: [{ estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' }, { estado: 'EN_REPARACION', fecha: '2026-11-05T08:00:00' }] }),
      );
      f.detectChanges();

      const asignado = el(f).querySelectorAll('.pasos li')[1];
      expect(asignado.className).toBe('hecho');
      expect(asignado.querySelector('.punto')?.textContent?.trim()).toBe('✓');
      expect(asignado.textContent).not.toContain('Hecho');
      expect(asignado.querySelector('small')?.textContent?.trim()).toBe('');
    });

    it('otroMotivoSinObservacionInvitaAComunicarseYMuestraLosBotonesDeContacto', () => {
      const f = crear({ ...COMUNA, telefono: '543543123456', whatsapp: '5493543654321' });
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({ estado: 'DESCARTADO', descarte: { motivo: 'OTRO', observacion: null, estadoOriginal: null, historialOriginal: [] } }),
      );
      f.detectChanges();

      const caja = el(f).querySelector('.descartado') as HTMLElement;
      expect(caja.textContent).toContain('Otro motivo');
      expect(caja.textContent).toContain('Si querés saber más, comunicate con la comuna.');
      expect(caja.querySelector('a[href="tel:+543543123456"]')).not.toBeNull();
      expect(caja.querySelector('a[href="https://wa.me/5493543654321"]')?.getAttribute('rel')).toBe('noopener noreferrer');
    });

    it('otroMotivoConObservacionMuestraLaObservacionYQueHacer', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({ estado: 'DESCARTADO', descarte: { motivo: 'OTRO', observacion: 'Ya está programado.', estadoOriginal: null, historialOriginal: [] } }),
      );
      f.detectChanges();

      expect(texto(f)).toContain('Ya está programado.');
      expect(texto(f)).toContain('Si querés saber más, comunicate con la comuna.');
    });

    it('noEsDeLaComunaExplicaQueHacerConLosBotonesDeContacto', () => {
      const f = crear({ ...COMUNA, telefono: '543543123456', whatsapp: null });
      listaPedida().flush(pagina([resumen(12, { estado: 'DESCARTADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({ estado: 'DESCARTADO', descarte: { motivo: 'NO_ES_DE_LA_COMUNA', observacion: null, estadoOriginal: null, historialOriginal: [] } }),
      );
      f.detectChanges();

      const caja = el(f).querySelector('.descartado') as HTMLElement;
      expect(caja.textContent).toContain('Si creés que sí le corresponde a la comuna, comunicate con ella.');
      expect(caja.querySelector('a[href="tel:+543543123456"]')).not.toBeNull();
    });

    it('resueltoConTrabajoRealizadoLoMuestra', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'RESUELTO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(
        detalle({
          estado: 'RESUELTO',
          trabajoRealizado: 'Se rellenó el pozo con asfalto.',
          historial: [
            { estado: 'RECIBIDO', fecha: '2026-11-03T14:05:00' },
            { estado: 'RESUELTO', fecha: '2026-11-07T16:00:00' },
          ],
        }),
      );
      f.detectChanges();

      expect(el(f).querySelector('.trabajo')?.textContent).toContain('Se rellenó el pozo con asfalto.');
      expect(texto(f)).toContain('07/11/2026 16:00');
    });

    it('siElDetalleFallaLaTarjetaSigueMostrandoElEstadoSinFechas', () => {
      const f = crear();
      listaPedida().flush(pagina([resumen(12, { estado: 'ASIGNADO' })]));
      http.expectOne('/api/reclamos/mios/12').flush(null, { status: 500, statusText: 'Error' });
      f.detectChanges();

      const pasos = Array.from(el(f).querySelectorAll('.pasos li'));
      expect(pasos.length).toBe(4);
      expect(pasos[1].getAttribute('aria-current')).toBe('step');
    });
  });

  describe('sin reclamos y con errores', () => {
    it('sinReclamosHayUnMensajeAmableYElBotonParaReportar', () => {
      const f = crear();
      listaPedida().flush(pagina([]));
      f.detectChanges();

      expect(texto(f)).toContain('Todavía no reportaste nada');
      expect(enlace(f, 'Reportar un problema')?.getAttribute('href')).toBe('/salsipuedes-cordoba/reportar');
      expect(el(f).querySelector('.ultimo')).toBeNull();
      http.expectNone((p) => p.url.startsWith('/api/reclamos/mios/'));
    });

    it('siFallaLaCargaMuestraUnErrorYReintentaConElBoton', () => {
      const f = crear();
      listaPedida().flush(null, { status: 500, statusText: 'Error' });
      f.detectChanges();
      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar tus reclamos');

      Array.from(el(f).querySelectorAll('button'))
        .find((b) => b.textContent?.includes('Reintentar'))
        ?.click();
      listaPedida().flush(pagina([]));
      f.detectChanges();

      expect(texto(f)).toContain('Todavía no reportaste nada');
    });

    it('datosExtremosNoRompenElDiseno', () => {
      const largo = 'Calle Presidente Doctor Don Arturo Umberto Illia y Avenida Libertador General San Martín esquina '.repeat(3);
      const f = crear();
      listaPedida().flush(pagina([resumen(1234567, { direccion: largo }), resumen(2, { tipoNombre: 'Tipo con un nombre larguísimo para ver cómo se corta en el celular' })]));
      http.expectOne('/api/reclamos/mios/1234567').flush(detalle({ numero: 1234567 }));
      f.detectChanges();

      expect(texto(f)).toContain('Reclamo n.º 1234567');
      expect(el(f).querySelector('.direccion')?.textContent).toContain('Calle Presidente');
    });
  });

  describe('ayuda de contacto', () => {
    const conLista = (comuna: ComunaDto) => {
      const f = crear(comuna);
      listaPedida().flush(pagina([]));
      f.detectChanges();
      return f;
    };

    it('conTelefonoYWhatsappMuestraLosDosBotonesConSusEnlaces', () => {
      const f = conLista({ ...COMUNA, telefono: '543543123456', whatsapp: '5493543654321' });

      const wa = enlace(f, 'WhatsApp');
      expect(enlace(f, 'Llamar')?.getAttribute('href')).toBe('tel:+543543123456');
      expect(wa?.getAttribute('href')).toBe('https://wa.me/5493543654321');
      expect(wa?.getAttribute('target')).toBe('_blank');
      expect(wa?.getAttribute('rel')).toBe('noopener noreferrer');
    });

    it('conSoloTelefonoNoMuestraWhatsapp', () => {
      const f = conLista({ ...COMUNA, telefono: '543543123456', whatsapp: null });

      expect(enlace(f, 'Llamar')).toBeDefined();
      expect(enlace(f, 'WhatsApp')).toBeUndefined();
    });

    it('conSoloWhatsappNoMuestraLlamar', () => {
      const f = conLista({ ...COMUNA, whatsapp: '5493543654321' });

      expect(enlace(f, 'WhatsApp')).toBeDefined();
      expect(enlace(f, 'Llamar')).toBeUndefined();
    });

    it('sinNingunoDeLosDosNoMuestraLaSeccion', () => {
      const f = conLista({ ...COMUNA, telefono: null, whatsapp: '' });

      expect(texto(f)).not.toContain('¿Necesitás ayuda?');
      expect(enlace(f, 'Llamar')).toBeUndefined();
    });

    it('unNumeroConCaracteresRarosNoSeConvierteEnEnlace', () => {
      expect(soloDigitos('543543123456')).toBe('543543123456');
      expect(soloDigitos('javascript:alert(1)')).toBeNull();
      expect(soloDigitos('54 354 3123456')).toBeNull();
      expect(soloDigitos('')).toBeNull();
      expect(soloDigitos(undefined)).toBeNull();
    });
  });
});
