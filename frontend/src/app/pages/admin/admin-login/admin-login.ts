import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AccesoService } from '../../../auth/acceso.service';
import { AuthService } from '../../../auth/auth.service';
import { mensajeDeError } from '../../../auth/errores';
import { limpiarCorreo, mensajeCorreo } from '../../../auth/mensajes-de-formulario';
import { CampoClave } from '../../../compartido/campo-clave/campo-clave';

// Ingreso del superadmin: no pertenece a ninguna comuna, así que no hay cabecera de comuna.
@Component({
  selector: 'app-admin-login',
  imports: [ReactiveFormsModule, CampoClave],
  templateUrl: './admin-login.html',
  styles: `
    :host { display: block; min-height: 100dvh; background: var(--fondo); }
    .cab { background: var(--p); color: #fff; padding: 52px 24px 56px; display: flex; flex-direction: column; gap: 6px; }
    @media (min-width: 900px) { .cab { justify-content: center; padding: 48px 56px; } }
    .plataforma { font-size: 18px; font-weight: 500; display: flex; gap: 8px; align-items: center; }
    .marca { font-size: 40px; line-height: 1.08; font-weight: 700; }
  `,
})
export class AdminLogin {
  private readonly acceso = inject(AccesoService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email]],
    clave: ['', Validators.required],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected get correo() {
    return this.form.controls.correo;
  }

  protected get clave() {
    return this.form.controls.clave;
  }

  protected mensajeCorreo(): string {
    return mensajeCorreo(this.correo);
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { correo, clave } = this.form.getRawValue();
    this.acceso.iniciarSesionAdmin(limpiarCorreo(correo), clave).subscribe({
      next: (respuesta) => {
        this.auth.guardarSesion(respuesta);
        void this.router.navigateByUrl('/admin/comunas');
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
