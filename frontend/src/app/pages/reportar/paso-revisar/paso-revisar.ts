import { Component, inject } from '@angular/core';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { BorradorReclamo } from '../borrador-reclamo';

// Paso 4: todo junto antes de enviar. Cada fila tiene "Cambiar", que vuelve a su paso sin perder nada.
@Component({
  selector: 'app-paso-revisar',
  imports: [IconoTipo],
  templateUrl: './paso-revisar.html',
  styleUrl: './paso-revisar.scss',
})
export class PasoRevisar {
  protected readonly b = inject(BorradorReclamo);
  protected readonly descripcion = this.b.form.controls.descripcion;
}
