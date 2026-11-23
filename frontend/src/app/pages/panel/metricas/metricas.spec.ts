import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { hoyEnArgentina, ultimosDias } from '../../../reclamos/metricas-formato';
import { MetricasDto } from '../../../reclamos/modelos-panel';
import { Metricas } from './metricas';

const COMUNA: ComunaDto = { nombre: 'Salsipuedes', provincia: 'Córdoba', departamento: 'Colón', slug: 'salsipuedes-cordoba', tieneLogo: false, color: 'OCRE' };
const BASE = '/salsipuedes-cordoba/panel/metricas';
const RUTAS = [{ path: ':slug/panel/metricas', data: { comuna: COMUNA }, component: Metricas }];

const METRICAS: MetricasDto = {
  desde: '2026-10-01',
  hasta: '2026-11-03',
  dias: 34,
  reclamosDelPeriodo: 1234,
  resueltos: 20,
  tiempoPromedioResolucionHoras: 5.2,
  sinAsignar: 7,
  porTipo: [
    { tipo: 'BACHE', tipoNombre: 'Bache o calle rota', cantidad: 10 },
    { tipo: 'LUZ', tipoNombre: 'Luz quemada', cantidad: 5 },
    { tipo: 'BASURA', tipoNombre: 'Basura acumulada', cantidad: 0 },
  ],
  porSemana: [
    { desde: '2026-10-01', hasta: '2026-10-04', cantidad: 3 },
    { desde: '2026-11-02', hasta: '2026-11-03', cantidad: 8 },
    { desde: '2026-10-05', hasta: '2026-10-11', cantidad: 0 },
  ],
};

