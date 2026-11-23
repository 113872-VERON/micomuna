import { anchoDeBarra, diasDelRango, diaYMes, errorDelRango, fechaCorta, hoyEnArgentina, textoDeSemana, textoDeTiempo, textoDelPeriodo, ultimosDias } from './metricas-formato';

describe('fechas de las métricas', () => {
  it('ultimosDiasCuentaHoyYTermina7DiasAtras', () => {
    expect(ultimosDias(7, '2026-11-03')).toEqual({ desde: '2026-10-28', hasta: '2026-11-03' });
    expect(ultimosDias(30, '2026-11-03')).toEqual({ desde: '2026-10-05', hasta: '2026-11-03' });
    expect(diasDelRango('2026-10-05', '2026-11-03')).toBe(30);
  });

  it('atraviesaFinDeAnioYBisiesto', () => {
    expect(ultimosDias(7, '2027-01-03')).toEqual({ desde: '2026-12-28', hasta: '2027-01-03' });
    expect(diasDelRango('2028-02-28', '2028-03-01')).toBe(3);
  });

  it('hoyEnArgentinaUsaLaFechaDeAlla', () => {
    // 01:30 UTC del 4/11 todavía es 3/11 en Argentina (UTC-3).
    expect(hoyEnArgentina(new Date('2026-11-04T01:30:00Z'))).toBe('2026-11-03');
    expect(hoyEnArgentina(new Date('2026-11-04T03:30:00Z'))).toBe('2026-11-04');
  });

  it('diasDelRangoContaLosDosExtremos', () => {
    expect(diasDelRango('2026-11-03', '2026-11-03')).toBe(1);
    expect(diasDelRango('2026-11-04', '2026-11-03')).toBe(0);
    expect(diasDelRango('2026-02-30', '2026-03-03')).toBeNull();
  });

  it('formatosDeFechaArgentinos', () => {
    expect(fechaCorta('2026-10-01')).toBe('1/10/2026');
    expect(diaYMes('2026-11-09')).toBe('9/11');
    expect(textoDeSemana('2026-11-03', '2026-11-09')).toBe('3/11 al 9/11');
    expect(textoDeSemana('2026-11-03', '2026-11-03')).toBe('3/11');
    expect(textoDelPeriodo('2026-10-01', '2026-11-03', 34)).toBe('Mostrando del 1/10/2026 al 3/11/2026 (34 días)');
    expect(textoDelPeriodo('2026-11-03', '2026-11-03', 1)).toBe('Mostrando del 3/11/2026 al 3/11/2026 (1 día)');
  });
});

describe('errorDelRango', () => {
  it('unRangoBuenoNoTieneError', () => {
    expect(errorDelRango('2026-10-01', '2026-11-03')).toBeNull();
    expect(errorDelRango('2026-11-03', '2026-11-03')).toBeNull();
  });

  it('desdePosteriorAHastaTieneElTextoPedido', () => {
    expect(errorDelRango('2026-11-04', '2026-11-03')).toBe('La fecha «desde» tiene que ser anterior o igual a «hasta».');
  });

  it('masDe366DiasSeAvisa', () => {
    expect(errorDelRango('2026-01-01', '2027-01-01')).toBeNull();
    expect(errorDelRango('2025-11-01', '2026-11-03')).toContain('366 días');
  });

  it('exactamente366DiasAlcanza', () => {
    expect(errorDelRango('2025-11-03', '2026-11-03')).toBeNull();
  });

  it('faltaUnaFechaOEstaMalEscrita', () => {
    expect(errorDelRango('', '2026-11-03')).toContain('las dos fechas');
    expect(errorDelRango(null, null)).toContain('las dos fechas');
    expect(errorDelRango('ayer', '2026-11-03')).toContain('no es válida');
  });
});

describe('textoDeTiempo', () => {
  it('hastaCuarentaYOchoHorasVaEnHorasConComa', () => {
    expect(textoDeTiempo(5.2)).toBe('5,2 horas');
    expect(textoDeTiempo(48)).toBe('48 horas');
    expect(textoDeTiempo(1)).toBe('1 hora');
    expect(textoDeTiempo(0)).toBe('0 horas');
  });

  it('despuesDeCuarentaYOchoHorasVaEnDias', () => {
    expect(textoDeTiempo(50.4)).toBe('2,1 días');
    expect(textoDeTiempo(72)).toBe('3 días');
    expect(textoDeTiempo(48.1)).toBe('2 días');
  });

  it('sinResueltosLoDice', () => {
    expect(textoDeTiempo(null)).toBe('Sin resueltos');
    expect(textoDeTiempo(undefined)).toBe('Sin resueltos');
  });
});

describe('anchoDeBarra', () => {
  it('laMayorOcupaTodoYLasDemasEnProporcion', () => {
    expect(anchoDeBarra(10, 10)).toBe(100);
    expect(anchoDeBarra(5, 10)).toBe(50);
  });

  it('ceroNoTieneBarraYUnaChicaSiempreSeVe', () => {
    expect(anchoDeBarra(0, 10)).toBe(0);
    expect(anchoDeBarra(0, 0)).toBe(0);
    expect(anchoDeBarra(1, 1000)).toBe(2);
  });
});
