import { Component, computed, inject, input } from '@angular/core';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { nombreDeRol } from '../../auth/modelos';

// Botón de la barra con quién ingresó (avatar, nombre y rol). Abre un menú con "Mis datos", "Cambiar contraseña" y "Salir".
// Lo comparten las barras del panel, del agente, del vecino y del superadmin; cada una dice cuál es su zona de rutas.
@Component({
  selector: 'app-menu-usuario',
  imports: [MatMenu, MatMenuItem, MatMenuTrigger, RouterLink],
  template: `
    <button type="button" class="disparador" [matMenuTriggerFor]="menu">
      <span class="av" aria-hidden="true">{{ inicial() }}</span>
      <span class="quien"><b>{{ auth.usuario()?.nombre }}</b><small>{{ rol() }}</small></span>
      <span class="material-symbols-outlined flecha" aria-hidden="true">expand_more</span>
    </button>
    <mat-menu #menu="matMenu" xPosition="before">
      <a mat-menu-item [routerLink]="[...zona(), 'mi-cuenta']">
        <span class="material-symbols-outlined" aria-hidden="true">person</span>
        <span>Mis datos</span>
      </a>
      <a mat-menu-item [routerLink]="[...zona(), 'cambiar-clave']">
        <span class="material-symbols-outlined" aria-hidden="true">lock</span>
        <span>Cambiar contraseña</span>
      </a>
      <button type="button" mat-menu-item (click)="salir()">
        <span class="material-symbols-outlined" aria-hidden="true">logout</span>
        <span>Salir</span>
      </button>
    </mat-menu>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .disparador {
      max-width: 100%; min-height: 48px; box-sizing: border-box; display: flex; align-items: center; gap: 10px; padding: 0 10px 0 4px;
      border: 2px solid #fff; border-radius: 28px; background: transparent; color: #fff; font: inherit; text-align: left;
      cursor: pointer; touch-action: manipulation; user-select: none; -webkit-user-select: none;
    }
    .disparador:focus-visible { outline: 4px solid #fff; outline-offset: 3px; }
    @media (hover: hover) and (pointer: fine) { .disparador:hover { background: rgb(255 255 255 / 0.14); } }
    .disparador[aria-expanded='true'] { background: rgb(255 255 255 / 0.22); }
    .av { flex: none; width: 38px; height: 38px; border-radius: 50%; background: #fff; color: var(--p); font-weight: 700; font-size: 20px; display: flex; align-items: center; justify-content: center; }
    .quien { min-width: 0; display: flex; flex-direction: column; line-height: 1.2; }
    .quien b { font-size: 16px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
    .quien small { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .flecha { flex: none; font-size: 24px; }
    @media (max-width: 640px) {
      .quien b { max-width: 120px; }
      .quien small { display: none; }
    }
    @media (max-width: 360px) { .quien { display: none; } }
    .material-symbols-outlined { margin-right: 12px; }
  `,
})
export class MenuUsuario {
  // Las rutas de la zona donde está la barra, por ejemplo ['/', 'mi-comuna', 'panel']; ahí viven "mi-cuenta" y "cambiar-clave".
  readonly zona = input.required<readonly string[]>();
  // A dónde se va al salir.
  readonly alSalir = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly inicial = computed(() => (Array.from((this.auth.usuario()?.nombre ?? '').trim())[0] ?? '?').toUpperCase());
  protected readonly rol = computed(() => {
    const rol = this.auth.usuario()?.rol;
    return rol ? nombreDeRol(rol) : '';
  });

  protected salir(): void {
    this.auth.cerrarSesion();
    void this.router.navigateByUrl(this.alSalir());
  }
}
