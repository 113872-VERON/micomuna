import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccesoService } from '../../auth/acceso.service';
import { mensajeDeError } from '../../auth/errores';
import { limpiarCorreo, mensajeCorreo } from '../../auth/mensajes-de-formulario';
import { ComunaDto } from '../../auth/modelos';

@Component({
  selector: 'app-recuperar-clave',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './recuperar-clave.html',
})
export class RecuperarClave {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly acceso = inject(AccesoService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  // El correo al que se pidió el enlace. El mensaje es el mismo exista o no la cuenta.
  protected readonly pedidoA = signal<string | null>(null);

  protected get correo() {
    return this.form.controls.correo;
  }

  protected mensajeCorreo(): string {
    return mensajeCorreo(this.correo);
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.pedir(limpiarCorreo(this.correo.value));
  }

  protected reenviar(): void {
    const destino = this.pedidoA();
    if (destino) {
      this.pedir(destino);
    }
  }

  private pedir(correo: string): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.acceso.recuperar(this.slug(), correo).subscribe({
      next: () => {
        this.enviando.set(false);
        this.pedidoA.set(correo);
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
