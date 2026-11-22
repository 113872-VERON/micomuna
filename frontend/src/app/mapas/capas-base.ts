// Mapas base libres. La capa principal es Argenmap del IGN; si no responde se usa OpenStreetMap.
// Cada una pide su atribución visible en el mapa (condición de sus licencias).

// Servicio de teselas (TMS) de Argenmap del IGN, en Web Mercator. Su TMS cuenta la y desde abajo:
// en Leaflet se resuelve con `tms: true`. Verificado el 22/11/2026: responde PNG hasta el zoom 19.
export const URL_ARGENMAP = 'https://wms.ign.gob.ar/geoserver/gwc/service/tms/1.0.0/capabaseargenmap@EPSG%3A3857@png/{z}/{x}/{y}.png';
export const ATRIBUCION_IGN = '© <a href="https://www.ign.gob.ar" target="_blank" rel="noopener">Instituto Geográfico Nacional</a>';

export const URL_OSM = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
export const ATRIBUCION_OSM = '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">colaboradores de OpenStreetMap</a>';

// Argenmap se arma con datos de OpenStreetMap: con esa capa se muestran las dos atribuciones.
export const ATRIBUCION_ARGENMAP = `${ATRIBUCION_IGN}, ${ATRIBUCION_OSM}`;

export const ZOOM_MAXIMO = 19;
// Si ninguna tesela llega en este tiempo, el paso sigue sin mapa.
export const ESPERA_TESELAS_MS = 10000;
// Cuántos errores seguidos de Argenmap (sin que cargue ninguna tesela) hacen falta para pasar a OpenStreetMap.
export const ERRORES_PARA_RESPALDO = 3;

// Decide cuándo abandonar Argenmap: errores de carga sin haber cargado nunca una tesela.
export class RespaldoDeTeselas {
  private errores = 0;
  private cargo = false;
  private usandoRespaldo = false;

  get enRespaldo(): boolean {
    return this.usandoRespaldo;
  }

  // Una tesela de la capa principal cargó bien: el servicio funciona, los errores sueltos no cuentan.
  teselaCargada(): void {
    this.cargo = true;
  }

  // Devuelve true cuando hay que cambiar a la capa de respaldo (una sola vez).
  teselaConError(): boolean {
    if (this.usandoRespaldo || this.cargo) {
      return false;
    }
    this.errores++;
    if (this.errores >= ERRORES_PARA_RESPALDO) {
      this.usandoRespaldo = true;
      return true;
    }
    return false;
  }
}
