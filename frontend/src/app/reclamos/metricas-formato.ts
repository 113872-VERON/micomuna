// Fechas y números de la pantalla de métricas (MC-21). Las fechas viajan como "2026-11-03" (día de calendario,
// hora de Argentina) y se muestran como 3/11/2026. No se usa `Date` con zona horaria para no correr un día.

export const MAX_DIAS = 366;
const MS_DIA = 86_400_000;

const UN_DECIMAL = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });
export const ENTERO = new Intl.NumberFormat('es-AR');

// "2026-11-03" -> milisegundos UTC de ese día, o null si no es una fecha real.
function aMs(iso: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m) {
    return null;
  }
  const [a, mes, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const fecha = new Date(Date.UTC(a, mes - 1, d));
  return fecha.getUTCFullYear() === a && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === d ? fecha.getTime() : null;
}

const aIso = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function esFechaIso(valor: string | null | undefined): valor is string {
  return !!valor && aMs(valor) !== null;
}

// Hoy en Argentina (no el día del reloj del teléfono, que puede estar en otra zona).
export function hoyEnArgentina(ahora = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora);
}

// Los últimos N días hasta hoy, contando hoy: 7 días son hoy y los seis anteriores.
export function ultimosDias(dias: number, hoy: string): { desde: string; hasta: string } {
  const fin = aMs(hoy) ?? Date.now();
  return { desde: aIso(fin - (dias - 1) * MS_DIA), hasta: aIso(fin) };
}

// Cantidad de días de un rango, contando los dos extremos. null si alguna fecha no es válida.
export function diasDelRango(desde: string, hasta: string): number | null {
  const a = aMs(desde);
  const b = aMs(hasta);
  return a === null || b === null ? null : Math.round((b - a) / MS_DIA) + 1;
}

// Qué le falta o le sobra al rango, con el texto para mostrar. null si está bien.
export function errorDelRango(desde: string | null | undefined, hasta: string | null | undefined): string | null {
  if (!desde || !hasta) {
    return 'Elegí las dos fechas: desde y hasta.';
  }
  const dias = diasDelRango(desde, hasta);
  if (dias === null) {
    return 'Alguna de las fechas no es válida. Revisalas y probá de nuevo.';
  }
  if (dias < 1) {
    return 'La fecha «desde» tiene que ser anterior o igual a «hasta».';
  }
  if (dias > MAX_DIAS) {
    return `El período no puede pasar de ${MAX_DIAS} días. Acortalo y probá de nuevo.`;
  }
  return null;
}

// "2026-11-03" -> "3/11/2026"
export function fechaCorta(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${Number(m[3])}/${Number(m[2])}/${m[1]}` : iso;
}

// "2026-11-03" -> "3/11"
export function diaYMes(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${Number(m[3])}/${Number(m[2])}` : iso;
}

// "3/11 al 9/11"
export function textoDeSemana(desde: string, hasta: string): string {
  return desde === hasta ? diaYMes(desde) : `${diaYMes(desde)} al ${diaYMes(hasta)}`;
}

// "Mostrando del 1/10/2026 al 3/11/2026 (34 días)"
export function textoDelPeriodo(desde: string, hasta: string, dias: number): string {
  return `Mostrando del ${fechaCorta(desde)} al ${fechaCorta(hasta)} (${dias === 1 ? '1 día' : `${dias} días`})`;
}

// Horas hasta 48; después, días. Un decimal, con coma. Sin resueltos no hay promedio.
export function textoDeTiempo(horas: number | null | undefined): string {
  if (horas === null || horas === undefined || !Number.isFinite(horas) || horas < 0) {
    return 'Sin resueltos';
  }
  if (horas > 48) {
    const dias = horas / 24;
    return `${UN_DECIMAL.format(dias)} días`;
  }
  return `${UN_DECIMAL.format(horas)} ${horas === 1 ? 'hora' : 'horas'}`;
}

// Ancho de una barra (0 a 100) respecto de la mayor. Con todo en 0 no hay barra.
export function anchoDeBarra(cantidad: number, maximo: number): number {
  return maximo > 0 && cantidad > 0 ? Math.max(2, Math.round((cantidad / maximo) * 100)) : 0;
}
