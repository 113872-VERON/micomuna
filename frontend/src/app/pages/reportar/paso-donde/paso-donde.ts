import { Component, ElementRef, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { centroDelLimite } from '../../../mapas/geo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { MapasService } from '../../../mapas/mapas.service';
import { Coordenadas, MAX_REFERENCIA } from '../../../reclamos/modelos';
import { BorradorReclamo } from '../borrador-reclamo';

// Sin límite ni punto conocido: una vista de la provincia (Córdoba), para que el vecino se acerque.
const VISTA_PROVINCIA: VistaMapa = { centro: { latitud: -31.4, longitud: -64.2 }, zoom: 7 };
const ZOOM_COMUNA = 14;
const ZOOM_PUNTO = 17;

// Paso 3: dónde está el problema. El vecino mueve el mapa hasta que el pin fijo queda en el lugar,
// o usa la ubicación del celular. El servidor exige el punto (latitud y longitud); la referencia
// escrita es opcional. Si el mapa no carga, queda el GPS (y la referencia).
@Component({
  selector: 'app-paso-donde',
  imports: [ReactiveFormsModule, MapaUbicacion],
  templateUrl: './paso-donde.html',
  styleUrl: './paso-donde.scss',
})
export class PasoDonde {
  protected readonly b = inject(BorradorReclamo);
  private readonly mapas = inject(MapasService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly maximo = MAX_REFERENCIA;
  protected readonly referencia = this.b.form.controls.referencia;
  protected readonly intento = signal(false);

  // Dónde mira el mapa. Se define una vez (con el punto, el límite o la provincia) y después solo
  // cambia cuando el GPS lo manda a otro lado: mover el mapa a mano no lo toca.
  protected readonly vista = signal<VistaMapa | null>(null);
  // El mapa se muestra cuando el límite ya está (así no salta de lugar al cargar el límite).
  protected readonly hayMapa = computed(() => this.b.estadoMapa() === 'listo' && this.b.limiteListo());
  protected readonly cargandoMapa = computed(() => this.b.estadoMapa() === 'listo' && !this.b.limiteListo());

  constructor() {
    this.b.cargarMapa();
    effect(() => {
      if (this.hayMapa() && this.vista() === null) {
        untracked(() => this.vista.set(this.vistaInicial()));
      }
    });
    effect(() => {
      const punto = this.b.ubicacion();
      if (punto && this.b.origenUbicacion() === 'gps') {
        untracked(() => this.vista.set({ centro: punto, zoom: ZOOM_PUNTO }));
      }
    });
  }

  private vistaInicial(): VistaMapa {
    const punto = this.b.ubicacion();
    if (punto) {
      return { centro: punto, zoom: ZOOM_PUNTO };
    }
    const centro = centroDelLimite(this.b.limite());
    return centro ? { centro, zoom: ZOOM_COMUNA } : VISTA_PROVINCIA;
  }

  // Sirve con el mapa real o con uno falso en las pruebas: solo se le pide el centro.
  private readonly mapa = viewChild<{ centroActual(): Coordenadas | null }>('mapa');

  // "Es acá": toma el centro actual del mapa como punto (igual que al mover el mapa). Solo cuando el vecino lo toca.
  protected esAca(): void {
    const centro = this.mapa()?.centroActual();
    if (centro) {
      this.b.fijarUbicacion(centro, 'mapa');
    }
  }

  protected alDetenerse(punto: Coordenadas): void {
    this.b.fijarUbicacion(punto, 'mapa');
  }

  protected faltaPunto(): boolean {
    return this.intento() && this.b.ubicacion() === null;
  }

  protected sinMapa(): void {
    this.mapas.marcarFalla();
  }

  protected siguiente(): void {
    this.intento.set(true);
    this.referencia.markAsTouched();
    if (this.b.ubicacion() === null) {
      this.host.nativeElement.querySelector<HTMLElement>('#usar-ubicacion')?.focus();
      return;
    }
    if (this.b.fueraDeComuna()) {
      return;
    }
    if (this.referencia.invalid) {
      this.host.nativeElement.querySelector<HTMLElement>('#referencia')?.focus();
      return;
    }
    this.b.ir(4);
  }
}
