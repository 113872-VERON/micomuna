import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, Routes, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Mock } from 'vitest';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { ACHICAR_FOTO, AchicarFoto } from '../../reclamos/achicar-foto';
import { Coordenadas, ReclamoCreadoDto, TipoReclamoDto } from '../../reclamos/modelos';
import { SinUbicacion, UbicacionService } from '../../reclamos/ubicacion.service';
import { cuandoSeEnvio } from './reclamo-recibido/reclamo-recibido';
import { RUTAS_REPORTAR } from './reportar.routes';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'CELESTE',
};

const TIPOS: TipoReclamoDto[] = [
  { codigo: 'BACHE', nombre: 'Bache o calle rota', consejoFoto: 'Sacala de día y de cerca, que se vea todo el pozo y un pedazo de la calle.' },
  { codigo: 'LUZ', nombre: 'Luz apagada', consejoFoto: 'Sacá el poste o el farol de día: de noche no se ve nada. Si tiene un número, que se lea.' },
  { codigo: 'BASURA', nombre: 'Basura', consejoFoto: 'Que se vea la basura y el lugar donde está, por ejemplo la esquina o el contenedor.' },
  { codigo: 'ARBOL', nombre: 'Árbol o ramas', consejoFoto: 'Que se vea el árbol entero y lo que molesta: los cables, la vereda o la calle.' },
  { codigo: 'AGUA', nombre: 'Pérdida de agua o desagüe', consejoFoto: 'Sacá el lugar donde sale el agua o donde está tapado el desagüe.' },
  { codigo: 'OTRO', nombre: 'Otro problema', consejoFoto: 'Que se vea bien el problema y el lugar donde está.' },
];

const CREADO: ReclamoCreadoDto = { id: 99, numero: 43, tipo: 'BACHE', estado: 'RECIBIDO', cantidadFotos: 2, creadoEn: '2026-11-03T12:00:00Z' };

const URL_TIPOS = '/api/tipos-reclamo';
const URL_RECLAMOS = '/api/reclamos';
const BASE = '/salsipuedes-cordoba/reportar';

@Component({ template: '<p>Pantalla de inicio</p>' })
class InicioDePrueba {}

// Las mismas rutas del recorrido que usa la aplicación, con la comuna ya resuelta.
const RUTAS: Routes = [
  { path: 'inicio', component: InicioDePrueba },
  { path: ':slug', data: { comuna }, children: [{ path: 'reportar', children: RUTAS_REPORTAR }] },
];

