import { Component, ElementRef, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MAX_REFERENCIA } from '../../../reclamos/modelos';
import { BorradorReclamo } from '../borrador-reclamo';

// Paso 3: dónde está el problema. Alcanza con una de las dos: la ubicación del celular o una
// dirección o referencia escrita (pueden ir las dos).
// MC-16 suma el mapa con el pin fijo arriba de "Usar mi ubicación": el mapa escribe en
// BorradorReclamo.ubicacion, igual que el GPS, y el resto del paso no cambia.
@Component({
  selector: 'app-paso-donde',
  imports: [ReactiveFormsModule],
  templateUrl: './paso-donde.html',
  styleUrl: './paso-donde.scss',
})
export class PasoDonde {
  protected readonly b = inject(BorradorReclamo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly maximo = MAX_REFERENCIA;
  protected readonly referencia = this.b.form.controls.referencia;
  protected readonly intento = signal(false);

  protected faltaLugar(): boolean {
    return this.intento() && !this.b.tieneLugar();
  }

  protected siguiente(): void {
    this.intento.set(true);
    this.referencia.markAsTouched();
    if (!this.b.tieneLugar()) {
      this.host.nativeElement.querySelector<HTMLElement>('#referencia')?.focus();
      return;
    }
    if (this.referencia.invalid) {
      this.host.nativeElement.querySelector<HTMLElement>('#referencia')?.focus();
      return;
    }
    this.b.ir(4);
  }
}
