import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ComunaDto } from './modelos';

@Injectable({ providedIn: 'root' })
export class ComunaService {
  private readonly http = inject(HttpClient);

  obtener(slug: string): Observable<ComunaDto> {
    return this.http.get<ComunaDto>(`/api/comunas/${encodeURIComponent(slug)}`);
  }
}
