import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { EstadoReclamoVista } from '../../../compartido/estado-reclamo/estado-reclamo';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { MiReclamoDetalleDto } from '../../../reclamos/modelos';
import { soloDigitos } from '../../../reclamos/contacto';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
import { ReclamosService } from '../../../reclamos/reclamos.service';
import { nombreDeEstado, volvioAtras } from '../../../reclamos/seguimiento';

interface LineaDeHistorial {
  nombre: string;
  fecha: string;
  volvio: boolean;
}

// Detalle de un reclamo del vecino (MC-17): pasos, datos, mapa, fotos, historial completo y, según el
// estado, el motivo del descarte o lo que se hizo. Un reclamo ajeno o inexistente da el mismo 404.
@Component({
  selector: 'app-detalle-reclamo',
  imports: [DatePipe, RouterLink, ChipEstado, EstadoReclamoVista, FotoReclamo, IconoTipo, MapaUbicacion],
  providers: [FotosDeReclamos],
  templateUrl: './detalle-reclamo.html',
  styleUrl: './detalle-reclamo.scss',
})
export class DetalleReclamo {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  // Viene de la dirección (/:slug/reclamos/:numero), como texto.
  readonly numero = input.required<string>();

  private readonly reclamosApi = inject(ReclamosService);

  protected readonly carga = signal<'cargando' | 'lista' | 'noEncontrado' | 'error'>('cargando');
  protected readonly reclamo = signal<MiReclamoDetalleDto | null>(null);
  protected readonly telefono = computed(() => soloDigitos(this.comuna().telefono));
  protected readonly whatsapp = computed(() => soloDigitos(this.comuna().whatsapp));
  protected readonly sinMapa = signal(false);
  private readonly intento = signal(0);

  protected readonly fotosDelVecino = computed(() => (this.reclamo()?.fotos ?? []).filter((f) => f.origen === 'VECINO'));
  protected readonly fotosDelTrabajo = computed(() => {
    const r = this.reclamo();
    // Las fotos del trabajo se muestran solo con el reclamo resuelto, sin importar lo que mande el servidor.
    return r?.estado === 'RESUELTO' ? r.fotos.filter((f) => f.origen === 'TRABAJO') : [];
  });
  protected readonly vistaMapa = computed<VistaMapa | null>(() => {
    const r = this.reclamo();
    return r && !this.sinMapa() ? { centro: { latitud: r.latitud, longitud: r.longitud }, zoom: 16 } : null;
  });
  protected readonly historial = computed<LineaDeHistorial[]>(() => {
    const cambios = this.reclamo()?.historial ?? [];
    return cambios.map((c, i) => ({ nombre: nombreDeEstado(c.estado), fecha: c.fecha, volvio: volvioAtras(cambios[i - 1]?.estado, c.estado) }));
  });

  constructor() {
    effect((alLimpiar) => {
      const texto = this.numero();
      this.intento();
      const numero = /^\d{1,9}$/.test(texto) ? Number(texto) : 0;
      untracked(() => {
        this.carga.set('cargando');
        this.reclamo.set(null);
        this.sinMapa.set(false);
      });
      if (numero < 1) {
        untracked(() => this.carga.set('noEncontrado'));
        return;
      }
      const sub = this.reclamosApi.detalleDeMiReclamo(numero).subscribe({
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
}
