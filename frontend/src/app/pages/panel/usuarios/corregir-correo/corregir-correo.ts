import { DOCUMENT } from '@angular/common';
import { Component, OnInit, afterNextRender, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { limpiarCorreo } from '../../../../auth/mensajes-de-formulario';

// Formulario de una línea para corregir el correo de alguien que todavía no eligió su contraseña.
// No llama al servidor: avisa el correo nuevo y quien lo usa hace el cambio y el reenvío.
@Component({
  selector: 'app-corregir-correo',
  imports: [ReactiveFormsModule],
  template: `
    <form class="corregir" [formGroup]="form" (ngSubmit)="enviar()" novalidate>
      <label [for]="idCampo()">Correo corregido</label>
      <input
        [id]="idCampo()"
        type="email"
        formControlName="correo"
        autocomplete="off"
        autocapitalize="none"
        spellcheck="false"
        inputmode="email"
        enterkeyhint="send"
        [attr.aria-invalid]="mostrarError()"
        [attr.aria-describedby]="idCampo() + '-ayuda'"
      />
      <p class="ayuda-c" [id]="idCampo() + '-ayuda'">Guardamos el correo nuevo y le mandamos otra invitación. La anterior deja de servir.</p>
      @if (mostrarError()) {
        <p class="msg-error" role="alert">{{ mensaje() }}</p>
      }
      @if (error(); as mensajeServidor) {
        <p class="msg-error caja-error" role="alert">{{ mensajeServidor }}</p>
      }
      <div class="acciones">
        <button type="button" class="btn sec chico" [disabled]="enviando()" (click)="cancelar.emit()">Cancelar</button>
        <button type="submit" class="btn pri chico" [disabled]="enviando() || deshabilitado()" [attr.aria-busy]="enviando()">
          {{ enviando() ? 'Guardando…' : 'Guardar y reenviar' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .corregir { display: flex; flex-direction: column; gap: 6px; max-width: 520px; }
    .ayuda-c, .msg-error { margin: 0; }
    .acciones { margin-top: 6px; }
  `,
})
export class CorregirCorreo implements OnInit {
  readonly correo = input.required<string>();
  readonly idCampo = input.required<string>();
  readonly enviando = input(false);
  // Otro pedido de la pantalla está en curso: no se puede guardar todavía (sin decir "Guardando…").
  readonly deshabilitado = input(false);
  readonly error = input<string | null>(null);

  readonly guardar = output<string>();
  readonly cancelar = output<void>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });
  private readonly mismoCorreo = signal(false);

  constructor() {
    // Se abre para escribir: el foco va directo al campo.
    const documento = inject(DOCUMENT);
    afterNextRender(() => documento.getElementById(this.idCampo())?.focus());
    // Si vuelve a escribir, el aviso de "mismo correo" ya no aplica.
    this.form.controls.correo.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.mismoCorreo.set(false));
  }

  ngOnInit(): void {
    this.form.controls.correo.setValue(this.correo());
  }

  protected mostrarError(): boolean {
    const control = this.form.controls.correo;
    return (control.touched && control.invalid) || this.mismoCorreo();
  }

  protected mensaje(): string {
    const control = this.form.controls.correo;
    if (control.hasError('required')) {
      return 'Escribí el correo.';
    }
    if (control.hasError('maxlength')) {
      return 'El correo puede tener hasta 254 caracteres.';
    }
    if (control.invalid) {
      return 'Revisá el correo: tiene que ser como ejemplo@correo.com.';
    }
    return 'Es el mismo correo de antes. Si está bien escrito, usá "Reenviar invitación".';
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    const correo = limpiarCorreo(this.form.controls.correo.value);
    this.mismoCorreo.set(this.form.valid && correo === limpiarCorreo(this.correo()));
    if (this.form.invalid || this.mismoCorreo() || this.enviando() || this.deshabilitado()) {
      return;
    }
    this.guardar.emit(correo);
  }
}
