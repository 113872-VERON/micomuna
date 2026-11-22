import { Injectable, OnDestroy, effect, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ReclamosService } from './reclamos.service';

// Memoria de las fotos ya bajadas mientras una pantalla está abierta. El servidor responde las fotos con
// `Cache-Control: no-store`, así que el navegador no las guarda: sin esto, cada vez que una foto se vuelve
// a dibujar se pediría de nuevo. Cada pantalla del vecino la declara en `providers`: vive lo que vive la
// pantalla y, al destruirse (o al cerrar la sesión), revoca todas las URLs de objeto y suelta las imágenes.
@Injectable()
export class FotosDeReclamos implements OnDestroy {
  private readonly api = inject(ReclamosService);
  private readonly auth = inject(AuthService);
  private entradas = new Map<string, Observable<string>>();
  private urls = new Set<string>();
  // Cambia cada vez que se vacía la memoria: una descarga que termina después no deja URLs sueltas.
  private generacion = 0;

  constructor() {
    effect(() => {
      if (!this.auth.usuario()) {
        this.limpiar();
      }
    });
  }

  // La URL de objeto de la foto: una sola descarga por número e id, aunque la pidan varios.
  url(numero: number, idFoto: number): Observable<string> {
    const clave = `${numero}/${idFoto}`;
    let entrada = this.entradas.get(clave);
    if (!entrada) {
      const generacion = this.generacion;
      entrada = this.api.fotoDeMiReclamo(numero, idFoto).pipe(
        map((blob) => {
          if (generacion !== this.generacion) {
            throw new Error('La memoria de fotos se vació');
          }
          const url = URL.createObjectURL(blob);
          this.urls.add(url);
          return url;
        }),
        // Un error no se guarda: al reintentar se vuelve a pedir.
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.entradas.set(clave, entrada);
    }
    return entrada;
  }

  // Revoca todas las URLs y olvida lo bajado.
  limpiar(): void {
    this.generacion++;
    this.urls.forEach((u) => URL.revokeObjectURL(u));
    this.urls.clear();
    this.entradas.clear();
  }

  ngOnDestroy(): void {
    this.limpiar();
  }
}
