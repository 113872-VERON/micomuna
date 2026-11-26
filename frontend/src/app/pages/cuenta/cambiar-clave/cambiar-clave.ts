import { DOCUMENT } from '@angular/common';
import { Component, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { CuentaService } from '../../../auth/cuenta.service';
import { mensajeDeError } from '../../../auth/errores';
import { inicioDeUsuario } from '../../../auth/inicio-de-usuario';
import { CLAVE_MAX, CLAVE_MIN } from '../../../auth/mensajes-de-formulario';

function igualALaNueva(repetida: AbstractControl): ValidationErrors | null {
  const nueva = repetida.parent?.get('claveNueva')?.value;
  return repetida.value && nueva !== undefined && repetida.value !== nueva ? { noCoincide: true } : null;
}

// Cambiar la contraseña con la sesión iniciada: pide la actual, la nueva y la nueva otra vez.
@Component({
  selector: 'app-cambiar-clave',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './cambiar-clave.html',
  styleUrl: '../cuenta.scss',
})
export class CambiarClave {
  private readonly auth = inject(AuthService);
  private readonly cuenta = inject(CuentaService);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  protected readonly inicio = computed(() => inicioDeUsuario(this.auth.usuario()));
  protected readonly min = CLAVE_MIN;
  protected readonly max = CLAVE_MAX;

  protected readonly form = inject(NonNullableFormBuilder).group({
    claveActual: ['', Validators.required],
    claveNueva: ['', [Validators.required, Validators.minLength(CLAVE_MIN), Validators.maxLength(CLAVE_MAX)]],
    claveRepetida: ['', [Validators.required, igualALaNueva]],
  });

  protected readonly visible = signal(false);
  protected readonly enviando = signal(false);
  protected readonly cambiada = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Si cambia la nueva, hay que volver a comparar con la repetida.
    this.form.controls.claveNueva.valueChanges.subscribe(() => this.form.controls.claveRepetida.updateValueAndValidity());
  }

  protected get c() {
    return this.form.controls;
  }

  protected mensajeNueva(): string {
    return this.c.claveNueva.hasError('required') ? 'Elegí una contraseña nueva.' : `La contraseña tiene que tener entre ${CLAVE_MIN} y ${CLAVE_MAX} caracteres.`;
  }

  protected mensajeRepetida(): string {
    return this.c.claveRepetida.hasError('required') ? 'Escribí la contraseña nueva otra vez.' : 'Las dos contraseñas nuevas tienen que ser iguales.';
  }

  protected cambiar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) {
      return;
    }
    const { claveActual, claveNueva } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.cuenta.cambiarClave(claveActual, claveNueva).subscribe({
      next: () => {
        this.enviando.set(false);
        this.cambiada.set(true);
        this.form.reset();
        afterNextRender(() => this.documento.getElementById('titulo-cambiar-clave')?.focus(), { injector: this.injector });
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
