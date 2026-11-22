import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MAX_DIRECCION, NuevoReclamo, ReclamoCreadoDto, TipoReclamoDto } from './modelos';

// Arma el formulario multipart con los campos planos del contrato y las fotos en "fotos".
// La comuna no se manda: el backend la toma del token.
export function aFormData(reclamo: NuevoReclamo): FormData {
  const datos = new FormData();
  datos.append('tipo', reclamo.tipo);
  datos.append('descripcion', reclamo.descripcion.trim());
  if (reclamo.ubicacion) {
    // El servidor guarda 6 decimales (unos 10 cm): no hace falta mandar más.
    datos.append('latitud', reclamo.ubicacion.latitud.toFixed(6));
    datos.append('longitud', reclamo.ubicacion.longitud.toFixed(6));
  }
  const direccion = reclamo.direccion?.trim();
  if (direccion) {
    datos.append('direccion', direccion.slice(0, MAX_DIRECCION));
  }
  const referencia = reclamo.referencia.trim();
  if (referencia) {
    datos.append('referencia', referencia);
  }
  // Las fotos ya llegan achicadas a JPEG en el celular.
  reclamo.fotos.forEach((foto, i) => datos.append('fotos', foto, `foto-${i + 1}.jpg`));
  return datos;
}

@Injectable({ providedIn: 'root' })
export class ReclamosService {
  private readonly http = inject(HttpClient);

  listarTipos(): Observable<TipoReclamoDto[]> {
    return this.http.get<TipoReclamoDto[]>('/api/tipos-reclamo');
  }

  // El límite de la comuna del token (GeoJSON), o null si no tiene (el servidor responde 204).
  limiteDeMiComuna(): Observable<unknown> {
    return this.http.get<unknown>('/api/mi-comuna/limite');
  }

  // Sin Content-Type a mano: el navegador lo pone con el separador del multipart.
  crear(reclamo: NuevoReclamo): Observable<ReclamoCreadoDto> {
    return this.http.post<ReclamoCreadoDto>('/api/reclamos', aFormData(reclamo));
  }
}
