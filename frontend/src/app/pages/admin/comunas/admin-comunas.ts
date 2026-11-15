import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComunasAdminService } from '../../../admin/comunas-admin.service';
import { mensajeDeError } from '../../../auth/errores';
import { ColorComuna, ComunaAdminDto } from '../../../auth/modelos';
import { COLORES_COMUNA, NOMBRES_COLOR } from '../../../tema/tema-comuna.service';

@Component({
  selector: 'app-admin-comunas',
  imports: [RouterLink],
  templateUrl: './admin-comunas.html',
  styleUrl: './admin-comunas.scss',
})
export class AdminComunas implements OnInit {
  private readonly servicio = inject(ComunasAdminService);

  protected readonly comunas = signal<ComunaAdminDto[]>([]);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal(false);
  protected readonly errorAccion = signal<string | null>(null);
  // La comuna cuya desactivación se está confirmando (desactivar bloquea a todos sus usuarios).
  protected readonly confirmando = signal<number | null>(null);
  protected readonly cambiando = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.servicio.listar().subscribe({
      next: (lista) => {
        this.comunas.set(lista);
        this.cargando.set(false);
      },
      error: () => {
        this.errorCarga.set(true);
        this.cargando.set(false);
      },
    });
  }

  protected ubicacion(c: ComunaAdminDto): string {
    return c.departamento ? `${c.departamento}, ${c.provincia}` : c.provincia;
  }

  protected nombreColor(c: ComunaAdminDto): string {
    return NOMBRES_COLOR[c.color as ColorComuna] ?? c.color;
  }

  protected hexColor(c: ComunaAdminDto): string {
    return COLORES_COMUNA[c.color as ColorComuna] ?? COLORES_COMUNA.CELESTE;
  }

  protected pedirConfirmacion(id: number): void {
    this.errorAccion.set(null);
    this.confirmando.set(id);
  }

  protected cambiarEstado(comuna: ComunaAdminDto, activa: boolean): void {
    if (this.cambiando() !== null) {
      return;
    }
    this.confirmando.set(null);
    this.errorAccion.set(null);
    this.cambiando.set(comuna.id);
    this.servicio.cambiarEstado(comuna.id, activa).subscribe({
      next: (actualizada) => {
        this.comunas.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c)));
        this.cambiando.set(null);
      },
      error: (e: unknown) => {
        this.errorAccion.set(mensajeDeError(e));
        this.cambiando.set(null);
      },
    });
  }
}