describe('Métricas', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(RUTAS, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' },
    });
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const el = () => harness.fixture.nativeElement as HTMLElement;
  const texto = () => el().textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const url = () => TestBed.inject(Router).url;
  const pedido = () => http.expectOne((p) => p.url === '/api/panel/metricas');
  const boton = (nombre: string) => Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.trim() === nombre) as HTMLButtonElement;
  const campo = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;

  async function esperar(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function abrir(query = '', datos: MetricasDto = METRICAS): Promise<TestRequest> {
    await harness.navigateByUrl(BASE + query);
    const p = pedido();
    p.flush(datos);
    harness.detectChanges();
    return p;
  }

  function escribir(id: string, valor: string): void {
    const c = campo(id);
    c.value = valor;
    c.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  describe('al abrir', () => {
    it('sinFechasPideLosUltimos30DiasAlServidorYLlenaLosCamposConLoQueDevuelve', async () => {
      const p = await abrir();

      expect(p.request.params.keys()).toEqual([]);
      expect(campo('desde').value).toBe('2026-10-01');
      expect(campo('hasta').value).toBe('2026-11-03');
      expect(texto()).toContain('Mostrando del 1/10/2026 al 3/11/2026 (34 días)');
    });

    it('losCuatroNumerosVanGrandesConSuExplicacion', async () => {
      await abrir();

      const items = Array.from(el().querySelectorAll('.numeros .numero')).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
      expect(items).toEqual([
        'Reclamos del período1.234Los que se crearon en estas fechas, en cualquier estado.',
        'Resueltos20Los que se resolvieron en estas fechas, aunque se hayan creado antes.',
        'Tiempo promedio de resolución5,2 horasDesde que se creó el reclamo hasta que se resolvió, entre los resueltos.',
        'Sin asignar (de estas fechas)7De los reclamos de estas fechas, los que siguen en Recibido, sin agente.',
      ]);
    });

    it('losNumerosSonUnaListaDeDefinicionesQueLosLectoresLeen', async () => {
      await abrir();

      const lista = el().querySelector('dl.numeros')!;
      const terminos = Array.from(lista.querySelectorAll('dt')).map((d) => d.textContent?.trim());
      expect(terminos).toEqual(['Reclamos del período', 'Resueltos', 'Tiempo promedio de resolución', 'Sin asignar (de estas fechas)']);
      // Cada rótulo trae su número y su explicación (dos dd).
      expect(Array.from(lista.querySelectorAll('.numero')).every((n) => n.querySelectorAll('dd').length === 2)).toBe(true);
      expect(lista.querySelector('[aria-labelledby]')).toBeNull();
    });

    it('conMasDe48HorasElTiempoVaEnDias', async () => {
      await abrir('', { ...METRICAS, tiempoPromedioResolucionHoras: 50.4 });

      expect(texto()).toContain('2,1 días');
    });

    it('sinResueltosElTiempoDiceSinResueltos', async () => {
      await abrir('', { ...METRICAS, resueltos: 0, tiempoPromedioResolucionHoras: null });

      expect(el().querySelectorAll('.numeros .numero')[2].textContent).toContain('Sin resueltos');
    });

    it('losTextosSeMuestranConLosNumerosFormateadosAlArgentino', async () => {
      await abrir('', { ...METRICAS, reclamosDelPeriodo: 1234567 });

      expect(texto()).toContain('1.234.567');
    });
  });

  describe('barras', () => {
    it('porTipoCadaBarraTieneSuNumeroYSuAnchoEnProporcion', async () => {
      await abrir();

      const filas = Array.from(el().querySelectorAll('section[aria-labelledby="t-tipo"] .barras li'));
      expect(filas.map((f) => f.querySelector('.nombre')?.textContent?.trim())).toEqual(['Bache o calle rota', 'Luz quemada', 'Basura acumulada']);
      expect(filas.map((f) => f.querySelector('.cant')?.textContent?.trim())).toEqual(['10', '5', '0']);
      expect(filas.map((f) => (f.querySelector('.barra') as HTMLElement).style.width)).toEqual(['100%', '50%', '0%']);
    });

    it('porSemanaMuestraLaSemanaComoDiaYMesAlDiaYMes', async () => {
      await abrir();

      const filas = Array.from(el().querySelectorAll('section[aria-labelledby="t-semana"] .barras li'));
      expect(filas.map((f) => f.querySelector('.nombre')?.textContent?.trim())).toEqual(['1/10 al 4/10', '2/11 al 3/11', '5/10 al 11/10']);
      expect(filas.map((f) => f.querySelector('.cant')?.textContent?.trim())).toEqual(['3', '8', '0']);
    });

    it('lasBarrasSonDecorativasYUnaTablaAccesibleTraeLosMismosDatos', async () => {
      await abrir();

      for (const id of ['t-tipo', 't-semana']) {
        const seccion = el().querySelector(`section[aria-labelledby="${id}"]`)!;
        expect(seccion.querySelector('.barras')?.getAttribute('aria-hidden')).toBe('true');
        expect(seccion.querySelector('table caption')).not.toBeNull();
        expect(seccion.querySelectorAll('table tbody tr').length).toBe(3);
      }
      const tipo = Array.from(el().querySelectorAll('section[aria-labelledby="t-tipo"] tbody tr')).map((tr) => tr.textContent?.replace(/\s+/g, ' ').trim());
      expect(tipo).toEqual(['Bache o calle rota10', 'Luz quemada5', 'Basura acumulada0']);
      const semana = Array.from(el().querySelectorAll('section[aria-labelledby="t-semana"] tbody tr')).map((tr) => tr.textContent?.replace(/\s+/g, ' ').trim());
      expect(semana[0]).toBe('1/10 al 4/103');
    });

    it('losNombresSeMuestranComoTextoPlano', async () => {
      await abrir('', { ...METRICAS, porTipo: [{ tipo: 'X', tipoNombre: '<img src=x onerror=alert(1)>', cantidad: 1 }] });

      expect(el().querySelector('.grafico img')).toBeNull();
      expect(texto()).toContain('<img src=x onerror=alert(1)>');
    });
  });

  describe('estado vacío', () => {
    it('sinReclamosEnElPeriodoLoDiceYNoDibujaGraficos', async () => {
      await abrir('', { ...METRICAS, reclamosDelPeriodo: 0, resueltos: 0, sinAsignar: 0, tiempoPromedioResolucionHoras: null, porTipo: [], porSemana: [] });

      expect(texto()).toContain('No hubo reclamos en este período');
      expect(el().querySelector('.graficos')).toBeNull();
      // Los números igual se muestran, en cero.
      expect(el().querySelectorAll('.numeros .numero').length).toBe(4);
    });
  });

  describe('atajos y fechas en la URL', () => {
    it('sinFechasElAtajoDe30DiasEstaActivo', async () => {
      await abrir();

      expect(boton('Últimos 30 días').getAttribute('aria-pressed')).toBe('true');
      expect(boton('Últimos 7 días').getAttribute('aria-pressed')).toBe('false');
    });

    it('ultimos7DiasPonenLasFechasEnLaUrlYLasPideAlServidor', async () => {
      await abrir();
      const { desde, hasta } = ultimosDias(7, hoyEnArgentina());

      boton('Últimos 7 días').click();
      await esperar();

      expect(url()).toContain(`desde=${desde}`);
      expect(url()).toContain(`hasta=${hasta}`);
      const p = pedido();
      expect(p.request.params.get('desde')).toBe(desde);
      expect(p.request.params.get('hasta')).toBe(hasta);
      p.flush({ ...METRICAS, desde, hasta, dias: 7 });
      harness.detectChanges();
      expect(boton('Últimos 7 días').getAttribute('aria-pressed')).toBe('true');
      expect(campo('desde').value).toBe(desde);
    });

    it('ultimos30DiasTambienPoneLasFechas', async () => {
      await abrir();
      const { desde, hasta } = ultimosDias(30, hoyEnArgentina());

      boton('Últimos 30 días').click();
      await esperar();

      expect(url()).toContain(`desde=${desde}`);
      expect(pedido().request.params.get('hasta')).toBe(hasta);
    });

    it('lasFechasDeLaUrlSePidenYSeMuestranEnLosCampos', async () => {
      const p = await abrir('?desde=2026-10-01&hasta=2026-11-03');

      expect(p.request.params.get('desde')).toBe('2026-10-01');
      expect(p.request.params.get('hasta')).toBe('2026-11-03');
      expect(campo('desde').value).toBe('2026-10-01');
      expect(campo('hasta').value).toBe('2026-11-03');
    });

    it('elegirFechasYTocarVerMetricasLasPoneEnLaUrl', async () => {
      await abrir();

      escribir('desde', '2026-09-01');
      escribir('hasta', '2026-09-30');
      boton('Ver métricas').click();
      await esperar();

      expect(url()).toBe(`${BASE}?desde=2026-09-01&hasta=2026-09-30`);
      const p = pedido();
      expect(p.request.params.get('desde')).toBe('2026-09-01');
      p.flush({ ...METRICAS, desde: '2026-09-01', hasta: '2026-09-30', dias: 30 });
    });
  });

  describe('validación antes de pedir', () => {
    it('desdePosteriorAHastaEnLosCamposNoLlamaAlServidorNiCambiaLaUrl', async () => {
      await abrir();

      escribir('desde', '2026-11-10');
      escribir('hasta', '2026-11-01');
      boton('Ver métricas').click();
      await esperar();

      expect(el().querySelector('#error-fechas')?.textContent?.trim()).toBe('La fecha «desde» tiene que ser anterior o igual a «hasta».');
      expect(campo('desde').getAttribute('aria-invalid')).toBe('true');
      expect(url()).toBe(BASE);
      http.expectNone((p) => p.url === '/api/panel/metricas');
    });

    it('masDe366DiasNoLlamaAlServidorYLoAvisa', async () => {
      await abrir();

      escribir('desde', '2025-01-01');
      escribir('hasta', '2026-11-03');
      boton('Ver métricas').click();
      await esperar();

      expect(el().querySelector('#error-fechas')?.textContent).toContain('366 días');
      http.expectNone((p) => p.url === '/api/panel/metricas');
    });

    it('faltandoUnaFechaPideLasDos', async () => {
      await abrir();

      escribir('hasta', '');
      boton('Ver métricas').click();
      await esperar();

      expect(el().querySelector('#error-fechas')?.textContent).toContain('Elegí las dos fechas');
      http.expectNone((p) => p.url === '/api/panel/metricas');
    });

    it('unaUrlConDesdePosteriorAHastaTampocoLlamaAlServidor', async () => {
      await harness.navigateByUrl(`${BASE}?desde=2026-11-10&hasta=2026-11-01`);
      harness.detectChanges();

      http.expectNone((p) => p.url === '/api/panel/metricas');
      expect(el().querySelector('#error-fechas')?.textContent).toContain('anterior o igual');
      expect(el().querySelector('.numeros')).toBeNull();
    });

    it('unaUrlConSoloUnaFechaPideLasDosSinLlamarAlServidor', async () => {
      await harness.navigateByUrl(`${BASE}?desde=2026-11-01`);
      harness.detectChanges();

      http.expectNone((p) => p.url === '/api/panel/metricas');
      expect(el().querySelector('#error-fechas')?.textContent).toContain('Elegí las dos fechas');
    });

    it('unaUrlConFechasRarasNoRompe', async () => {
      await harness.navigateByUrl(`${BASE}?desde=<b>hola</b>&hasta=2026-13-45`);
      harness.detectChanges();

      http.expectNone((p) => p.url === '/api/panel/metricas');
      expect(el().querySelector('#error-fechas')?.textContent).toContain('no es válida');
    });

    it('corregirLasFechasDespuesDelErrorVuelveAPedir', async () => {
      await harness.navigateByUrl(`${BASE}?desde=2026-11-10&hasta=2026-11-01`);
      harness.detectChanges();

      escribir('desde', '2026-10-01');
      escribir('hasta', '2026-11-01');
      boton('Ver métricas').click();
      await esperar();

      expect(el().querySelector('#error-fechas')).toBeNull();
      pedido().flush({ ...METRICAS, desde: '2026-10-01', hasta: '2026-11-01', dias: 32 });
      harness.detectChanges();
      expect(texto()).toContain('Mostrando del 1/10/2026 al 1/11/2026 (32 días)');
    });
  });

  describe('errores del servidor', () => {
    it('muestraElMensajeDelServidorEnUnaAlerta', async () => {
      await harness.navigateByUrl(`${BASE}?desde=2026-10-01&hasta=2026-11-03`);
      pedido().flush({ codigo: 'FECHAS_INVALIDAS', detail: 'La fecha desde no puede ser posterior a hasta.' }, { status: 400, statusText: 'Bad Request' });
      harness.detectChanges();

      expect(el().querySelector('.caja-error')?.textContent).toContain('La fecha desde no puede ser posterior a hasta.');
      expect(el().querySelector('.numeros')).toBeNull();
    });

    it('rangoMuyLargoDelServidorTambienSeMuestra', async () => {
      await harness.navigateByUrl(`${BASE}?desde=2026-10-01&hasta=2026-11-03`);
      pedido().flush({ codigo: 'RANGO_MUY_LARGO', detail: 'El período no puede superar los 366 días.' }, { status: 400, statusText: 'Bad Request' });
      harness.detectChanges();

      expect(texto()).toContain('El período no puede superar los 366 días.');
    });

    it('unaFallaDeRedOfreceReintentar', async () => {
      await harness.navigateByUrl(BASE);
      pedido().flush(null, { status: 500, statusText: 'Error' });
      harness.detectChanges();

      expect(texto()).toContain('No pudimos cargar las métricas');
      boton('Reintentar').click();
      harness.detectChanges();
      pedido().flush(METRICAS);
      harness.detectChanges();

      expect(texto()).toContain('Mostrando del 1/10/2026');
    });
  });
});
