import { Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { CuentaService } from '../../../auth/cuenta.service';
import { mensajeDeError } from '../../../auth/errores';
import { inicioDeUsuario } from '../../../auth/inicio-de-usuario';
import { nombreDeRol } from '../../../auth/modelos';

const NOMBRE_MAX = 100;

// Un nombre de solo espacios no vale (el servidor lo guarda sin espacios a los costados).
function conTexto(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;
}

// Mis datos: el nombre se puede cambiar; el correo y el rol se ven pero no se tocan desde acá.
// Al guardar, el usuario de la sesión se actualiza y la barra muestra el nombre nuevo enseguida.
@Component({
  selector: 'app-mis-datos',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './mis-datos.html',
  styleUrl: '../cuenta.scss',
})
export class MisDatos {
  private readonly auth = inject(AuthService);
  private readonly cuenta = inject(CuentaService);

  protected readonly usuario = this.auth.usuario;
  protected readonly rol = computed(() => {
    const u = this.usuario();
    return u ? nombreDeRol(u.rol) : '';
  });
  // A quién pedirle el cambio de correo o rol: el administrador de la comuna no tiene a quién en su comuna, y el superadmin a nadie.
  protected readonly consejo = computed(() => {
    switch (this.usuario()?.rol) {
      case 'SUPERADMIN':
        return null;
      case 'ADMIN_COMUNA':
        return 'Si necesitás cambiarlos, pedíselo a soporte.';
      default:
        return 'Si necesitás cambiarlos, pedíselo a quien administra tu comuna.';
    }
  });
  protected readonly inicio =computed(() => inicioDeUsuario(this.usuario()));

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: [this.auth.usuario()?.nombre ?? '', [Validators.required, Validators.maxLength(NOMBRE_MAX), conTexto]],
  });

  protected readonly guardando = signal(false);
  protected readonly guardado = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // Datos al día desde el servidor (otro dispositivo pudo haber cambiado el nombre). Si falla, quedan los de la sesión.
    this.cuenta.misDatos().subscribe({
      next: (datos) => {
        if (!this.form.controls.nombre.dirty) {
          this.form.controls.nombre.setValue(datos.nombre);
        }
        this.auth.actualizarUsuario({ ...this.auth.usuario(), ...datos });
      },
      error: () => undefined,
    });
  }

  protected get nombre() {
    return this.form.controls.nombre;
  }

  protected mensajeNombre(): string {
    return this.nombre.hasError('maxlength') ? `El nombre puede tener hasta ${NOMBRE_MAX} caracteres.` : 'Escribí tu nombre y apellido.';
  }

  protected guardar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    this.guardado.set(false);
    this.error.set(null);
    this.cuenta.cambiarNombre(this.nombre.value.trim()).subscribe({
      next: (datos) => {
        this.guardando.set(false);
        this.guardado.set(true);
        this.auth.actualizarUsuario({ ...this.auth.usuario(), ...datos });
        this.form.reset({ nombre: datos.nombre });
      },
      error: (e: unknown) => {
        this.guardando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
