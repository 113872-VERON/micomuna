import { Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

// Campo de contraseña con el botón del ojito (solo el ícono, sin la palabra "Ver").
@Component({
  selector: 'app-campo-clave',
  imports: [ReactiveFormsModule],
  template: `
    <label [attr.for]="idCampo()">{{ etiqueta() }}</label>
    <div class="campo" [class.error]="conError()">
      <input
        [id]="idCampo()"
        [type]="visible() ? 'text' : 'password'"
        [formControl]="control()"
        [attr.autocomplete]="autocompletar()"
        [attr.aria-invalid]="conError()"
        [attr.aria-describedby]="descripcion()"
      />
      <button
        type="button"
        class="ojo"
        [attr.aria-pressed]="visible()"
        [attr.aria-label]="visible() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
        (click)="visible.set(!visible())"
      >
        @if (visible()) {
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12z" /><circle cx="12" cy="12" r="3.2" /><path d="M3 3l18 18" />
          </svg>
        } @else {
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M1.5 12S5.5 5 12 5s10.5 7 10.5 7-4 7-10.5 7S1.5 12 1.5 12z" /><circle cx="12" cy="12" r="3.2" />
          </svg>
        }
      </button>
    </div>
  `,
  styles: `:host { display: block; }`,
})
export class CampoClave {
  readonly control = input.required<FormControl<string>>();
  readonly etiqueta = input('Contraseña');
  readonly idCampo = input('clave');
  readonly autocompletar = input<'current-password' | 'new-password'>('current-password');
  readonly conError = input(false);
  readonly descripcion = input<string | null>(null);

  protected readonly visible = signal(false);
}
