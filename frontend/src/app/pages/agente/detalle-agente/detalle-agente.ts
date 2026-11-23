import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { enlaceComoLlegar } from '../../../agente/distancia';
import { codigoDeError, mensajeDeAgente } from '../../../agente/errores-agente';
import { ReclamoAgenteDetalleDto } from '../../../agente/modelos-agente';
import { ReclamosAgenteService } from '../../../agente/reclamos-agente.service';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
import { DialogoResolver } from './dialogo-resolver';

// Detalle de un reclamo del agente (MC-20): fotos del vecino, descripción, dirección, mapa, "Cómo llegar" y un solo
// botón de acción según el estado. Sin datos del vecino. Un reclamo ajeno o inexistente da el mismo 404.
@Component({
  selector: 'app-detalle-agente',
  imports: [DatePipe, RouterLink, ChipEstado, FotoReclamo, IconoTipo, MapaUbicacion, DialogoResolver],
  providers: [FotosDeReclamos],
  templateUrl: './detalle-agente.html',
  styleUrl: './detalle-agente.scss',
})
export class DetalleAgente {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  // Viene de la dirección (/:slug/agente/reclamos/:numero), como texto.
  readonly numero = input.required<string>();

  private readonly api = inject(ReclamosAgenteService);

  protected readonly carga = signal<'cargando' | 'lista' | 'noEncontrado' | 'error'>('cargando');
  protected readonly reclamo = signal<ReclamoAgenteDetalleDto | null>(null);
  protected readonly sinMapa = signal(false);
  protected readonly resolviendo = signal(false);
  protected readonly empezando = signal(false);
  protected readonly errorAccion = signal<string | null>(null);
  private readonly intento = signal(0);

  protected readonly fotosDelVecino = computed(() => (this.reclamo()?.fotos ?? []).filter((f) => f.origen === 'VECINO'));
  // Las del trabajo solo con el reclamo resuelto.
  protected readonly fotosDelTrabajo = computed(() => {
    const r = this.reclamo();
    return r?.estado === 'RESUELTO' ? r.fotos.filter((f) => f.origen === 'TRABAJO') : [];
  });
  protected readonly vistaMapa = computed<VistaMapa | null>(() => {
    const r = this.reclamo();
    return r && r.latitud !== null && r.longitud !== null && !this.sinMapa() ? { centro: { latitud: r.latitud, longitud: r.longitud }, zoom: 16 } : null;
  });
  protected readonly sinCoordenadas = computed(() => {
    const r = this.reclamo();
    return !!r && (r.latitud === null || r.longitud === null);
  });
  protected readonly comoLlegar = computed(() => {
    const r = this.reclamo();
    return r ? enlaceComoLlegar(r.latitud, r.longitud) : null;
  });

  constructor() {
    effect((alLimpiar) => {
      const texto = this.numero();
      this.intento();
      const numero = /^\d{1,9}$/.test(texto) ? Number(texto) : 0;
      untracked(() => {
        // Al volver a pedir el mismo reclamo se sigue mostrando lo que había.
        if (this.reclamo()?.numero !== numero) {
          this.carga.set('cargando');
          this.reclamo.set(null);
          this.sinMapa.set(false);
          this.errorAccion.set(null);
        }
      });
      if (numero < 1) {
        untracked(() => this.carga.set('noEncontrado'));
        return;
      }
      const sub = this.api.detalle(numero).subscribe({
        next: (r) => {
          this.reclamo.set(r);
          this.carga.set('lista');
        },
        error: (e: unknown) => this.carga.set(e instanceof HttpErrorResponse && e.status === 404 ? 'noEncontrado' : 'error'),
      });
      alLimpiar(() => sub.unsubscribe());
    });
  }

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  // Pasa de Asignado a En reparación.
  protected empezar(): void {
    const r = this.reclamo();
    if (!r || this.empezando()) {
      return;
    }
    this.empezando.set(true);
    this.errorAccion.set(null);
    this.api.empezar(r.numero).subscribe({
      next: (nuevo) => {
        this.reclamo.set(nuevo);
        this.empezando.set(false);
      },
      error: (e: unknown) => {
        this.empezando.set(false);
        this.errorAccion.set(mensajeDeAgente(e));
        const codigo = codigoDeError(e);
        if (codigo === 'TRANSICION_INVALIDA' || codigo === 'CONFLICTO') {
          this.reintentar();
        }
      },
    });
  }

  protected alResolver(nuevo: ReclamoAgenteDetalleDto): void {
    this.reclamo.set(nuevo);
    this.resolviendo.set(false);
    this.errorAccion.set(null);
  }
}
