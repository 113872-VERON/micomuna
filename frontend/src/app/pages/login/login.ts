import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccesoService } from '../../auth/acceso.service';
import { AuthService } from '../../auth/auth.service';
import { mensajeDeError } from '../../auth/errores';
import { limpiarCorreo, mensajeCorreo } from '../../auth/mensajes-de-formulario';
import { ComunaDto, UsuarioDto } from '../../auth/modelos';
import { CampoClave } from '../../compartido/campo-clave/campo-clave';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, CampoClave],
  templateUrl: './login.html',
})
export class Login {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

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
    this.acceso.iniciarSesion(this.slug(), limpiarCorreo(correo), clave).subscribe({
      next: (respuesta) => {
        this.auth.guardarSesion(respuesta);
        void this.router.navigateByUrl(this.destinoDespuesDeEntrar(respuesta.usuario));
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  // El administrador de la comuna va a su panel, el vecino a su inicio; el resto, por ahora, a la pantalla provisoria.
  private destinoDespuesDeEntrar(usuario: UsuarioDto): string {
    if (usuario.rol === 'ADMIN_COMUNA') {
      return `/${encodeURIComponent(usuario.comunaSlug ?? this.slug())}/panel/usuarios`;
    }
    if (usuario.rol === 'VECINO') {
      return `/${encodeURIComponent(usuario.comunaSlug ?? this.slug())}/inicio`;
    }
    return '/inicio';
  }
}
