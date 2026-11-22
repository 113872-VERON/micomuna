import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ComunasAdminService } from '../../../admin/comunas-admin.service';
import { leerLimite } from '../../../admin/limite';
import { validarLogo } from '../../../admin/logo';
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

  protected readonly subiendoLogo = signal<number | null>(null);
  // Marca de tiempo por comuna para esquivar la caché del navegador después de subir un logo nuevo.
  private readonly versionesLogo = signal<Record<number, number>>({});
  private readonly logosRotos = signal<ReadonlySet<number>>(new Set());

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

  protected urlLogo(c: ComunaAdminDto): string {
    const base = `/api/comunas/${encodeURIComponent(c.slug)}/logo`;
    const version = this.versionesLogo()[c.id];
    return version ? `${base}?v=${version}` : base;
  }

  // El logo se pide al endpoint público, que responde 404 si la comuna está inactiva:
  // en ese caso ni se intenta cargar la miniatura.
  protected mostrarMiniatura(c: ComunaAdminDto): boolean {
    return c.tieneLogo && c.activa && !this.logosRotos().has(c.id);
  }

  protected logoOcultoPorInactiva(c: ComunaAdminDto): boolean {
    return c.tieneLogo && !c.activa;
  }

  protected logoRoto(id: number): void {
    this.logosRotos.update((s) => new Set(s).add(id));
  }

  // Después de activar, desactivar o subir un logo, la miniatura merece otro intento.
  private olvidarLogoRoto(id: number): void {
    this.logosRotos.update((s) => {
      if (!s.has(id)) {
        return s;
      }
      const nuevo = new Set(s);
      nuevo.delete(id);
      return nuevo;
    });
  }

  protected elegirLogo(comuna: ComunaAdminDto, evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    // Se vacía siempre para poder volver a elegir el mismo archivo.
    input.value = '';
    if (!archivo || this.subiendoLogo() !== null) {
      return;
    }
    this.errorAccion.set(null);
    const problema = validarLogo(archivo);
    if (problema) {
      this.errorAccion.set(problema);
      return;
    }
    this.subiendoLogo.set(comuna.id);
    this.servicio.subirLogo(comuna.id, archivo).subscribe({
      next: (actualizada) => {
        this.comunas.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c)));
        this.versionesLogo.update((v) => ({ ...v, [actualizada.id]: Date.now() }));
        this.olvidarLogoRoto(actualizada.id);
        this.subiendoLogo.set(null);
      },
      error: (e: unknown) => {
        this.errorAccion.set(mensajeDeError(e));
        this.subiendoLogo.set(null);
      },
    });
  }

  protected readonly subiendoLimite = signal<number | null>(null);

  protected fechaLimite(c: ComunaAdminDto): string {
    const fecha = c.limiteCargadoEn ? new Date(c.limiteCargadoEn) : null;
    return fecha && !Number.isNaN(fecha.getTime())
      ? new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long', year: 'numeric' }).format(fecha)
      : '';
  }

  protected async elegirLimite(comuna: ComunaAdminDto, evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo || this.subiendoLimite() !== null) {
      return;
    }
    this.errorAccion.set(null);
    this.subiendoLimite.set(comuna.id);
    const lectura = await leerLimite(archivo);
    if (lectura.error !== undefined) {
      this.errorAccion.set(lectura.error);
      this.subiendoLimite.set(null);
      return;
    }
    this.servicio.cambiarLimite(comuna.id, lectura.geojson).subscribe({
      next: (actualizada) => {
        this.comunas.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c)));
        this.subiendoLimite.set(null);
      },
      error: (e: unknown) => {
        this.errorAccion.set(mensajeDeError(e));
        this.subiendoLimite.set(null);
      },
    });
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
        this.olvidarLogoRoto(actualizada.id);
        this.cambiando.set(null);
      },
      error: (e: unknown) => {
        this.errorAccion.set(mensajeDeError(e));
        this.cambiando.set(null);
      },
    });
  }
}
