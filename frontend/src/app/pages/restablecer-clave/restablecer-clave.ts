import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { AccesoService } from '../../auth/acceso.service';
import { mensajeDeError } from '../../auth/errores';
import { CLAVE_MAX, CLAVE_MIN, mensajeClaveNueva } from '../../auth/mensajes-de-formulario';
import { ComunaDto } from '../../auth/modelos';
import { CampoClave } from '../../compartido/campo-clave/campo-clave';

// Pantalla a la que lleva el enlace del correo: /{slug}/restablecer?codigo=...
@Component({
  selector: 'app-restablecer-clave',
  imports: [ReactiveFormsModule, RouterLink, CampoClave],
  templateUrl: './restablecer-clave.html',
})
export class RestablecerClave {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  readonly codigo = input<string | undefined>();

  private readonly acceso = inject(AccesoService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    clave: ['', [Validators.required, Validators.minLength(CLAVE_MIN), Validators.maxLength(CLAVE_MAX)]],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly listo = signal(false);
  private readonly rechazado = signal(false);
  // Sin código en la URL, o con uno que el backend rechazó, el enlace "ya no sirve".
  protected readonly vencido = computed(() => this.rechazado() || !this.codigo());
  protected readonly largoCumplido = toSignal(
    this.form.controls.clave.valueChanges.pipe(map((valor) => valor.length >= CLAVE_MIN)),
    { initialValue: false },
  );

  protected get clave() {
    return this.form.controls.clave;
  }

  protected mensajeClave(): string {
    return mensajeClaveNueva(this.clave);
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    const codigo = this.codigo();
    if (this.form.invalid || this.enviando() || !codigo) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.acceso.restablecer(this.slug(), codigo, this.clave.value).subscribe({
      next: () => {
        this.enviando.set(false);
        this.listo.set(true);
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        // Un 400 que no es de validación significa código inválido, vencido o ya usado: el backend no distingue cuál.
        const codigoDeError = e instanceof HttpErrorResponse ? e.error?.codigo : null;
        if (e instanceof HttpErrorResponse && e.status === 400 && codigoDeError !== 'VALIDACION') {
          this.rechazado.set(true);
        } else {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }
}
