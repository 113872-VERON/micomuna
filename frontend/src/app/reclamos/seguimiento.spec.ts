import { CambioDeEstadoDto } from './modelos';
import { fechaDelEstado, nombreDeEstado, pasosDelReclamo, textoDeMotivo, volvioAtras } from './seguimiento';

const h = (estado: CambioDeEstadoDto['estado'], fecha: string): CambioDeEstadoDto => ({ estado, fecha });

describe('seguimiento', () => {
  it('losNombresDeEstadoYMotivoSonLosAcordados', () => {
    expect(['RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO'].map(nombreDeEstado)).toEqual([
      'Recibido', 'Asignado', 'En reparación', 'Resuelto', 'Descartado',
    ]);
    expect(textoDeMotivo('DUPLICADO')).toBe('Ya había un reclamo por el mismo problema');
    expect(textoDeMotivo('NO_ES_DE_LA_COMUNA')).toBe('No le corresponde a la comuna');
    expect(textoDeMotivo('NO_SE_PUEDE_ATENDER')).toBe('La comuna no lo puede atender');
    expect(textoDeMotivo('OTRO')).toBe('Otro motivo');
  });

  it('enReparacionTieneDosPasosHechosUnoActualYUnoPendiente', () => {
    const pasos = pasosDelReclamo('EN_REPARACION', [h('RECIBIDO', 'a'), h('ASIGNADO', 'b'), h('EN_REPARACION', 'c')]);
    expect(pasos.map((p) => p.situacion)).toEqual(['hecho', 'hecho', 'actual', 'pendiente']);
    expect(pasos.map((p) => p.fecha)).toEqual(['a', 'b', 'c', null]);
  });

  it('unPasoSaltadoNoTieneFechaPeroCuentaComoHecho', () => {
    const pasos = pasosDelReclamo('EN_REPARACION', [h('RECIBIDO', 'a'), h('EN_REPARACION', 'c')]);
    expect(pasos[1]).toEqual({ estado: 'ASIGNADO', nombre: 'Asignado', situacion: 'hecho', fecha: null });
  });

  it('siVolvioAtrasLosPasosPosterioresQuedanPendientesYSinFecha', () => {
    const pasos = pasosDelReclamo('ASIGNADO', [h('RECIBIDO', 'a'), h('ASIGNADO', 'b'), h('EN_REPARACION', 'c'), h('ASIGNADO', 'd')]);
    expect(pasos.map((p) => p.situacion)).toEqual(['hecho', 'actual', 'pendiente', 'pendiente']);
    expect(pasos[1].fecha).toBe('d');
    expect(pasos[2].fecha).toBeNull();
  });

  it('descartadoNoTienePasos', () => {
    expect(pasosDelReclamo('DESCARTADO', [h('RECIBIDO', 'a'), h('DESCARTADO', 'b')])).toEqual([]);
  });

  it('sinHistorialLosPasosIgualSeArman', () => {
    const pasos = pasosDelReclamo('RECIBIDO', []);
    expect(pasos.map((p) => p.situacion)).toEqual(['actual', 'pendiente', 'pendiente', 'pendiente']);
    expect(pasos[0].fecha).toBeNull();
  });

  it('laFechaDeUnEstadoEsLaDeLaUltimaVez', () => {
    expect(fechaDelEstado([h('RECIBIDO', 'a'), h('ASIGNADO', 'b'), h('RECIBIDO', 'c')], 'RECIBIDO')).toBe('c');
    expect(fechaDelEstado([], 'RESUELTO')).toBeNull();
  });

  it('detectaCuandoUnReclamoVuelveAUnEstadoAnterior', () => {
    expect(volvioAtras(undefined, 'RECIBIDO')).toBe(false);
    expect(volvioAtras('RECIBIDO', 'ASIGNADO')).toBe(false);
    expect(volvioAtras('EN_REPARACION', 'ASIGNADO')).toBe(true);
    expect(volvioAtras('RESUELTO', 'EN_REPARACION')).toBe(true);
    expect(volvioAtras('RECIBIDO', 'DESCARTADO')).toBe(false);
    expect(volvioAtras('DESCARTADO', 'RECIBIDO')).toBe(true);
  });
});
