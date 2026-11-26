import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { ReclamoAgenteDetalleDto } from '../../../agente/modelos-agente';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { ACHICAR_FOTO } from '../../../reclamos/achicar-foto';
import { DetalleAgente } from './detalle-agente';

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
const URL_DETALLE = '/api/agente/reclamos/12';

const reclamo = (extra: Partial<ReclamoAgenteDetalleDto> = {}): ReclamoAgenteDetalleDto => ({
  numero: 12,
  tipo: 'BACHE',
  tipoNombre: 'Bache o calle rota',
  estado: 'ASIGNADO',
  descripcion: 'Hay un pozo grande frente a la panadería.',
  referencia: 'Frente a la panadería',
  direccion: 'Av. San Martín 120',
  latitud: -31.123456,
  longitud: -64.299123,
  creadoEn: '2026-11-03T09:12:00',
  fotos: [],
  trabajoRealizado: null,
  ...extra,
});

describe('DetalleAgente', () => {
  let http: HttpTestingController;
  let liberadas: string[];
  // Cómo "achica" la foto en esta prueba (sin canvas): por defecto la deja igual.
  let achicar: (b: Blob) => Promise<Blob>;

  beforeEach(() => {
    localStorage.clear();
    liberadas = [];
    achicar = async (b) => b;
    let n = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:foto-${++n}`);
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((u) => void liberadas.push(u));
    TestBed.overrideComponent(DetalleAgente, { remove: { imports: [MapaUbicacion] }, add: { imports: [MapaFalso] } });
    // La foto se achica con el canvas del navegador: en las pruebas pasa tal cual.
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ACHICAR_FOTO, useValue: (b: Blob) => achicar(b) }],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 5, nombre: 'Juan Agente', correo: 'juan@correo.com', rol: 'AGENTE', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  function crear(numero = '12', datos: ReclamoAgenteDetalleDto | 'no-existe' | 'falla' = reclamo()): ComponentFixture<DetalleAgente> {
    const f = TestBed.createComponent(DetalleAgente);
    f.componentRef.setInput('slug', 'salsipuedes-cordoba');
    f.componentRef.setInput('comuna', COMUNA);
    f.componentRef.setInput('numero', numero);
    f.detectChanges();
    if (/^\d+$/.test(numero) && Number(numero) > 0) {
      const pedido = http.expectOne(`/api/agente/reclamos/${numero}`);
      if (datos === 'no-existe') {
        pedido.flush({ codigo: 'NO_ENCONTRADO' }, { status: 404, statusText: 'No encontrado' });
      } else if (datos === 'falla') {
        pedido.flush(null, { status: 500, statusText: 'Error' });
      } else {
        pedido.flush(datos);
      }
    }
    f.detectChanges();
    return f;
  }

  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const boton = (f: ComponentFixture<unknown>, nombre: string) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim() === nombre) as HTMLButtonElement | undefined;
  const botonesDeAccion = (f: ComponentFixture<unknown>) => Array.from(el(f).querySelectorAll('button.accion')).map((b) => b.textContent?.trim());
  const fotos = () => http.match((p) => p.url.includes('/fotos/'));
  const dialogo = (f: ComponentFixture<unknown>) => el(f).querySelector('app-dialogo-resolver') as HTMLElement | null;

  describe('lo que muestra', () => {
    it('muestraLaPrioridadEfectivaSinDecirQuienLaFijo', () => {
      const f = crear('12', reclamo({ prioridad: { valor: 'ALTA', origen: 'PERSONAL' } }));

      expect(el(f).querySelector('.titulo app-chip-prioridad')?.textContent?.trim()).toBe('Alta');
      expect(texto(f)).not.toContain('Fijada por');
    });

    it('sinPrioridadDiceSinPrioridad', () => {
      const f = crear('12', reclamo({ prioridad: { valor: null, origen: 'NINGUNA' } }));

      expect(el(f).querySelector('.titulo app-chip-prioridad')?.textContent?.trim()).toBe('Sin prioridad');
    });

    it('laSugeridaPorLaIALlevaLaMarcaYElAgenteNoPuedeCambiarla', () => {
      const f = crear('12', reclamo({ prioridad: { valor: 'MEDIA', origen: 'IA' } }));

      expect(el(f).querySelector('.titulo app-chip-prioridad .marca')?.textContent).toBe('Sugerida');
      expect(el(f).querySelector('button.opcion')).toBeNull();
    });

    it('mostrarTipoDescripcionDireccionReferenciaYFecha', () => {
      const f = crear();

      expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 12');
      expect(el(f).querySelector('app-chip-estado')?.textContent?.trim()).toBe('Asignado');
      expect(texto(f)).toContain('Bache o calle rota');
      expect(texto(f)).toContain('Hay un pozo grande frente a la panadería.');
      expect(texto(f)).toContain('Frente a la panadería');
      expect(texto(f)).toContain('Av. San Martín 120');
      expect(texto(f)).toContain('3/11/2026 9:12');
    });

    it('nuncaMuestraDatosDelVecino', () => {
      const f = crear();

      expect(texto(f)).not.toContain('Reportado por');
    });

    it('lasFotosDelVecinoSeBajanDelEndpointDelAgenteConElToken', () => {
      const f = crear('12', reclamo({ fotos: [{ id: 101, origen: 'VECINO', orden: 1 }, { id: 102, origen: 'VECINO', orden: 2 }] }));

      const pedidos = fotos();
      expect(pedidos.map((p) => p.request.url).sort()).toEqual(['/api/agente/reclamos/12/fotos/101', '/api/agente/reclamos/12/fotos/102']);
      expect(pedidos.every((p) => p.request.responseType === 'blob')).toBe(true);
      pedidos.forEach((p) => p.flush(new Blob(['x'], { type: 'image/jpeg' })));
      f.detectChanges();

      expect(el(f).querySelectorAll('.fotos img').length).toBe(2);
      expect(el(f).querySelector('.fotos img')?.getAttribute('alt')).toBe('Foto 1 que subió el vecino');
    });

    it('conCoordenadasMuestraElMapa', () => {
      const f = crear();

      expect(texto(f)).not.toContain('Sin ubicación en el mapa');
      expect(f.debugElement.query(By.directive(MapaFalso))).not.toBeNull();
    });

    it('sinCoordenadasNoMuestraMapaNiComoLlegarPeroLoExplica', () => {
      const f = crear('12', reclamo({ latitud: null, longitud: null }));

      expect(f.debugElement.query(By.directive(MapaFalso))).toBeNull();
      expect(texto(f)).not.toContain('Cómo llegar');
      expect(texto(f)).toContain('Sin ubicación en el mapa: guiate por la dirección y la referencia.');
    });

    it('losTextosDelVecinoSeMuestranComoTextoPlano', () => {
      const f = crear('12', reclamo({ descripcion: '<img src=x onerror=alert(1)>pozo', referencia: '<script>alert(1)</script>' }));

      expect(el(f).querySelector('.bloque img')).toBeNull();
      expect(el(f).querySelector('script')).toBeNull();
      expect(texto(f)).toContain('<script>alert(1)</script>');
    });

    it('unReclamoInexistenteODeOtroAgenteDa404ConBotonParaVolver', () => {
      const f = crear('999', 'no-existe');

      expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
      expect(el(f).querySelector('.vacio a')?.getAttribute('href')).toBe('/salsipuedes-cordoba/agente');
    });

    it('unNumeroQueNoEsNumeroNoPideNadaYDice404', () => {
      const f = crear('abc');

      expect(el(f).querySelector('h1')?.textContent).toBe('No encontramos ese reclamo');
    });

    it('unErrorDeRedNoSeDisfrazaDe404YPermiteReintentar', () => {
      const f = crear('12', 'falla');

      expect(texto(f)).toContain('No pudimos cargar el reclamo');
      expect(texto(f)).not.toContain('No encontramos');
      boton(f, 'Reintentar')!.click();
      f.detectChanges();
      http.expectOne(URL_DETALLE).flush(reclamo());
      f.detectChanges();

      expect(el(f).querySelector('h1')?.textContent).toContain('Reclamo n.º 12');
    });
  });

  describe('cómo llegar', () => {
    it('esUnEnlaceAGoogleMapsConElDestinoQueSeAbreEnOtraPestanaDeFormaSegura', () => {
      const f = crear();

      const enlace = Array.from(el(f).querySelectorAll('a')).find((a) => a.textContent?.includes('Cómo llegar')) as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('https://www.google.com/maps/dir/?api=1&destination=-31.123456,-64.299123');
      expect(enlace.target).toBe('_blank');
      expect(enlace.rel).toBe('noopener noreferrer');
      expect(enlace.textContent).toContain('se abre en otra pestaña');
    });
  });

  describe('el botón de acción según el estado', () => {
    it('asignadoTieneSoloEmpezarATrabajar', () => {
      const f = crear('12', reclamo({ estado: 'ASIGNADO' }));

      expect(botonesDeAccion(f)).toEqual(['Empezar a trabajar']);
    });

    it('enReparacionTieneSoloMarcarComoResuelto', () => {
      const f = crear('12', reclamo({ estado: 'EN_REPARACION' }));

      expect(botonesDeAccion(f)).toEqual(['Marcar como resuelto']);
    });

    it('resueltoNoTieneBotonYMuestraLoQueSeHizoConSuFoto', () => {
      const f = crear('12', reclamo({ estado: 'RESUELTO', trabajoRealizado: 'Se tapó el pozo con asfalto.', fotos: [{ id: 201, origen: 'TRABAJO', orden: 1 }] }));

      expect(botonesDeAccion(f)).toEqual([]);
      expect(texto(f)).toContain('Lo que se hizo');
      expect(texto(f)).toContain('Se tapó el pozo con asfalto.');
      const pedidos = fotos();
      expect(pedidos.map((p) => p.request.url)).toEqual(['/api/agente/reclamos/12/fotos/201']);
      pedidos[0].flush(new Blob(['x'], { type: 'image/jpeg' }));
      f.detectChanges();
      expect(el(f).querySelector('section[aria-labelledby="t-trabajo"] img')?.getAttribute('alt')).toBe('Foto 1 del trabajo');
    });

    it('resueltoSinNotaLoDice', () => {
      const f = crear('12', reclamo({ estado: 'RESUELTO' }));

      expect(texto(f)).toContain('Sin nota.');
    });

    it('lasFotosDelTrabajoSoloSeVenConElReclamoResuelto', () => {
      const f = crear('12', reclamo({ estado: 'EN_REPARACION', fotos: [{ id: 201, origen: 'TRABAJO', orden: 1 }] }));

      expect(fotos().length).toBe(0);
      expect(el(f).querySelector('#t-trabajo')).toBeNull();
    });
  });

  describe('empezar a trabajar', () => {
    it('pasaAEnReparacionYCambiaElBoton', () => {
      const f = crear();

      boton(f, 'Empezar a trabajar')!.click();
      const pedido = http.expectOne(`${URL_DETALLE}/empezar`);
      expect(pedido.request.method).toBe('POST');
      pedido.flush(reclamo({ estado: 'EN_REPARACION' }));
      f.detectChanges();

      expect(el(f).querySelector('app-chip-estado')?.textContent?.trim()).toBe('En reparación');
      expect(botonesDeAccion(f)).toEqual(['Marcar como resuelto']);
    });

    it('mientrasGuardaNoSePuedeTocarDeNuevo', () => {
      const f = crear();

      boton(f, 'Empezar a trabajar')!.click();
      f.detectChanges();

      expect(boton(f, 'Guardando…')!.disabled).toBe(true);
      http.expectOne(`${URL_DETALLE}/empezar`).flush(reclamo({ estado: 'EN_REPARACION' }));
    });

    it('siElReclamoYaCambioLoDiceYVuelveAPedirElDetalle', () => {
      const f = crear();

      boton(f, 'Empezar a trabajar')!.click();
      http.expectOne(`${URL_DETALLE}/empezar`).flush({ codigo: 'TRANSICION_INVALIDA' }, { status: 409, statusText: 'Conflicto' });
      f.detectChanges();
      http.expectOne(URL_DETALLE).flush(reclamo({ estado: 'EN_REPARACION' }));
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('ya cambió de estado');
      expect(botonesDeAccion(f)).toEqual(['Marcar como resuelto']);
    });

    it('siOtraPersonaLoEstabaCambiandoLoExplica', () => {
      const f = crear();

      boton(f, 'Empezar a trabajar')!.click();
      http.expectOne(`${URL_DETALLE}/empezar`).flush({ codigo: 'CONFLICTO' }, { status: 409, statusText: 'Conflicto' });
      f.detectChanges();
      http.expectOne(URL_DETALLE).flush(reclamo());
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('Otra persona está cambiando');
    });
  });

  describe('marcar como resuelto', () => {
    const abrir = () => {
      const f = crear('12', reclamo({ estado: 'EN_REPARACION' }));
      boton(f, 'Marcar como resuelto')!.click();
      f.detectChanges();
      return f;
    };
    const botonDialogo = (f: ComponentFixture<unknown>, nombre: string) =>
      Array.from(dialogo(f)!.querySelectorAll('button')).find((b) => b.textContent?.trim() === nombre) as HTMLButtonElement;
    const escribirNota = (f: ComponentFixture<unknown>, valor: string) => {
      const t = dialogo(f)!.querySelector('textarea') as HTMLTextAreaElement;
      t.value = valor;
      t.dispatchEvent(new Event('input'));
      f.detectChanges();
    };
    async function elegirFoto(f: ComponentFixture<unknown>, que: 'camara' | 'galeria' = 'galeria'): Promise<void> {
      const entrada = dialogo(f)!.querySelector(que === 'camara' ? 'input[capture]' : 'input[type="file"]:not([capture])') as HTMLInputElement;
      Object.defineProperty(entrada, 'files', { value: [new File(['x'], 'trabajo.jpg', { type: 'image/jpeg' })], configurable: true });
      entrada.dispatchEvent(new Event('change'));
      await new Promise((r) => setTimeout(r));
      f.detectChanges();
    }
    const resolver = (f: ComponentFixture<unknown>): TestRequest => {
      botonDialogo(f, 'Resuelto').click();
      return http.expectOne(`${URL_DETALLE}/resolver`);
    };

    it('abreUnaVentanaConNotaFotoResueltoYCancelar', () => {
      const f = abrir();

      expect(dialogo(f)?.textContent).toContain('Nota de lo que se hizo (opcional)');
      expect(dialogo(f)?.textContent).toContain('Foto del trabajo (opcional)');
      expect(botonDialogo(f, 'Resuelto')).toBeDefined();
      expect(botonDialogo(f, 'Cancelar').classList).toContain('cancelar');
      // El aviso de que el vecino recibe un correo llega con MC-22.
      expect(dialogo(f)?.textContent).not.toContain('correo');
    });

    it('laFotoSePuedeSacarConLaCamaraOElegirDeLaGaleria', () => {
      const f = abrir();

      expect(dialogo(f)!.querySelector('input[type="file"][capture="environment"]')).not.toBeNull();
      expect(dialogo(f)!.querySelector('input[type="file"]:not([capture])')).not.toBeNull();
      expect(botonDialogo(f, 'Sacar una foto')).toBeDefined();
      expect(botonDialogo(f, 'Elegir una foto del teléfono')).toBeDefined();
    });

    it('elContadorDeLaNotaLlegaAMil', () => {
      const f = abrir();

      expect(dialogo(f)?.textContent).toContain('0/1000');
      escribirNota(f, 'Se tapó el pozo.');
      expect(dialogo(f)?.textContent).toContain('16/1000');
      expect(dialogo(f)!.querySelector('textarea')!.getAttribute('maxlength')).toBe('1000');
    });

    it('sinNotaNiFotoSeResuelveIgualYElFormularioVaVacio', () => {
      const f = abrir();

      const pedido = resolver(f);
      const datos = pedido.request.body as FormData;
      expect(pedido.request.method).toBe('POST');
      expect(datos.has('nota')).toBe(false);
      expect(datos.has('foto')).toBe(false);
      pedido.flush(reclamo({ estado: 'RESUELTO' }));
      f.detectChanges();

      expect(dialogo(f)).toBeNull();
      expect(el(f).querySelector('app-chip-estado')?.textContent?.trim()).toBe('Resuelto');
      expect(botonesDeAccion(f)).toEqual([]);
    });

    it('conNotaLaManda', () => {
      const f = abrir();
      escribirNota(f, '  Se tapó el pozo.  ');

      const pedido = resolver(f);

      expect((pedido.request.body as FormData).get('nota')).toBe('Se tapó el pozo.');
      expect((pedido.request.body as FormData).has('foto')).toBe(false);
      pedido.flush(reclamo({ estado: 'RESUELTO', trabajoRealizado: 'Se tapó el pozo.' }));
      f.detectChanges();
      expect(texto(f)).toContain('Se tapó el pozo.');
    });

    it('conFotoLaMuestraYLaManda', async () => {
      const f = abrir();
      await elegirFoto(f);

      expect(dialogo(f)!.querySelector('img.vista')?.getAttribute('src')).toBe('blob:foto-1');
      expect(botonDialogo(f, 'Quitar la foto')).toBeDefined();
      const pedido = resolver(f);

      expect((pedido.request.body as FormData).get('foto')).toBeInstanceOf(Blob);
      expect((pedido.request.body as FormData).has('nota')).toBe(false);
      pedido.flush(reclamo({ estado: 'RESUELTO' }));
    });

    it('conNotaYFotoMandaLasDos', async () => {
      const f = abrir();
      escribirNota(f, 'Listo.');
      await elegirFoto(f, 'camara');

      const datos = resolver(f).request.body as FormData;

      expect(datos.get('nota')).toBe('Listo.');
      expect(datos.has('foto')).toBe(true);
    });

    it('quitarLaFotoLaSacaDelEnvioYLiberaLaVista', async () => {
      const f = abrir();
      await elegirFoto(f);

      botonDialogo(f, 'Quitar la foto').click();
      f.detectChanges();

      expect(liberadas).toContain('blob:foto-1');
      expect((resolver(f).request.body as FormData).has('foto')).toBe(false);
    });

    it('unaFotoQueNoSePuedeAchicarAvisaYNoSeEnvia', async () => {
      achicar = () => Promise.reject(new Error('no es imagen'));
      const f = abrir();
      await elegirFoto(f);

      expect(dialogo(f)?.textContent).toContain('No pudimos usar esa foto');
      expect(dialogo(f)!.querySelector('img.vista')).toBeNull();
    });

    it('cancelarCierraSinEnviarNada', () => {
      const f = abrir();

      botonDialogo(f, 'Cancelar').click();
      f.detectChanges();

      expect(dialogo(f)).toBeNull();
      http.expectNone(`${URL_DETALLE}/resolver`);
    });

    it('mientrasEnviaNoSePuedeCancelarNiEnviarDeNuevo', () => {
      const f = abrir();

      const pedido = resolver(f);
      f.detectChanges();

      expect(botonDialogo(f, 'Guardando…').disabled).toBe(true);
      expect(botonDialogo(f, 'Cancelar').disabled).toBe(true);
      pedido.flush(reclamo({ estado: 'RESUELTO' }));
    });

    const errores: [number, string, string][] = [
      [400, 'FOTO_INVALIDA', 'No pudimos usar esa foto. Sacá otra o elegí una distinta, o resolvé sin foto.'],
      [503, 'OCUPADO', 'En este momento se están recibiendo muchas fotos. Esperá unos segundos y tocá "Resuelto" de nuevo.'],
      [409, 'CONFLICTO', 'Otra persona está cambiando este reclamo en este momento.'],
    ];
    for (const [status, codigo, mensaje] of errores) {
      it('errorDelServidor_' + codigo + '_SeExplicaYSePuedeReintentar', () => {
        const f = abrir();

        resolver(f).flush({ codigo, detail: 'texto del servidor' }, { status, statusText: 'Error' });
        if (codigo === 'CONFLICTO') {
          f.detectChanges();
          http.expectOne(URL_DETALLE).flush(reclamo({ estado: 'EN_REPARACION' }));
        }
        f.detectChanges();

        expect(dialogo(f)!.querySelector('.caja-error')?.textContent).toContain(mensaje);
        // La ventana sigue abierta y se puede volver a tocar "Resuelto".
        expect(botonDialogo(f, 'Resuelto').disabled).toBe(false);
      });
    }

    it('siElReclamoYaNoEstabaEnReparacionLoDiceYVuelveAPedirElDetalle', () => {
      const f = abrir();

      resolver(f).flush({ codigo: 'TRANSICION_INVALIDA' }, { status: 409, statusText: 'Conflicto' });
      f.detectChanges();
      http.expectOne(URL_DETALLE).flush(reclamo({ estado: 'RESUELTO' }));
      f.detectChanges();

      expect(dialogo(f)!.querySelector('.caja-error')?.textContent).toContain('ya cambió de estado');
      expect(el(f).querySelector('app-chip-estado')?.textContent?.trim()).toBe('Resuelto');
    });
  });
});
