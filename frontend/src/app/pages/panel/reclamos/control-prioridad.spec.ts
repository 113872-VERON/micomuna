import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../../auth/auth.service';
import { ReclamoPanelDetalleDto } from '../../../reclamos/modelos-panel';
import { PrioridadPanelDto } from '../../../reclamos/prioridad';
import { ControlPrioridad, mensajeDePrioridad } from './control-prioridad';

describe('ControlPrioridad', () => {
  let http: HttpTestingController;
  let actualizados: ReclamoPanelDetalleDto[];
  let desactualizados: number;

  beforeEach(() => {
    localStorage.clear();
    actualizados = [];
    desactualizados = 0;
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Marta Gil', correo: 'marta@correo.com', rol: 'ADMINISTRATIVO', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  function crear(prioridad: PrioridadPanelDto | null, cerrado = false): ComponentFixture<ControlPrioridad> {
    const f = TestBed.createComponent(ControlPrioridad);
    f.componentRef.setInput('numero', 42);
    f.componentRef.setInput('prioridad', prioridad);
    f.componentRef.setInput('cerrado', cerrado);
    f.componentInstance.actualizado.subscribe((d) => actualizados.push(d));
    f.componentInstance.desactualizado.subscribe(() => desactualizados++);
    f.detectChanges();
    return f;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const texto = (f: ComponentFixture<unknown>) => el(f).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const opciones = (f: ComponentFixture<unknown>) => Array.from(el(f).querySelectorAll<HTMLButtonElement>('button.opcion'));
  const opcion = (f: ComponentFixture<unknown>, nombre: string) => opciones(f).find((b) => b.textContent?.trim().startsWith(nombre)) as HTMLButtonElement;
  const DETALLE = { numero: 42, prioridad: { valor: 'ALTA', origen: 'PERSONAL', fijadaPor: 'Marta Gil', fijadaEn: '2026-11-26T10:00:00' } } as unknown as ReclamoPanelDetalleDto;
  const DEL_PERSONAL: PrioridadPanelDto = { valor: 'MEDIA', origen: 'PERSONAL', fijadaPor: 'Marta Gil', fijadaEn: '2026-11-26T10:00:00' };

  it('conPrioridadDeLaIADiceQueEsLaSugeridaYQueElegirlaLaConfirma', () => {
    const f = crear({ valor: 'ALTA', origen: 'IA' });

    expect(texto(f)).toContain('sugirió la IA');
    expect(texto(f)).toContain('la confirmás');
    expect(opciones(f).map((b) => b.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Alta (sugerida)', 'Media', 'Baja']);
    expect(opciones(f).every((b) => b.getAttribute('aria-pressed') === 'false')).toBe(true);
  });

  it('conPrioridadDelPersonalDiceQuienYCuandoYMarcaLaElegida', () => {
    const f = crear(DEL_PERSONAL);

    expect(texto(f)).toContain('Fijada por Marta Gil el 26/11/2026.');
    expect(opcion(f, 'Media').getAttribute('aria-pressed')).toBe('true');
    expect(opcion(f, 'Alta').getAttribute('aria-pressed')).toBe('false');
  });

  it('sinPrioridadLoDiceYOfreceLasTres', () => {
    const f = crear({ valor: null, origen: 'NINGUNA' });

    expect(texto(f)).toContain('Todavía no tiene prioridad');
    expect(opciones(f).length).toBe(3);
  });

  it('sinDatoDelServidorSeTrataComoSinPrioridad', () => {
    const f = crear(null);

    expect(texto(f)).toContain('Todavía no tiene prioridad');
  });

  it('alElegirUnaMandaElPutConLaPrioridadYAvisaConElDetalleNuevo', () => {
    const f = crear({ valor: null, origen: 'NINGUNA' });

    opcion(f, 'Alta').click();
    f.detectChanges();
    expect(opciones(f).every((b) => b.disabled)).toBe(true);
    const p = http.expectOne('/api/panel/reclamos/42/prioridad');
    expect(p.request.method).toBe('PUT');
    expect(p.request.body).toEqual({ prioridad: 'ALTA' });
    p.flush(DETALLE);
    f.detectChanges();

    expect(actualizados).toEqual([DETALLE]);
    expect(texto(f)).toContain('Prioridad fijada: alta.');
    expect(opciones(f).every((b) => !b.disabled)).toBe(true);
  });

  it('elegirLaSugeridaDiceQueLaConfirmo', () => {
    const f = crear({ valor: 'ALTA', origen: 'IA' });

    opcion(f, 'Alta').click();
    http.expectOne('/api/panel/reclamos/42/prioridad').flush(DETALLE);
    f.detectChanges();

    expect(texto(f)).toContain('Confirmaste la prioridad alta que sugirió la IA.');
  });

  it('laQueYaFijoElPersonalNoVuelveAEnviarse', () => {
    const f = crear(DEL_PERSONAL);

    opcion(f, 'Media').click();

    http.expectNone('/api/panel/reclamos/42/prioridad');
    expect(opcion(f, 'Media').getAttribute('aria-disabled')).toBe('true');
  });

  it('conElReclamoCerradoSeVeSinPoderCambiarla', () => {
    const f = crear({ ...DEL_PERSONAL, valor: 'ALTA' }, true);

    expect(opciones(f).length).toBe(0);
    expect(texto(f)).toContain('Alta');
    expect(texto(f)).toContain('ya no se puede cambiar');
  });

  describe('errores', () => {
    const fallar = (f: ComponentFixture<ControlPrioridad>, codigo: string) => {
      opcion(f, 'Baja').click();
      http.expectOne('/api/panel/reclamos/42/prioridad').flush({ codigo, detail: 'x' }, { status: 409, statusText: 'Conflicto' });
      f.detectChanges();
    };
    const alerta = (f: ComponentFixture<unknown>) => el(f).querySelector('[role="alert"]')?.textContent ?? '';

    it('reclamoCerradoLoDiceEnEspañolYPideVolverAMirar', () => {
      const f = crear({ valor: null, origen: 'NINGUNA' });
      fallar(f, 'RECLAMO_CERRADO');

      expect(alerta(f)).toContain('El reclamo ya está cerrado');
      expect(desactualizados).toBe(1);
      expect(actualizados).toEqual([]);
    });

    it('mismaPrioridadYConflictoTambienVuelvenAMirar', () => {
      const f = crear({ valor: null, origen: 'NINGUNA' });
      fallar(f, 'MISMA_PRIORIDAD');
      expect(alerta(f)).toContain('ya tiene esa prioridad');

      fallar(f, 'CONFLICTO');
      expect(alerta(f)).toContain('Otra persona está cambiando');
      expect(desactualizados).toBe(2);
    });

    it('unErrorDeServidorMuestraElMensajeGeneralYNoPideVolverAMirar', () => {
      const f = crear({ valor: null, origen: 'NINGUNA' });
      opcion(f, 'Baja').click();
      http.expectOne('/api/panel/reclamos/42/prioridad').flush(null, { status: 500, statusText: 'Error' });
      f.detectChanges();

      expect(alerta(f)).toContain('Ocurrió un error inesperado');
      expect(desactualizados).toBe(0);
      expect(opciones(f).every((b) => !b.disabled)).toBe(true);
    });
  });

  it('mensajeDePrioridadCaeEnElGeneralConUnCodigoDesconocido', () => {
    expect(mensajeDePrioridad(new Error('x'), 'OTRO')).toContain('error inesperado');
  });
});
