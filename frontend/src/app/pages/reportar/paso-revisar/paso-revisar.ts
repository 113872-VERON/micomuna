import { Component, computed, inject } from '@angular/core';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { MapasService } from '../../../mapas/mapas.service';
import { BorradorReclamo } from '../borrador-reclamo';

// Paso 4: todo junto antes de enviar. Cada fila tiene "Cambiar", que vuelve a su paso sin perder nada.
@Component({
  selector: 'app-paso-revisar',
  imports: [IconoTipo, MapaUbicacion],
  templateUrl: './paso-revisar.html',
  styleUrl: './paso-revisar.scss',
})
export class PasoRevisar {
  protected readonly b = inject(BorradorReclamo);
  private readonly mapas = inject(MapasService);
  protected readonly descripcion = this.b.form.controls.descripcion;

  // Mapa chico de solo lectura con el punto; sin mapa disponible no se muestra.
  protected readonly vistaMapa = computed<VistaMapa | null>(() => {
    const punto = this.b.ubicacion();
    return punto && this.b.estadoMapa() === 'listo' ? { centro: punto, zoom: 16 } : null;
  });

  protected sinMapa(): void {
    this.mapas.marcarFalla();
  }
}
