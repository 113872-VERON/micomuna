import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
import { AgenteDto, ReclamoElegido, ReclamoPanelDetalleDto, sePuedeDescartar } from '../../../reclamos/modelos-panel';
import { textoDeMotivo } from '../../../reclamos/seguimiento';
import { DialogoCambiarEstado } from './dialogo-cambiar-estado';
import { DialogoDescartar } from './dialogo-descartar';

// Detalle de un reclamo para el personal (MC-18): lo que reportó el vecino, la foto, el mapa, el historial
// completo con quién hizo cada cambio, el descarte, lo que se hizo y los duplicados vinculados. Todo texto de
// usuarios se muestra como texto plano. Asignar con la carga de cada agente y la IA son de MC-19.
@Component({
  selector: 'app-detalle-panel',
  imports: [DatePipe, RouterLink, ChipEstado, FotoReclamo, IconoTipo, MapaUbicacion, DialogoCambiarEstado, DialogoDescartar],
  providers: [FotosDeReclamos],
  templateUrl: './detalle-panel.html',
  styleUrl: './detalle-panel.scss',
})
export class DetallePanel {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  // Viene de la dirección (/:slug/panel/reclamos/:numero), como texto.
  readonly numero = input.required<string>();

  private readonly api = inject(ReclamosPanelService);

  protected readonly carga = signal<'cargando' | 'lista' | 'noEncontrado' | 'error'>('cargando');
  protected readonly reclamo = signal<ReclamoPanelDetalleDto | null>(null);
  protected readonly agentes = signal<readonly AgenteDto[]>([]);
  protected readonly sinMapa = signal(false);
  protected readonly dialogo = signal<'estado' | 'descarte' | null>(null);
  private readonly intento = signal(0);

  protected readonly fotosDelVecino = computed(() => (this.reclamo()?.fotos ?? []).filter((f) => f.origen === 'VECINO'));
  // Las del trabajo solo con el reclamo resuelto, como en la pantalla del vecino.
  protected readonly fotosDelTrabajo = computed(() => {
    const r = this.reclamo();
    return r?.estado === 'RESUELTO' ? r.fotos.filter((f) => f.origen === 'TRABAJO') : [];
  });
  protected readonly vistaMapa = computed<VistaMapa | null>(() => {
    const r = this.reclamo();
    return r && r.latitud !== null && r.longitud !== null && !this.sinMapa() ? { centro: { latitud: r.latitud, longitud: r.longitud }, zoom: 16 } : null;
  });
  protected readonly sinDescarte = computed(() => {
    const estado = this.reclamo()?.estado ?? 'RECIBIDO';
    if (sePuedeDescartar(estado)) {
      return null;
    }
    return estado === 'EN_REPARACION' ? 'No se puede descartar: el reclamo está en reparación' : 'No se puede descartar: el reclamo ya está cerrado';
  });
  protected readonly elegido = computed<readonly ReclamoElegido[]>(() => {
    const r = this.reclamo();
    return r ? [{ numero: r.numero, estado: r.estado }] : [];
  });
  protected readonly motivo = computed(() => textoDeMotivo(this.reclamo()?.descarte?.motivo ?? 'OTRO'));

  constructor() {
    this.api.agentes().subscribe({ next: (a) => this.agentes.set(a), error: () => this.agentes.set([]) });
    effect((alLimpiar) => {
      const texto = this.numero();
      this.intento();
      const numero = /^\d{1,9}$/.test(texto) ? Number(texto) : 0;
      untracked(() => {
        // Al recargar el mismo reclamo (después de un cambio) se sigue mostrando lo que había.
        if (this.reclamo()?.numero !== numero) {
          this.carga.set('cargando');
          this.reclamo.set(null);
          this.sinMapa.set(false);
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

  protected cerrarDialogo(): void {
    this.dialogo.set(null);
  }

  // Algo cambió: se vuelve a pedir el detalle, sin esperar a que se cierre la ventana.
  protected alAplicar(): void {
    this.intento.update((n) => n + 1);
  }
}
