import { Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AccesoService } from '../../auth/acceso.service';
import { AuthService } from '../../auth/auth.service';
import { mensajeDeError } from '../../auth/errores';
import { CLAVE_MAX, CLAVE_MIN, limpiarCorreo, mensajeClaveNueva, mensajeCorreo } from '../../auth/mensajes-de-formulario';
import { ComunaDto } from '../../auth/modelos';
import { CampoClave } from '../../compartido/campo-clave/campo-clave';

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, CampoClave],
  templateUrl: './registro.html',
})
export class Registro {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly acceso = inject(AccesoService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    correo: ['', [Validators.required, Validators.email]],
    clave: ['', [Validators.required, Validators.minLength(CLAVE_MIN), Validators.maxLength(CLAVE_MAX)]],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly largoCumplido = toSignal(
    this.form.controls.clave.valueChanges.pipe(map((valor) => valor.length >= CLAVE_MIN)),
    { initialValue: false },
  );

  protected get nombre() {
    return this.form.controls.nombre;
  }

  protected get correo() {
    return this.form.controls.correo;
  }

  protected get clave() {
    return this.form.controls.clave;
  }

  protected mensajeCorreo(): string {
    return mensajeCorreo(this.correo);
  }

  protected mensajeClave(): string {
    return mensajeClaveNueva(this.clave);
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { nombre, correo, clave } = this.form.getRawValue();
    this.acceso.registrar(this.slug(), nombre.trim(), limpiarCorreo(correo), clave).subscribe({
      next: (respuesta) => {
        this.auth.guardarSesion(respuesta);
        void this.router.navigateByUrl('/inicio');
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
