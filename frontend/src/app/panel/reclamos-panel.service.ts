import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FiltrosPanel, aParamsDeApi } from '../reclamos/filtros-panel';
import {
  AgenteDto,
  CambioDeEstadoEnLoteRequest,
  DescarteEnLoteRequest,
  MapaReclamosDto,
  PaginaReclamosPanelDto,
  ReclamoPanelDetalleDto,
  ResultadoEnLoteDto,
  ResumenPanelDto,
} from '../reclamos/modelos-panel';

const BASE = '/api/panel';

// El panel de reclamos del personal. La comuna sale siempre del token: nunca viaja en la URL ni en el cuerpo.
@Injectable({ providedIn: 'root' })
export class ReclamosPanelService {
  private readonly http = inject(HttpClient);

  listar(filtros: FiltrosPanel, pagina: number, tamanio: number): Observable<PaginaReclamosPanelDto> {
    return this.http.get<PaginaReclamosPanelDto>(`${BASE}/reclamos`, { params: { ...aParamsDeApi(filtros), pagina, tamanio } });
  }

  mapa(filtros: FiltrosPanel): Observable<MapaReclamosDto> {
    return this.http.get<MapaReclamosDto>(`${BASE}/reclamos/mapa`, { params: aParamsDeApi(filtros) });
  }

  // Cuenta por estado: no lleva el filtro de estado, para que el desplegable muestre cuántos hay en cada uno.
  resumen(filtros: FiltrosPanel): Observable<ResumenPanelDto> {
    return this.http.get<ResumenPanelDto>(`${BASE}/reclamos/resumen`, { params: aParamsDeApi(filtros, false) });
  }

  detalle(numero: number): Observable<ReclamoPanelDetalleDto> {
    return this.http.get<ReclamoPanelDetalleDto>(`${BASE}/reclamos/${numero}`);
  }

  // La imagen pide el token: se baja como blob y la pantalla arma la URL de objeto.
  foto(numero: number, idFoto: number): Observable<Blob> {
    return this.http.get(`${BASE}/reclamos/${numero}/fotos/${idFoto}`, { responseType: 'blob' });
  }

  agentes(): Observable<AgenteDto[]> {
    return this.http.get<AgenteDto[]>(`${BASE}/agentes`);
  }

  cambiarEstado(pedido: CambioDeEstadoEnLoteRequest): Observable<ResultadoEnLoteDto> {
    return this.http.post<ResultadoEnLoteDto>(`${BASE}/reclamos/cambios-de-estado`, pedido);
  }

  descartar(pedido: DescarteEnLoteRequest): Observable<ResultadoEnLoteDto> {
    return this.http.post<ResultadoEnLoteDto>(`${BASE}/reclamos/descartes`, pedido);
  }
}
