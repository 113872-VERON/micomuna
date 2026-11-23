import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgenteDto, ReclamoElegido, ResultadoEnLoteDto } from '../../../reclamos/modelos-panel';
import { DialogoCambiarEstado } from './dialogo-cambiar-estado';
import { DialogoDescartar, textoDeAplicables } from './dialogo-descartar';

const AGENTES: AgenteDto[] = [
  { id: 4, nombre: 'Marcos Díaz' },
  { id: 9, nombre: 'Lucía Paz' },
];
const URL_ESTADO = '/api/panel/reclamos/cambios-de-estado';
const URL_DESCARTE = '/api/panel/reclamos/descartes';

const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
const boton = (f: ComponentFixture<unknown>, nombre: string) =>
  Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(nombre)) as HTMLButtonElement;
function elegir(f: ComponentFixture<unknown>, etiqueta: string) {
  const radio = Array.from(el(f).querySelectorAll('label.opcion')).find((l) => l.textContent?.trim() === etiqueta)?.querySelector('input') as HTMLInputElement;
  radio.click();
  f.detectChanges();
}
function escribir(f: ComponentFixture<unknown>, selector: string, valor: string) {
  const campo = el(f).querySelector(selector) as HTMLInputElement | HTMLTextAreaElement;
  campo.value = valor;
  campo.dispatchEvent(new Event('input'));
  f.detectChanges();
}
const alertas = (f: ComponentFixture<unknown>) => Array.from(el(f).querySelectorAll('[role="alert"]')).map((a) => a.textContent?.trim());

