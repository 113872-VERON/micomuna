import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Salud {
  estado: string;
}

@Injectable({ providedIn: 'root' })
export class SaludService {
  private readonly http = inject(HttpClient);

  consultar(): Observable<Salud> {
    return this.http.get<Salud>('/api/salud');
  }
}
