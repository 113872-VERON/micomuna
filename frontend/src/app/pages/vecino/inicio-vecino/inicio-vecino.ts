import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { EstadoReclamoVista } from '../../../compartido/estado-reclamo/estado-reclamo';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
import { MiReclamoDetalleDto, MiReclamoResumenDto } from '../../../reclamos/modelos';
import { soloDigitos } from '../../../reclamos/contacto';
import { ReclamosService } from '../../../reclamos/reclamos.service';

const POR_PAGINA = 10;

export { soloDigitos };

// Inicio del vecino (MC-17): su último reclamo en grande con la línea de pasos, los demás en tarjetas
// compactas, el botón para reportar y, si la comuna cargó un teléfono o WhatsApp, cómo comunicarse.
@Component({
  selector: 'app-inicio-vecino',
  imports: [DatePipe, RouterLink, ChipEstado, EstadoReclamoVista, FotoReclamo, IconoTipo],
  providers: [FotosDeReclamos],
  templateUrl: './inicio-vecino.html',
  styleUrl: './inicio-vecino.scss',
})
export class InicioVecino {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly reclamosApi = inject(ReclamosService);
  protected readonly auth = inject(AuthService);

  protected readonly carga = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly reclamos = signal<MiReclamoResumenDto[]>([]);
  protected readonly total = signal(0);
  protected readonly detalle = signal<MiReclamoDetalleDto | null>(null);
  protected readonly cargandoMas = signal(false);
  protected readonly errorMas = signal(false);

  private pagina = 0;

  protected readonly ultimo = computed(() => this.reclamos()[0] ?? null);
  protected readonly otros = computed(() => this.reclamos().slice(1));
  protected readonly hayMas = computed(() => this.reclamos().length < this.total());
  protected readonly telefono = computed(() => soloDigitos(this.comuna().telefono));
  protected readonly whatsapp = computed(() => soloDigitos(this.comuna().whatsapp));

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.carga.set('cargando');
    this.reclamosApi.misReclamos(0, POR_PAGINA).subscribe({
      next: (pagina) => {
        this.pagina = 0;
        this.reclamos.set(pagina.reclamos);
        this.total.set(pagina.total);
        this.carga.set('lista');
        const primero = pagina.reclamos[0];
        if (primero) {
          this.cargarDetalle(primero.numero);
        }
      },
      error: () => this.carga.set('error'),
    });
  }

  // El detalle del último trae el historial con la fecha de cada paso. Si falla, la tarjeta sigue
  // mostrando el estado (sin fechas): no vale la pena un error aparte.
  private cargarDetalle(numero: number): void {
    this.reclamosApi.detalleDeMiReclamo(numero).subscribe({
      next: (d) => this.detalle.set(d),
      error: () => this.detalle.set(null),
    });
  }

  protected verMas(): void {
    if (this.cargandoMas()) {
      return;
    }
    this.cargandoMas.set(true);
    this.errorMas.set(false);
    this.reclamosApi.misReclamos(this.pagina + 1, POR_PAGINA).subscribe({
      next: (pagina) => {
        this.pagina = pagina.pagina;
        // Si el vecino reportó algo mientras tanto, las páginas se corren y podría repetirse un reclamo.
        const vistos = new Set(this.reclamos().map((r) => r.numero));
        this.reclamos.update((lista) => [...lista, ...pagina.reclamos.filter((r) => !vistos.has(r.numero))]);
        this.total.set(pagina.total);
        this.cargandoMas.set(false);
      },
      error: () => {
        this.cargandoMas.set(false);
        this.errorMas.set(true);
      },
    });
  }
}