describe('Ventanas del panel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('Cambiar estado', () => {
    function crear(reclamos: ReclamoElegido[], agentes: AgenteDto[] = AGENTES) {
      const f = TestBed.createComponent(DialogoCambiarEstado);
      f.componentRef.setInput('reclamos', reclamos);
      f.componentRef.setInput('agentes', agentes);
      const cerrar = vi.fn();
      const aplicado = vi.fn();
      f.componentInstance.cerrar.subscribe(cerrar);
      f.componentInstance.aplicado.subscribe(aplicado);
      f.detectChanges();
      return { f, cerrar, aplicado };
    }

    it('ofreceLosCuatroEstadosYNoDescartado', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);

      const opciones = Array.from(el(f).querySelectorAll('label.opcion')).map((l) => l.textContent?.trim());
      expect(opciones).toEqual(['Recibido', 'Asignado', 'En reparación', 'Resuelto']);
      expect(texto(f)).toContain('Reclamo n.º 12 (ahora: Recibido)');
    });

    it('sinElegirNadaPideElegirUnEstadoYNoEnvia', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);

      boton(f, 'Cambiar estado').click();
      f.detectChanges();

      expect(alertas(f)).toEqual(['Elegí el estado nuevo.']);
      http.expectNone(URL_ESTADO);
    });

    it('asignadoPideElAgenteYNoEnviaSinEl', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);
      expect(el(f).querySelector('#agente-nuevo')).toBeNull();

      elegir(f, 'Asignado');
      expect(el(f).querySelector('#agente-nuevo')).not.toBeNull();
      boton(f, 'Cambiar estado').click();
      f.detectChanges();

      expect(alertas(f)).toEqual(['Elegí a qué agente se lo asignás.']);
      expect(el(f).querySelector('#agente-nuevo')?.getAttribute('aria-invalid')).toBe('true');
      http.expectNone(URL_ESTADO);
    });

    it('asignadoConAgenteMandaElIdDelAgenteYMuestraElResultado', () => {
      const { f, aplicado } = crear([{ numero: 12, estado: 'RECIBIDO' }, { numero: 14, estado: 'RECIBIDO' }]);
      elegir(f, 'Asignado');
      const select = el(f).querySelector('#agente-nuevo') as HTMLSelectElement;
      select.value = '9';
      select.dispatchEvent(new Event('change'));
      f.detectChanges();

      boton(f, 'Cambiar estado').click();
      const pedido = http.expectOne(URL_ESTADO);
      expect(pedido.request.body).toEqual({ numeros: [12, 14], estado: 'ASIGNADO', idAgente: 9 });
      const resultado: ResultadoEnLoteDto = { aplicados: [12], omitidos: [{ numero: 14, codigo: 'TRANSICION_INVALIDA', motivo: 'El n.º 14 no puede pasar de Recibido a Resuelto' }] };
      pedido.flush(resultado);
      f.detectChanges();

      expect(aplicado).toHaveBeenCalledWith(resultado);
      expect(texto(f)).toContain('Cambió 1 reclamo: n.º 12');
      expect(texto(f)).toContain('No se pudo en 1 reclamo');
      expect(texto(f)).toContain('El n.º 14 no puede pasar de Recibido a Resuelto');
      expect(boton(f, 'Cerrar')).toBeDefined();
    });

    it('muestraCualquierCodigoDeOmitidoConSuMotivoAunqueSeaNuevo', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);
      elegir(f, 'Asignado');
      const select = el(f).querySelector('#agente-nuevo') as HTMLSelectElement;
      select.value = '9';
      select.dispatchEvent(new Event('change'));
      f.detectChanges();
      boton(f, 'Cambiar estado').click();
      http.expectOne(URL_ESTADO).flush({
        aplicados: [],
        omitidos: [
          { numero: 12, codigo: 'AGENTE_INVALIDO', motivo: 'El agente ya no está activo' },
          { numero: 13, codigo: 'ERROR', motivo: 'Algo falló en el reclamo 13' },
          { numero: 14, codigo: 'CODIGO_DEL_FUTURO', motivo: 'Motivo que todavía no existe' },
        ],
      });
      f.detectChanges();

      expect(texto(f)).toContain('No se pudo en 3 reclamos');
      expect(texto(f)).toContain('El agente ya no está activo');
      expect(texto(f)).toContain('Algo falló en el reclamo 13');
      expect(texto(f)).toContain('Motivo que todavía no existe');
    });

    it('otroEstadoNoMandaAgente', () => {
      const { f } = crear([{ numero: 12, estado: 'ASIGNADO' }]);
      elegir(f, 'En reparación');

      boton(f, 'Cambiar estado').click();

      expect(http.expectOne(URL_ESTADO).request.body).toEqual({ numeros: [12], estado: 'EN_REPARACION' });
    });

    it('variosReclamosListanTodosLosNumeros', () => {
      const { f } = crear([{ numero: 1, estado: 'RECIBIDO' }, { numero: 2, estado: 'ASIGNADO' }, { numero: 3, estado: 'RESUELTO' }]);
      expect(texto(f)).toContain('3 reclamos: n.º 1, n.º 2, n.º 3');
    });

    it('sinAgentesActivosLoExplicaEnLugarDeUnDesplegableVacio', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }], []);
      elegir(f, 'Asignado');

      expect(el(f).querySelector('#agente-nuevo')).toBeNull();
      expect(texto(f)).toContain('No hay agentes activos');
    });

    it('unErrorDelServidorSeMuestraYSePuedeReintentar', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);
      elegir(f, 'Resuelto');

      boton(f, 'Cambiar estado').click();
      http.expectOne(URL_ESTADO).flush({ detail: 'El agente no está activo.' }, { status: 400, statusText: 'Bad Request' });
      f.detectChanges();

      expect(alertas(f)).toEqual(['El agente no está activo.']);
      expect(boton(f, 'Cambiar estado').disabled).toBe(false);
    });

    it('noEnviaDosVecesMientrasEspera', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }]);
      elegir(f, 'Recibido');

      boton(f, 'Cambiar estado').click();
      f.detectChanges();
      boton(f, 'Cambiando').click();

      http.expectOne(URL_ESTADO).flush({ aplicados: [12], omitidos: [] });
    });

    it('cancelarCierraSinEnviarNada', () => {
      const { f, cerrar } = crear([{ numero: 12, estado: 'RECIBIDO' }]);

      boton(f, 'Cancelar').click();

      expect(cerrar).toHaveBeenCalled();
      http.expectNone(URL_ESTADO);
    });
  });

  describe('Descartar', () => {
    function crear(reclamos: ReclamoElegido[]) {
      const f = TestBed.createComponent(DialogoDescartar);
      f.componentRef.setInput('reclamos', reclamos);
      const aplicado = vi.fn();
      f.componentInstance.aplicado.subscribe(aplicado);
      f.detectChanges();
      return { f, aplicado };
    }
    const UNO: ReclamoElegido[] = [{ numero: 12, estado: 'RECIBIDO' }];

    it('ofreceLosCuatroMotivosYAvisaQueElVecinoLeeElTexto', () => {
      const { f } = crear(UNO);

      expect(Array.from(el(f).querySelectorAll('label.opcion')).map((l) => l.textContent?.trim())).toEqual([
        'Ya había un reclamo por el mismo problema',
        'No le corresponde a la comuna',
        'La comuna no lo puede atender',
        'Otro motivo',
      ]);
      expect(texto(f)).toContain('El vecino lee este texto: no pongas datos de otras personas.');
    });

    it('sinMotivoNoEnvia', () => {
      const { f } = crear(UNO);

      boton(f, 'Descartar reclamo').click();
      f.detectChanges();

      expect(alertas(f)).toContain('Elegí un motivo.');
      http.expectNone(URL_DESCARTE);
    });

    it('laObservacionEsObligatoriaSalvoConDuplicado', () => {
      const { f } = crear(UNO);
      elegir(f, 'No le corresponde a la comuna');

      boton(f, 'Descartar reclamo').click();
      f.detectChanges();
      expect(alertas(f)).toEqual(['Escribí por qué lo descartás. El vecino lo va a leer.']);
      expect(el(f).querySelector('#observacion')?.getAttribute('aria-invalid')).toBe('true');
      http.expectNone(URL_DESCARTE);

      // Solo espacios tampoco vale.
      escribir(f, '#observacion', '    ');
      boton(f, 'Descartar reclamo').click();
      http.expectNone(URL_DESCARTE);
    });

    it('descartaConMotivoYObservacionSinEspaciosSobrantes', () => {
      const { f, aplicado } = crear(UNO);
      elegir(f, 'La comuna no lo puede atender');
      escribir(f, '#observacion', '  Es un terreno privado.  ');

      boton(f, 'Descartar reclamo').click();
      const pedido = http.expectOne(URL_DESCARTE);
      expect(pedido.request.body).toEqual({ numeros: [12], motivo: 'NO_SE_PUEDE_ATENDER', observacion: 'Es un terreno privado.' });
      pedido.flush({ aplicados: [12], omitidos: [] });
      f.detectChanges();

      expect(aplicado).toHaveBeenCalled();
      expect(texto(f)).toContain('Se descartó 1 reclamo: n.º 12');
    });

    it('elContadorLlegaA300YNoDejaEscribirMas', () => {
      const { f } = crear(UNO);
      elegir(f, 'Otro motivo');

      escribir(f, '#observacion', 'x'.repeat(250));
      expect(texto(f)).toContain('250/300');
      escribir(f, '#observacion', 'x'.repeat(400));
      expect(texto(f)).toContain('300/300');
      expect((el(f).querySelector('#observacion') as HTMLTextAreaElement).getAttribute('maxlength')).toBe('300');
    });

    it('conDuplicadoPideElNumeroDelOriginalYLaObservacionEsOpcional', () => {
      const { f } = crear(UNO);
      elegir(f, 'Ya había un reclamo por el mismo problema');
      expect(texto(f)).toContain('Observación (opcional)');

      boton(f, 'Descartar reclamo').click();
      f.detectChanges();
      expect(alertas(f)).toEqual(['Escribí el número del reclamo original.']);
      http.expectNone(URL_DESCARTE);

      escribir(f, '#original', '7');
      boton(f, 'Descartar reclamo').click();
      const pedido = http.expectOne(URL_DESCARTE);
      expect(pedido.request.body).toEqual({ numeros: [12], motivo: 'DUPLICADO', observacion: null, numeroOriginal: 7 });
      pedido.flush({ aplicados: [12], omitidos: [] });
    });

    it('elOriginalNoPuedeSerUnoDeLosQueSeDescartan', () => {
      const { f } = crear([{ numero: 12, estado: 'RECIBIDO' }, { numero: 13, estado: 'ASIGNADO' }]);
      elegir(f, 'Ya había un reclamo por el mismo problema');
      escribir(f, '#original', '13');

      boton(f, 'Descartar 2 reclamos').click();
      f.detectChanges();

      expect(alertas(f)).toEqual(['El original no puede ser uno de los reclamos que estás descartando.']);
      http.expectNone(URL_DESCARTE);
    });

    it('elNumeroOriginalDebeSerUnNumero', () => {
      const { f } = crear(UNO);
      elegir(f, 'Ya había un reclamo por el mismo problema');
      escribir(f, '#original', 'abc');

      boton(f, 'Descartar reclamo').click();
      f.detectChanges();

      expect(alertas(f)).toEqual(['Escribí el número del reclamo original.']);
    });

    it('losCerradosYLosEnReparacionQuedanAfueraYLaVentanaLoExplicaAntesDeConfirmar', () => {
      const { f } = crear([
        { numero: 37, estado: 'RECIBIDO' },
        { numero: 38, estado: 'EN_REPARACION' },
        { numero: 39, estado: 'RESUELTO' },
      ]);

      expect(texto(f)).toContain('Descartar aplica a 1 de 3: el n.º 38 está en reparación y el n.º 39 ya está cerrado.');
      elegir(f, 'Otro motivo');
      escribir(f, '#observacion', 'Ya está programado.');
      boton(f, 'Descartar reclamo').click();

      // No viajan los que no se pueden descartar.
      const pedido = http.expectOne(URL_DESCARTE);
      expect(pedido.request.body.numeros).toEqual([37]);
      pedido.flush({ aplicados: [], omitidos: [{ numero: 37, codigo: 'CONFLICTO', motivo: 'El n.º 37 cambió mientras tanto' }] });
      f.detectChanges();
      expect(texto(f)).toContain('El n.º 37 cambió mientras tanto');
    });

    it('siNingunoSePuedeDescartarNoSePuedeConfirmar', () => {
      const { f } = crear([{ numero: 39, estado: 'RESUELTO' }, { numero: 40, estado: 'DESCARTADO' }, { numero: 41, estado: 'EN_REPARACION' }]);

      expect(texto(f)).toContain('Descartar aplica a 0 de 3: el n.º 41 está en reparación y los n.º 39 y n.º 40 ya están cerrados.');
      expect(el(f).querySelector('fieldset')).toBeNull();
      expect(boton(f, 'Descartar').disabled).toBe(true);
      http.expectNone(URL_DESCARTE);
    });

    it('unErrorDelServidorSeMuestraEnLaVentana', () => {
      const { f } = crear(UNO);
      elegir(f, 'Otro motivo');
      escribir(f, '#observacion', 'Texto');

      boton(f, 'Descartar reclamo').click();
      http.expectOne(URL_DESCARTE).flush({ detail: 'El reclamo original no existe.' }, { status: 400, statusText: 'Bad Request' });
      f.detectChanges();

      expect(alertas(f)).toEqual(['El reclamo original no existe.']);
    });

    it('lasObservacionesSeMuestranComoTextoPlano', () => {
      const { f } = crear(UNO);
      elegir(f, 'Otro motivo');
      escribir(f, '#observacion', '<b>negrita</b>');
      boton(f, 'Descartar reclamo').click();
      http.expectOne(URL_DESCARTE).flush({ aplicados: [], omitidos: [{ numero: 12, codigo: 'CONFLICTO', motivo: '<i>raro</i>' }] });
      f.detectChanges();

      expect(texto(f)).toContain('<i>raro</i>');
      expect(el(f).querySelector('i')).toBeNull();
    });
  });

  describe('textoDeAplicables', () => {
    it('unoEnReparacionYUnoCerradoSeExplicanLosDos', () => {
      expect(textoDeAplicables([{ numero: 1, estado: 'RECIBIDO' }, { numero: 3, estado: 'EN_REPARACION' }, { numero: 4, estado: 'DESCARTADO' }])).toBe(
        'Descartar aplica a 1 de 3: el n.º 3 está en reparación y el n.º 4 ya está cerrado.',
      );
    });

    it('sinCerradosNoDiceNada', () => {
      expect(textoDeAplicables([{ numero: 1, estado: 'RECIBIDO' }])).toBeNull();
    });

    it('conMuchosCerradosDiceCuantos', () => {
      const cerrados = [1, 2, 3, 4].map((n) => ({ numero: n, estado: 'RESUELTO' as const }));
      expect(textoDeAplicables([...cerrados, { numero: 5, estado: 'RECIBIDO' }])).toBe('Descartar aplica a 1 de 5: 4 ya están cerrados.');
    });
  });
});