describe('Reportar un problema', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let achicar: Mock<AchicarFoto>;
  let gps: { obtener: Mock<() => Promise<Coordenadas>> };
  let liberadas: string[];

  beforeEach(async () => {
    localStorage.clear();
    let urls = 0;
    liberadas = [];
    // `URL` del navegador: las vistas previas se crean y se liberan.
    vi.spyOn(globalThis.URL, 'createObjectURL').mockImplementation(() => `blob:foto-${++urls}`);
    vi.spyOn(globalThis.URL, 'revokeObjectURL').mockImplementation((url: string) => void liberadas.push(url));
    // El achicado real usa el canvas del navegador: acá se simula (se prueba aparte en achicar-foto.spec).
    achicar = vi.fn<AchicarFoto>(async () => new Blob(['jpeg'], { type: 'image/jpeg' }));
    gps = { obtener: vi.fn<() => Promise<Coordenadas>>() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          RUTAS,
          withComponentInputBinding(),
          withRouterConfig({ paramsInheritanceStrategy: 'always', canceledNavigationResolution: 'computed' }),
        ),
        // Historial simulado: "atrás" y "adelante" como en el celular.
        provideLocationMocks(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ACHICAR_FOTO, useValue: achicar },
        { provide: UbicacionService, useValue: gps },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 7, nombre: 'Rosa', correo: 'rosa@correo.com', rol: 'VECINO', comunaSlug: 'salsipuedes-cordoba' },
    });
    harness = await RouterTestingHarness.create();
    // En la aplicación esto lo hace el arranque: el router escucha "atrás" y "adelante".
    TestBed.inject(Router).setUpLocationChangeListener();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    document.documentElement.style.removeProperty('--p');
  });

  const el = () => harness.fixture.nativeElement as HTMLElement;
  const texto = () => el().textContent?.replace(/\s+/g, ' ') ?? '';
  const titulo = () => el().querySelector('h1')?.textContent?.trim();
  const url = () => TestBed.inject(Router).url;
  const historial = () => TestBed.inject(Location);

  // Navegaciones, el achicado y el GPS terminan en otras vueltas del ciclo de eventos.
  async function esperar() {
    // Sin whenStable: con el aviso abierto la navegación queda esperando la respuesta a propósito.
    for (let i = 0; i < 5; i++) {
      await new Promise((listo) => setTimeout(listo, 0));
      harness.detectChanges();
    }
  }

  async function abrir(direccion = BASE, tipos: TipoReclamoDto[] = TIPOS) {
    await harness.navigateByUrl(direccion);
    http.expectOne(URL_TIPOS).flush(tipos);
    await esperar();
  }

  function boton(nombre: string): HTMLButtonElement {
    const encontrado = Array.from(el().querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === nombre || b.textContent?.trim() === nombre,
    );
    if (!encontrado) {
      throw new Error(`No hay un botón "${nombre}"`);
    }
    return encontrado;
  }
  const hayBoton = (nombre: string) =>
    Array.from(el().querySelectorAll('button')).some((b) => b.getAttribute('aria-label') === nombre || b.textContent?.trim() === nombre);

  async function tocar(nombre: string) {
    boton(nombre).click();
    await esperar();
  }
  // "Siguiente" de los pasos con formulario es el submit (también se dispara con Enter).
  async function siguiente() {
    const form = el().querySelector('main form');
    if (form) {
      form.dispatchEvent(new Event('submit'));
    } else {
      boton('Siguiente').click();
    }
    await esperar();
  }
  function elegirTipo(nombre: string) {
    const opcion = Array.from(el().querySelectorAll<HTMLElement>('label.tipo')).find((l) => l.textContent?.includes(nombre));
    opcion!.querySelector('input')!.click();
    harness.detectChanges();
  }
  function escribir(selector: string, valor: string) {
    const campo = el().querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }
  const foto = (nombre: string) => new File([new Uint8Array(20)], nombre, { type: 'image/jpeg' });
  async function elegirFotos(desde: 'camara' | 'galeria', archivos: File[]) {
    const input = el().querySelector<HTMLInputElement>(desde === 'camara' ? 'input[capture]' : 'input[multiple]')!;
    Object.defineProperty(input, 'files', { value: archivos, configurable: true });
    input.dispatchEvent(new Event('change'));
    await esperar();
  }
  const fotosEnPantalla = () => el().querySelectorAll('li.foto img').length;
  const aviso = () => el().querySelector('dialog');

  async function completarPaso1(tipo = 'Bache o calle rota', descripcion = 'Hay un pozo grande frente a la panadería.') {
    elegirTipo(tipo);
    escribir('#descripcion', descripcion);
    await siguiente();
  }
  async function irAlPaso3() {
    await completarPaso1();
    await tocar('Seguir sin foto');
  }
  // Recorrido completo: bache, dos fotos, GPS y referencia.
  async function llegarARevisar() {
    await completarPaso1('Bache o calle rota', 'Hay un pozo grande frente a la panadería.');
    await elegirFotos('galeria', [foto('a.jpg'), foto('b.jpg')]);
    await tocar('Siguiente');
    gps.obtener.mockResolvedValue({ latitud: -31.123456, longitud: -64.299123, precision: 8 });
    await tocar('Usar mi ubicación');
    escribir('#referencia', 'Frente a la panadería');
    await siguiente();
  }
  // Sin esperar el resultado: si aparece el aviso, la navegación queda en pausa hasta que responda.
  const salirA = (direccion: string) => void TestBed.inject(Router).navigateByUrl(direccion);
  const fila = (nombre: string) => el().querySelector<HTMLElement>(`[data-fila="${nombre}"]`)!;

  describe('direcciones de cada paso', () => {
    it('entrarAReportarLlevaAlPrimerPaso', async () => {
      await abrir();

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(texto()).toContain('Paso 1 de 4');
    });

    it('cadaPasoTieneSuDireccion', async () => {
      await abrir();
      await completarPaso1();
      expect(url()).toBe(`${BASE}/fotos`);
      await tocar('Seguir sin foto');
      expect(url()).toBe(`${BASE}/donde`);
      escribir('#referencia', 'Plaza');
      await siguiente();
      expect(url()).toBe(`${BASE}/revisar`);
    });

    it('atrasYAdelanteDelNavegadorRecorrenLosPasosSinPerderNada', async () => {
      await abrir();
      await llegarARevisar();

      historial().back();
      await esperar();
      expect(url()).toBe(`${BASE}/donde`);
      expect((el().querySelector('#referencia') as HTMLInputElement).value).toBe('Frente a la panadería');
      expect(texto()).toContain('Tomamos tu ubicación.');

      historial().back();
      await esperar();
      expect(url()).toBe(`${BASE}/fotos`);
      expect(fotosEnPantalla()).toBe(2);

      historial().back();
      await esperar();
      expect(url()).toBe(`${BASE}/que-paso`);
      expect(el().querySelector('label.tipo.sel')?.textContent).toContain('Bache o calle rota');
      expect((el().querySelector('#descripcion') as HTMLTextAreaElement).value).toBe('Hay un pozo grande frente a la panadería.');
      // No se pidieron los tipos de nuevo: la pantalla es la misma.
      http.expectNone(URL_TIPOS);

      historial().forward();
      await esperar();
      historial().forward();
      await esperar();
      historial().forward();
      await esperar();
      expect(url()).toBe(`${BASE}/revisar`);
      expect(fila('fotos').querySelectorAll('img').length).toBe(2);
    });

    it('adelanteNoDejaSaltarAUnPasoSiElAnteriorQuedoIncompleto', async () => {
      await abrir();
      await completarPaso1();
      historial().back();
      await esperar();

      escribir('#descripcion', '');
      historial().forward();
      await esperar();

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(texto()).toContain('Paso 1 de 4');
    });

    it('adelanteNoPasaDeDondeLlegoElVecino', async () => {
      await abrir();
      await completarPaso1();

      await harness.navigateByUrl(`${BASE}/revisar`);
      await esperar();

      // Llegó hasta las fotos: no puede saltear el paso de dónde está.
      expect(url()).toBe(`${BASE}/fotos`);
    });

    it('entrarDirectoAUnPasoIntermedioSinBorradorLlevaAlPrimero', async () => {
      await abrir(`${BASE}/revisar`);

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(titulo()).toBe('¿Qué pasó?');
    });

    it('unaDireccionQueNoEsUnPasoLlevaAlPrimero', async () => {
      await abrir(`${BASE}/cualquier-cosa`);

      expect(url()).toBe(`${BASE}/que-paso`);
    });

    it('laConfirmacionSinReclamoEnviadoNoSeAbre', async () => {
      await abrir(`${BASE}/listo`);

      expect(url()).toBe(`${BASE}/que-paso`);
    });
  });

  describe('salir del recorrido', () => {
    it('sinNadaCargadoSaleSinPreguntar', async () => {
      await abrir();
      await tocar('Volver');

      expect(url()).toBe('/inicio');
      expect(aviso()).toBeNull();
    });

    it('conAlgoCargadoPreguntaYSeguirCargandoNoPierdeNada', async () => {
      await abrir();
      elegirTipo('Basura');
      escribir('#descripcion', 'Bolsas en la esquina');

      await tocar('Volver');

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(aviso()?.textContent).toContain('¿Salir sin enviar?');
      expect(aviso()?.textContent).toContain('Se pierde lo que cargaste.');
      expect(document.activeElement?.textContent?.trim()).toBe('Seguir cargando');

      await tocar('Seguir cargando');

      expect(aviso()).toBeNull();
      expect(url()).toBe(`${BASE}/que-paso`);
      expect((el().querySelector('#descripcion') as HTMLTextAreaElement).value).toBe('Bolsas en la esquina');
    });

    it('conAlgoCargadoYSalirVaAlInicio', async () => {
      await abrir();
      escribir('#descripcion', 'Bolsas en la esquina');

      await tocar('Volver');
      await tocar('Salir');

      expect(url()).toBe('/inicio');
      expect(texto()).toContain('Pantalla de inicio');
    });

    it('atrasDelNavegadorDesdeElPrimerPasoTambienPregunta', async () => {
      await harness.navigateByUrl('/inicio');
      await abrir();
      escribir('#descripcion', 'Farol apagado');

      historial().back();
      await esperar();

      expect(aviso()?.textContent).toContain('¿Salir sin enviar?');
      await tocar('Seguir cargando');
      expect(url()).toBe(`${BASE}/que-paso`);
      expect((el().querySelector('#descripcion') as HTMLTextAreaElement).value).toBe('Farol apagado');
    });

    it('enUnPasoIntermedioTambienPreguntaAlSalir', async () => {
      await abrir();
      await completarPaso1();
      salirA('/inicio');
      await esperar();

      expect(aviso()?.textContent).toContain('¿Salir sin enviar?');
    });

    it('mientrasSeEnviaAvisaQueTodaviaSeEstaEnviando', async () => {
      await abrir();
      await llegarARevisar();
      await tocar('Enviar reclamo');

      salirA('/inicio');
      await esperar();

      expect(aviso()?.textContent).toContain('Tu reclamo se está enviando');
      await tocar('Esperar');
      http.expectOne(URL_RECLAMOS).flush(CREADO, { status: 201, statusText: 'Created' });
      await esperar();
      expect(texto()).toContain('Reclamo n.º 43');
    });

    it('alRecargarOCerrarConAlgoCargadoPideElAvisoDelNavegador', async () => {
      await abrir();
      const sinNada = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(sinNada);
      expect(sinNada.defaultPrevented).toBe(false);

      escribir('#descripcion', 'Algo');
      const conAlgo = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(conAlgo);
      expect(conAlgo.defaultPrevented).toBe(true);
    });

    it('conLaSesionVencidaNoPregunta', async () => {
      await abrir();
      escribir('#descripcion', 'Algo');
      TestBed.inject(AuthService).cerrarSesion();

      salirA('/inicio');
      await esperar();

      expect(url()).toBe('/inicio');
    });
  });

  describe('paso 1: qué pasó', () => {
    it('muestraLosSeisTiposDelServidorConIconoYPalabraEnSuOrden', async () => {
      await abrir();

      expect(titulo()).toBe('¿Qué pasó?');
      const opciones = Array.from(el().querySelectorAll<HTMLElement>('label.tipo'));
      expect(opciones.map((o) => o.textContent?.trim())).toEqual(TIPOS.map((t) => t.nombre));
      expect(opciones.every((o) => o.querySelector('svg') && o.querySelector('input[type="radio"]'))).toBe(true);
    });

    it('elegirUnTipoLoMarcaComoElegido', async () => {
      await abrir();
      elegirTipo('Luz apagada');

      expect(Array.from(el().querySelectorAll('label.tipo.sel')).map((o) => o.textContent?.trim())).toEqual(['Luz apagada']);
    });

    it('sinTipoNiDescripcionNoAvanzaYDiceQueHacer', async () => {
      await abrir();
      await siguiente();

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(texto()).toContain('Elegí qué tipo de problema es.');
      expect(texto()).toContain('Contanos qué pasa, así la comuna puede ayudarte.');
      expect(el().querySelectorAll('[role="alert"]').length).toBe(2);
      expect(document.activeElement?.getAttribute('name')).toBe('tipo');
    });

    it('unosEspaciosNoCuentanComoDescripcion', async () => {
      await abrir();
      elegirTipo('Basura');
      escribir('#descripcion', '    ');
      await siguiente();

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(texto()).toContain('Contanos qué pasa');
      expect(document.activeElement?.id).toBe('descripcion');
    });

    it('cuentaLosCaracteresHasta500', async () => {
      await abrir();
      escribir('#descripcion', 'Hay un pozo');

      expect(texto()).toContain('11 de 500');
      expect(el().querySelector('#descripcion')?.getAttribute('maxlength')).toBe('500');
    });

    it('siNoCarganLosTiposSePuedeProbarDeNuevo', async () => {
      await harness.navigateByUrl(BASE);
      http.expectOne(URL_TIPOS).flush({ detail: 'El servidor no responde.' }, { status: 500, statusText: 'Error' });
      await esperar();

      expect(texto()).toContain('No pudimos cargar los tipos de problema.');
      await tocar('Probar de nuevo');
      http.expectOne(URL_TIPOS).flush(TIPOS);
      await esperar();
      expect(el().querySelectorAll('label.tipo').length).toBe(6);
    });

    it('siElServidorNoMandaTiposLoExplicaEnLugarDeQuedarseTrabado', async () => {
      await abrir(BASE, []);
      escribir('#descripcion', 'Algo pasa');
      await siguiente();

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(el().querySelector('[role="alert"]')?.textContent).toContain('Ahora no hay tipos de problema para elegir.');
      await tocar('Probar de nuevo');
      http.expectOne(URL_TIPOS).flush(TIPOS);
      await esperar();
      expect(el().querySelectorAll('label.tipo').length).toBe(6);
    });

    it('conTodoCompletoPasaALasFotosYLlevaElFocoAlTitulo', async () => {
      await abrir();
      await completarPaso1();

      expect(texto()).toContain('Paso 2 de 4');
      expect(titulo()).toBe('Sacá una foto');
      expect(document.activeElement?.tagName).toBe('H1');
      expect(document.activeElement?.textContent?.trim()).toBe('Sacá una foto');
    });
  });

  describe('paso 2: fotos', () => {
    it('elConsejoCambiaSegunElTipoElegido', async () => {
      await abrir();
      await completarPaso1('Bache o calle rota');
      expect(texto()).toContain('Sacala de día y de cerca, que se vea todo el pozo');

      await tocar('Volver');
      expect(url()).toBe(`${BASE}/que-paso`);
      expect((el().querySelector('#descripcion') as HTMLTextAreaElement).value).toBe('Hay un pozo grande frente a la panadería.');
      elegirTipo('Luz apagada');
      await siguiente();

      expect(texto()).toContain('Sacá el poste o el farol de día');
      expect(texto()).not.toContain('todo el pozo');
    });

    it('ofreceLaCamaraYLaGaleriaYSePuedeSeguirSinFoto', async () => {
      await abrir();
      await completarPaso1();

      expect(el().querySelector('input[type="file"][capture]')?.getAttribute('accept')).toBe('image/*');
      expect(el().querySelector('input[type="file"][multiple]')?.getAttribute('accept')).toBe('image/*');
      expect(hayBoton('Sacar una foto')).toBe(true);
      expect(hayBoton('Elegir una foto del teléfono')).toBe(true);

      await tocar('Seguir sin foto');
      expect(texto()).toContain('Paso 3 de 4');
    });

    it('agregaHasta3FotosYNoUnaCuarta', async () => {
      await abrir();
      await completarPaso1();

      await elegirFotos('camara', [foto('a.jpg')]);
      expect(fotosEnPantalla()).toBe(1);
      expect(titulo()).toBe('¿Se ve bien?');
      expect(hayBoton('Sacar otra foto')).toBe(true);

      await elegirFotos('galeria', [foto('b.jpg'), foto('c.jpg')]);
      expect(fotosEnPantalla()).toBe(3);
      expect(texto()).toContain('3 de 3 fotos: ya no entran más.');
      expect(hayBoton('Sacar otra foto')).toBe(false);
      expect(hayBoton('Elegir una foto del teléfono')).toBe(false);

      await elegirFotos('camara', [foto('d.jpg')]);
      expect(fotosEnPantalla()).toBe(3);
      expect(achicar).toHaveBeenCalledTimes(3);
    });

    it('siElige5DeUnaVezAgregaLasQueEntranYLoAvisa', async () => {
      await abrir();
      await completarPaso1();

      await elegirFotos('galeria', ['1', '2', '3', '4', '5'].map((n) => foto(`${n}.jpg`)));

      expect(fotosEnPantalla()).toBe(3);
      expect(texto()).toContain('Elegiste 5 fotos y entran 3 más: agregamos las primeras.');
    });

    it('cadaFotoSeAchicaAntesDeMostrarla', async () => {
      await abrir();
      await completarPaso1();
      const original = foto('IMG_0001.jpg');

      await elegirFotos('camara', [original]);

      expect(achicar).toHaveBeenCalledWith(original);
      expect(el().querySelector('li.foto img')?.getAttribute('src')).toBe('blob:foto-1');
    });

    it('quitarUnaFotoLaSacaYLiberaSuVistaPrevia', async () => {
      await abrir();
      await completarPaso1();
      await elegirFotos('galeria', [foto('a.jpg'), foto('b.jpg')]);

      await tocar('Quitar la foto 1');

      expect(fotosEnPantalla()).toBe(1);
      expect(liberadas).toEqual(['blob:foto-1']);
      expect(el().querySelector('li.foto img')?.getAttribute('src')).toBe('blob:foto-2');
    });

    it('unaFotoQueNoSePuedeLeerMuestraElErrorYNoSeAgrega', async () => {
      await abrir();
      await completarPaso1();
      achicar.mockRejectedValueOnce(new Error('dañada'));

      await elegirFotos('camara', [foto('rota.jpg')]);

      expect(fotosEnPantalla()).toBe(0);
      const alerta = el().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('No pudimos usar esa foto');
      expect(alerta).toContain('Sacá otra o elegí una distinta.');
      expect(hayBoton('Sacar una foto')).toBe(true);
      expect(hayBoton('Seguir sin foto')).toBe(true);

      await elegirFotos('camara', [foto('buena.jpg')]);
      expect(fotosEnPantalla()).toBe(1);
      expect(texto()).not.toContain('No pudimos usar esa foto');
    });

    it('alSalirDeLaPantallaLiberaLasVistasPrevias', async () => {
      await abrir();
      await completarPaso1();
      await elegirFotos('galeria', [foto('a.jpg'), foto('b.jpg')]);

      salirA('/inicio');
      await esperar();
      await tocar('Salir');

      expect(url()).toBe('/inicio');
      expect(liberadas).toEqual(['blob:foto-1', 'blob:foto-2']);
    });
  });

  describe('paso 3: dónde está', () => {
    it('conLaUbicacionDelTelefonoMuestraQueSeTomoYAvanza', async () => {
      await abrir();
      await irAlPaso3();
      let responder!: (c: Coordenadas) => void;
      gps.obtener.mockReturnValue(new Promise((r) => (responder = r)));

      boton('Usar mi ubicación').click();
      harness.detectChanges();
      expect(boton('Buscando dónde estás…').disabled).toBe(true);

      responder({ latitud: -31.1, longitud: -64.3, precision: 10 });
      await esperar();

      expect(el().querySelector('main [role="status"]')?.textContent).toContain('Tomamos tu ubicación.');
      expect(texto()).not.toContain('Guardamos');
      expect(hayBoton('Volver a tomar mi ubicación')).toBe(true);
      await siguiente();
      expect(texto()).toContain('Paso 4 de 4');
    });

    it('siElTelefonoNoDaPermisoLoExplicaYSePuedeSeguirConLaReferencia', async () => {
      await abrir();
      await irAlPaso3();
      gps.obtener.mockRejectedValue(new SinUbicacion('permiso'));

      await tocar('Usar mi ubicación');

      const alerta = el().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('No pudimos ver dónde estás porque el teléfono no dio permiso.');
      expect(alerta).toContain('Escribí la dirección');
      expect(hayBoton('Usar mi ubicación')).toBe(true);

      escribir('#referencia', 'Frente a la panadería');
      await siguiente();
      expect(texto()).toContain('Paso 4 de 4');
    });

    it('otraFallaDelGpsTambienDejaSeguir', async () => {
      await abrir();
      await irAlPaso3();
      gps.obtener.mockRejectedValue(new SinUbicacion('otro'));

      await tocar('Usar mi ubicación');

      expect(el().querySelector('[role="alert"]')?.textContent).toContain('No pudimos ver dónde estás. Escribí la dirección');
    });

    it('soloConLaReferenciaAlcanza', async () => {
      await abrir();
      await irAlPaso3();

      escribir('#referencia', 'San Martín 450');
      await siguiente();

      expect(texto()).toContain('Paso 4 de 4');
      expect(gps.obtener).not.toHaveBeenCalled();
    });

    it('sinUbicacionNiReferenciaNoAvanza', async () => {
      await abrir();
      await irAlPaso3();

      escribir('#referencia', '   ');
      await siguiente();

      expect(url()).toBe(`${BASE}/donde`);
      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'Usá tu ubicación o escribí la dirección, así la comuna encuentra el lugar.',
      );
      expect(el().querySelector('#referencia')?.getAttribute('aria-invalid')).toBe('true');
      expect(document.activeElement?.id).toBe('referencia');
    });

    it('quitarLaUbicacionVuelveAPedirUnLugar', async () => {
      await abrir();
      await irAlPaso3();
      gps.obtener.mockResolvedValue({ latitud: -31.1, longitud: -64.3 });
      await tocar('Usar mi ubicación');

      await tocar('Quitar la ubicación');
      await siguiente();

      expect(texto()).toContain('Paso 3 de 4');
    });
  });

  describe('paso 4: revisar y enviar', () => {
    it('muestraElResumenDeTodoLoCargado', async () => {
      await abrir();
      await llegarARevisar();

      expect(titulo()).toBe('Revisá y enviá');
      expect(fila('tipo').textContent).toContain('Bache o calle rota');
      expect(fila('descripcion').textContent).toContain('Hay un pozo grande frente a la panadería.');
      expect(fila('fotos').textContent).toContain('2 fotos');
      expect(fila('fotos').querySelectorAll('img').length).toBe(2);
      expect(fila('lugar').textContent).toContain('Frente a la panadería');
      expect(fila('lugar').textContent).toContain('Con tu ubicación del teléfono');
    });

    it('cambiarVuelveACadaPasoSinPerderLoCargado', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Cambiar las fotos');
      expect(url()).toBe(`${BASE}/fotos`);
      expect(fotosEnPantalla()).toBe(2);
      await tocar('Siguiente');

      expect(url()).toBe(`${BASE}/donde`);
      expect((el().querySelector('#referencia') as HTMLInputElement).value).toBe('Frente a la panadería');
      await siguiente();

      await tocar('Cambiar el tipo');
      expect(url()).toBe(`${BASE}/que-paso`);
      expect(el().querySelector('label.tipo.sel')?.textContent).toContain('Bache o calle rota');

      await siguiente();
      await tocar('Siguiente');
      await siguiente();
      await tocar('Cambiar dónde está');
      expect(url()).toBe(`${BASE}/donde`);
    });

    it('enviaUnMultipartConLosCamposYLasFotosYMuestraElNumero', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      expect(boton('Enviando…').disabled).toBe(true);
      const pedido = http.expectOne(URL_RECLAMOS);
      expect(pedido.request.method).toBe('POST');
      const datos = pedido.request.body as FormData;
      expect(datos.get('tipo')).toBe('BACHE');
      expect(datos.get('descripcion')).toBe('Hay un pozo grande frente a la panadería.');
      expect(datos.get('latitud')).toBe('-31.123456');
      expect(datos.get('longitud')).toBe('-64.299123');
      expect(datos.get('referencia')).toBe('Frente a la panadería');
      const fotos = datos.getAll('fotos') as File[];
      expect(fotos.map((x) => x.name)).toEqual(['foto-1.jpg', 'foto-2.jpg']);
      expect(fotos.every((x) => x.type === 'image/jpeg')).toBe(true);

      pedido.flush(CREADO, { status: 201, statusText: 'Created' });
      await esperar();

      expect(url()).toBe(`${BASE}/listo`);
      expect(titulo()).toBe('Recibimos tu reclamo');
      expect(texto()).toContain('Reclamo n.º 43');
      expect(texto()).toContain('Recibido');
      expect(texto()).not.toContain('Paso');
      expect(el().querySelector('a.btn')?.getAttribute('href')).toBe('/inicio');
      expect(document.activeElement?.textContent?.trim()).toBe('Recibimos tu reclamo');
      expect(liberadas).toEqual(['blob:foto-1', 'blob:foto-2']);
    });

    it('atrasDesdeLaConfirmacionNoVuelveAlReclamoEnviado', async () => {
      await abrir();
      await llegarARevisar();
      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(CREADO, { status: 201, statusText: 'Created' });
      await esperar();

      historial().back();
      await esperar();

      expect(url()).toBe('/inicio');
      expect(aviso()).toBeNull();
      http.expectNone(URL_RECLAMOS);
    });

    it('unaFotoInvalidaUsaElTextoPropioConElNumeroDeFotoYNoPierdeNada', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        { status: 400, codigo: 'FOTO_INVALIDA', detail: 'La foto 2 no es un JPG o PNG real.' },
        { status: 400, statusText: 'Bad Request' },
      );
      await esperar();

      expect(url()).toBe(`${BASE}/revisar`);
      const alerta = el().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('No pudimos usar la foto 2. Tocá "Cambiar" en las fotos, quitala y sacá otra.');
      expect(alerta).not.toContain('JPG');
      expect(fila('descripcion').textContent).toContain('Hay un pozo grande');
      expect(fila('fotos').querySelectorAll('img').length).toBe(2);
      expect(boton('Enviar reclamo').disabled).toBe(false);

      await tocar('Cambiar las fotos');
      await tocar('Quitar la foto 2');
      expect(fotosEnPantalla()).toBe(1);
    });

    it('elTextoPropioVaAunqueElServidorMandeDetail', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        { status: 400, codigo: 'ARCHIVO_MUY_GRANDE', detail: 'Maximum upload size exceeded' },
        { status: 400, statusText: 'Bad Request' },
      );
      await esperar();

      const alerta = el().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('Las fotos pesan demasiado. Tocá "Cambiar" en las fotos y quitá alguna.');
      expect(alerta).not.toContain('Maximum');
    });

    it('unConflictoSeMuestraYAlReintentarSeEnviaDeNuevo', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      // Texto real del contrato para el 409 de este endpoint.
      http.expectOne(URL_RECLAMOS).flush(
        { status: 409, codigo: 'CONFLICTO', detail: 'Otra persona está reportando en este momento y no se pudo asignar el número. Probá de nuevo.' },
        { status: 409, statusText: 'Conflict' },
      );
      await esperar();
      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'Otra persona estaba reportando al mismo tiempo. Tocá "Enviar reclamo" de nuevo.',
      );

      await tocar('Enviar reclamo');
      const reintento = http.expectOne(URL_RECLAMOS);
      expect((reintento.request.body as FormData).getAll('fotos').length).toBe(2);
      reintento.flush(CREADO, { status: 201, statusText: 'Created' });
      await esperar();
      expect(texto()).toContain('Reclamo n.º 43');
      expect(texto()).not.toContain('Otra persona estaba reportando');
    });

    it('demasiadosReclamosExplicaQueHayQueEsperar', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        { status: 429, codigo: 'DEMASIADOS_RECLAMOS', detail: 'Ya creaste 10 reclamos en la última hora.' },
        { status: 429, statusText: 'Too Many Requests' },
      );
      await esperar();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'Ya enviaste muchos reclamos en la última hora. Esperá un rato y tocá "Enviar reclamo" de nuevo.',
      );
      expect(fila('descripcion').textContent).toContain('Hay un pozo grande');
      expect(boton('Enviar reclamo').disabled).toBe(false);
    });

    it('servidorOcupadoPideProbarEnUnosSegundos', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        { status: 503, codigo: 'OCUPADO', detail: 'El servidor está procesando muchas fotos a la vez.' },
        { status: 503, statusText: 'Service Unavailable' },
      );
      await esperar();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain(
        'Esperá unos segundos y tocá "Enviar reclamo" de nuevo.',
      );
      expect(fila('fotos').querySelectorAll('img').length).toBe(2);
    });

    it('unaValidacionMuestraElMotivoDeCadaCampo', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        {
          status: 400,
          codigo: 'VALIDACION',
          detail: 'Datos inválidos',
          errores: { descripcion: 'La descripción puede tener hasta 500 caracteres', referencia: 'La referencia puede tener hasta 200 caracteres.' },
        },
        { status: 400, statusText: 'Bad Request' },
      );
      await esperar();

      const alerta = el().querySelector('[role="alert"]')?.textContent ?? '';
      expect(alerta).toContain('La descripción puede tener hasta 500 caracteres.');
      expect(alerta).toContain('La referencia puede tener hasta 200 caracteres.');
    });

    it('unCodigoQueNoConoceUsaElTextoDelServidor', async () => {
      await abrir();
      await llegarARevisar();

      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(
        { status: 400, codigo: 'OTRO_ERROR', detail: 'Algo que solo sabe el servidor.' },
        { status: 400, statusText: 'Bad Request' },
      );
      await esperar();

      expect(el().querySelector('[role="alert"]')?.textContent).toContain('Algo que solo sabe el servidor.');
    });

    it('sinFotosNiGpsMandaSoloLoQueHay', async () => {
      await abrir();
      await completarPaso1('Otro problema', 'Un cartel caído');
      await tocar('Seguir sin foto');
      escribir('#referencia', 'Plaza central');
      await siguiente();

      expect(fila('fotos').textContent).toContain('Sin fotos');
      await tocar('Enviar reclamo');
      const datos = http.expectOne(URL_RECLAMOS).request.body as FormData;
      expect(Array.from(datos.keys())).toEqual(['tipo', 'descripcion', 'referencia']);
      expect(datos.get('tipo')).toBe('OTRO');
    });

    it('reportarOtroProblemaEmpiezaDeCero', async () => {
      await abrir();
      await llegarARevisar();
      await tocar('Enviar reclamo');
      http.expectOne(URL_RECLAMOS).flush(CREADO, { status: 201, statusText: 'Created' });
      await esperar();

      await tocar('Reportar otro problema');

      expect(url()).toBe(`${BASE}/que-paso`);
      expect(el().querySelector('label.tipo.sel')).toBeNull();
      expect((el().querySelector('#descripcion') as HTMLTextAreaElement).value).toBe('');
      http.expectNone(URL_TIPOS);
    });
  });
});

describe('cuandoSeEnvio', () => {
  it('diceHoyCuandoEsElMismoDia', () => {
    const ahora = new Date(2026, 10, 3, 18, 0);
    expect(cuandoSeEnvio(new Date(2026, 10, 3, 9, 12).toISOString(), ahora)).toBe('Lo enviaste hoy, 3 de noviembre.');
  });

  it('otroDiaLlevaLaFechaYElAnioSoloSiNoEsElActual', () => {
    const ahora = new Date(2026, 10, 3);
    expect(cuandoSeEnvio(new Date(2026, 10, 1, 12).toISOString(), ahora)).toBe('Lo enviaste el 1 de noviembre.');
    expect(cuandoSeEnvio(new Date(2025, 11, 30, 12).toISOString(), ahora)).toBe('Lo enviaste el 30 de diciembre de 2025.');
  });

  it('sinFechaValidaNoDiceNada', () => {
    expect(cuandoSeEnvio(undefined)).toBeNull();
    expect(cuandoSeEnvio('no es fecha')).toBeNull();
  });
});
