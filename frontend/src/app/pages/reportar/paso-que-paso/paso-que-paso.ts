import { Component, ElementRef, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MAX_DESCRIPCION } from '../../../reclamos/modelos';
import { BorradorReclamo } from '../borrador-reclamo';

// Paso 1: el tipo de problema (seis botones con ícono y palabra) y la descripción con sus palabras.
@Component({
  selector: 'app-paso-que-paso',
  imports: [ReactiveFormsModule, IconoTipo],
  templateUrl: './paso-que-paso.html',
  styleUrl: './paso-que-paso.scss',
})
export class PasoQuePaso {
  protected readonly b = inject(BorradorReclamo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly maximo = MAX_DESCRIPCION;
  protected readonly tipo = this.b.form.controls.tipo;
  protected readonly descripcion = this.b.form.controls.descripcion;

  protected siguiente(): void {
    this.tipo.markAsTouched();
    this.descripcion.markAsTouched();
    if (this.b.tipos().length > 0 && this.tipo.invalid) {
      this.enfocar('input[name="tipo"]');
      return;
    }
    if (this.tipo.invalid) {
      // Sin tipos cargados no hay qué elegir: el aviso de error de carga ya está a la vista.
      return;
    }
    if (this.descripcion.invalid) {
      this.enfocar('#descripcion');
      return;
    }
    this.b.ir(2);
  }

  private enfocar(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }
}
