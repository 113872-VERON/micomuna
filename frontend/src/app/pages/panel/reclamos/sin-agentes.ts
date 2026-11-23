import { Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';

// Lo que se ve cuando no hay agentes para elegir: o la lista no se pudo cargar (con "Reintentar") o está vacía.
// Solo al administrador de la comuna se le ofrece el enlace a Usuarios: el administrativo no tiene esa pantalla.
@Component({
  selector: 'app-sin-agentes',
  imports: [RouterLink],
  template: `
    @if (fallo()) {
      <div class="caja" role="alert">
        <p>No se pudo cargar la lista de agentes.</p>
        <button type="button" class="reintentar" (click)="reintentar.emit()">Reintentar</button>
      </div>
    } @else {
      <p class="vacia" role="status">
        No hay agentes disponibles. Para aparecer acá, el agente tiene que estar activo y haber aceptado la invitación.
        @if (puedeIrAUsuarios() && slug()) {
          &ngsp;Podés revisarlo en <a [routerLink]="['/', slug(), 'panel', 'usuarios']">Usuarios</a>.
        }
      </p>
    }
  `,
  styles: `
    :host { display: block; }
    p { margin: 0; overflow-wrap: anywhere; }
    .caja { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; padding: 12px 14px; border: 3px solid var(--error); border-radius: 14px; background: var(--error-fondo); }
    .reintentar { min-height: 48px; padding: 0 18px; border: 2px solid var(--p); border-radius: 14px; background: #fff; color: var(--p); font: inherit; font-weight: 700; cursor: pointer; touch-action: manipulation; }
    a { color: var(--p); font-weight: 700; }
  `,
})
export class SinAgentes {
  readonly fallo = input(false);
  readonly slug = input('');
  readonly reintentar = output<void>();

  private readonly auth = inject(AuthService);
  protected readonly puedeIrAUsuarios = computed(() => this.auth.usuario()?.rol === 'ADMIN_COMUNA');
}
