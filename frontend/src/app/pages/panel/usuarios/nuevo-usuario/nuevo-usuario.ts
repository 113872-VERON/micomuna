import { DOCUMENT } from '@angular/common';
import { Component, Injector, afterNextRender, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { mensajeDeError } from '../../../../auth/errores';
import { limpiarCorreo } from '../../../../auth/mensajes-de-formulario';
import { NOMBRES_ROL, RolDePersonal, UsuarioCreadoDto } from '../../../../auth/modelos';
import { AyudaFlotante } from '../../../../compartido/ayuda-flotante/ayuda-flotante';
import { UsuariosService } from '../../../../panel/usuarios.service';
import { CorregirCorreo } from '../corregir-correo/corregir-correo';
import { DESCRIPCIONES_ROL } from '../roles';

// Pantalla de alta de una persona del personal (/:slug/panel/usuarios/nuevo). Los vecinos no se dan de alta acá: se registran solos.
// El usuario nuevo aparece en la lista porque la lista se carga de nuevo al entrar.
@Component({
  selector: 'app-nuevo-usuario',
  imports: [ReactiveFormsModule, RouterLink, CorregirCorreo, AyudaFlotante],
  templateUrl: './nuevo-usuario.html',
  styleUrl: './nuevo-usuario.scss',
})
export class NuevoUsuario {
  private readonly servicio = inject(UsuariosService);

  // Viene de la ruta (/:slug/panel/usuarios/nuevo).
  readonly slug = input.required<string>();
  protected readonly textoInfo =
    'Le mandamos un correo con un enlace para que elija su propia contraseña.\nLos vecinos no se dan de alta acá: se registran solos con el enlace de la comuna.';

  protected readonly roles = (['ADMINISTRATIVO', 'AGENTE', 'ADMIN_COMUNA'] as const).map((clave) => ({
    clave,
    nombre: NOMBRES_ROL[clave],
    descripcion: DESCRIPCIONES_ROL[clave],
  }));

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    rol: ['' as RolDePersonal | '', Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultado = signal<UsuarioCreadoDto | null>(null);
  protected readonly reenviando = signal(false);
  protected readonly errorEnvio = signal<string | null>(null);
  protected readonly corrigiendo = signal(false);
  protected readonly errorCorreccion = signal<string | null>(null);

  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  constructor() {
    afterNextRender(() => this.enfocar('nuevo-nombre'));
  }

  protected get c() {
    return this.form.controls;
  }

  protected mensajeNombre(): string {
    return this.c.nombre.hasError('maxlength') ? 'El nombre puede tener hasta 100 caracteres.' : 'Escribí el nombre y apellido.';
  }

  protected mensajeCorreo(): string {
    if (this.c.correo.hasError('required')) {
      return 'Escribí el correo.';
    }
    if (this.c.correo.hasError('maxlength')) {
      return 'El correo puede tener hasta 254 caracteres.';
    }
    return 'Revisá el correo: tiene que ser como ejemplo@correo.com.';
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) {
      return;
    }
    const { nombre, correo, rol } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.servicio.crear({ nombre: nombre.trim(), correo: limpiarCorreo(correo), rol: rol as RolDePersonal }).subscribe({
      next: (resultado) => {
        this.enviando.set(false);
        this.resultado.set(resultado);
        this.enfocarTitulo();
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected reintentarEnvio(): void {
    const resultado = this.resultado();
    if (!resultado || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.errorEnvio.set(null);
    this.servicio.reenviarInvitacion(resultado.usuario.id).subscribe({
      next: () => {
        this.reenviando.set(false);
        this.resultado.set({ ...resultado, invitacionEnviada: true });
        this.enfocarTitulo();
      },
      error: (e: unknown) => {
        this.reenviando.set(false);
        this.errorEnvio.set(mensajeDeError(e));
      },
    });
  }

  protected abrirCorreccion(): void {
    this.errorCorreccion.set(null);
    this.corrigiendo.set(true);
  }

  protected corregirCorreo(correo: string): void {
    const resultado = this.resultado();
    if (!resultado || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.errorCorreccion.set(null);
    this.servicio.corregirCorreoYReenviar(resultado.usuario.id, correo).subscribe({
      next: ({ usuario, invitacionEnviada, errorEnvio }) => {
        this.reenviando.set(false);
        this.corrigiendo.set(false);
        this.resultado.set({ usuario, invitacionEnviada });
        this.errorEnvio.set(invitacionEnviada ? null : mensajeDeError(errorEnvio));
        this.enfocarTitulo();
      },
      error: (e: unknown) => {
        this.reenviando.set(false);
        this.errorCorreccion.set(mensajeDeError(e));
      },
    });
  }

  private enfocarTitulo(): void {
    // El título cambia ("Invitación enviada" o "el correo no salió"): el foco lo acompaña.
    afterNextRender(() => this.enfocar('titulo-nuevo-usuario'), { injector: this.injector });
  }

  private enfocar(id: string): void {
    this.documento.getElementById(id)?.focus();
  }
}
