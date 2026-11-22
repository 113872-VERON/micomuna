import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { merge, startWith } from 'rxjs';
import { ComunasAdminService } from '../../../admin/comunas-admin.service';
import { leerLimite } from '../../../admin/limite';
import { validarLogo } from '../../../admin/logo';
import { sugerirSlug } from '../../../admin/slug';
import { AYUDA_TELEFONO, MENSAJE_TELEFONO, soloDigitos, validarTelefono } from '../../../admin/telefono';
import { mensajeDeError } from '../../../auth/errores';
import { limpiarCorreo, mensajeCorreo } from '../../../auth/mensajes-de-formulario';
import { ColorComuna, ComunaCreadaDto, ComunaDto, NuevaComunaRequest, PROVINCIAS } from '../../../auth/modelos';
import { CabeceraComuna } from '../../../compartido/cabecera-comuna/cabecera-comuna';
import { COLORES_COMUNA, NOMBRES_COLOR } from '../../../tema/tema-comuna.service';

@Component({
  selector: 'app-nueva-comuna',
  imports: [ReactiveFormsModule, RouterLink, CabeceraComuna],
  templateUrl: './nueva-comuna.html',
  styleUrl: './nueva-comuna.scss',
})
export class NuevaComuna {
  private readonly servicio = inject(ComunasAdminService);

  protected readonly provincias = PROVINCIAS;
  protected readonly mensajeTelefono = MENSAJE_TELEFONO;
  protected readonly ayudaTelefono = AYUDA_TELEFONO;
  protected readonly colores = (Object.keys(COLORES_COMUNA) as ColorComuna[]).map((clave) => ({
    clave,
    nombre: NOMBRES_COLOR[clave],
    hex: COLORES_COMUNA[clave],
  }));

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    provincia: ['', Validators.required],
    departamento: ['', Validators.maxLength(80)],
    slug: ['', Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/)],
    telefono: ['', validarTelefono],
    whatsapp: ['', validarTelefono],
    color: ['CELESTE'],
    adminNombre: ['', [Validators.required, Validators.maxLength(100)]],
    adminCorreo: ['', [Validators.required, Validators.email]],
  });

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly creada = signal<ComunaCreadaDto | null>(null);
  protected readonly invitacionEnviada = signal(false);
  protected readonly reenviando = signal(false);
  protected readonly errorReenvio = signal<string | null>(null);
  protected readonly enlaceCopiado = signal(false);

  // Logo opcional: se muestra en la vista previa y se sube recién cuando la comuna ya está creada.
  protected readonly logoArchivo = signal<File | null>(null);
  protected readonly logoUrl = signal<string | null>(null);
  protected readonly errorLogo = signal<string | null>(null);
  protected readonly logoFallido = signal(false);

  // Límite opcional (GeoJSON): se lee y revisa al elegirlo y se sube cuando la comuna ya está creada.
  protected readonly limiteArchivo = signal<{ nombre: string; geojson: object } | null>(null);
  protected readonly errorLimite = signal<string | null>(null);
  protected readonly limiteFallido = signal(false);
  protected readonly limiteMotivo = signal<string | null>(null);

  // Mientras nadie toque el enlace, se arma solo con el nombre y la provincia.
  private enlaceEditadoAMano = false;

  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  // Así se verá la cabecera de las pantallas de los vecinos con lo que se va escribiendo.
  protected readonly vistaPrevia = computed<ComunaDto>(() => {
    const v = this.valores();
    const provincia = PROVINCIAS.find((p) => p.clave === v.provincia)?.nombre ?? 'Provincia';
    return {
      nombre: v.nombre?.trim() || 'Nombre de la comuna',
      provincia,
      departamento: v.departamento?.trim() || null,
      slug: v.slug || 'enlace',
      tieneLogo: false,
      color: v.color ?? 'CELESTE',
    };
  });
  protected readonly colorVistaPrevia = computed(() => COLORES_COMUNA[this.valores().color as ColorComuna] ?? COLORES_COMUNA.CELESTE);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarLogo());
    const { nombre, provincia, slug } = this.form.controls;
    merge(nombre.valueChanges, provincia.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (!this.enlaceEditadoAMano) {
          slug.setValue(sugerirSlug(nombre.value, provincia.value), { emitEvent: false });
        }
      });
  }

  protected get c() {
    return this.form.controls;
  }

  protected enlaceTocado(): void {
    // Si vacía el campo, vuelve a sugerirse.
    this.enlaceEditadoAMano = this.c.slug.value !== '';
  }

  protected mensajeCorreo(): string {
    return mensajeCorreo(this.c.adminCorreo);
  }

  protected enlaceCompleto(): string {
    return `${location.origin}/${this.creada()?.comuna.slug}/login`;
  }

  protected elegirLogo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    // Se vacía siempre para poder volver a elegir el mismo archivo.
    input.value = '';
    if (!archivo || this.enviando()) {
      return;
    }
    const problema = validarLogo(archivo);
    this.errorLogo.set(problema);
    if (problema) {
      return;
    }
    this.liberarLogo();
    this.logoArchivo.set(archivo);
    this.logoUrl.set(URL.createObjectURL(archivo));
  }

  protected async elegirLimite(evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo || this.enviando()) {
      return;
    }
    const lectura = await leerLimite(archivo);
    if (lectura.error !== undefined) {
      this.errorLimite.set(lectura.error);
      return;
    }
    this.errorLimite.set(null);
    this.limiteArchivo.set({ nombre: archivo.name, geojson: lectura.geojson });
  }

  protected quitarLimite(): void {
    if (this.enviando()) {
      return;
    }
    this.errorLimite.set(null);
    this.limiteArchivo.set(null);
  }

  protected quitarLogo(): void {
    if (this.enviando()) {
      return;
    }
    this.errorLogo.set(null);
    this.liberarLogo();
  }

  private liberarLogo(): void {
    const url = this.logoUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.logoUrl.set(null);
    this.logoArchivo.set(null);
  }

  protected enviar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.servicio.crear(this.armarPedido()).subscribe({
      next: (resultado) => {
        this.subirComplementos(resultado);
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  // La comuna ya existe: primero se sube el logo (si hay) y después el límite (si hay). Si algo falla no se vuelve
  // a crear la comuna: se avisa cuál falló y se puede subir desde la lista.
  private subirComplementos(resultado: ComunaCreadaDto): void {
    const id = resultado.comuna.id;
    const logo = this.logoArchivo();
    const limite = this.limiteArchivo();
    const subirLimite = (logoFallo: boolean) => {
      if (!limite) {
        this.terminarAlta(resultado, logoFallo, null);
        return;
      }
      this.servicio.cambiarLimite(id, limite.geojson).subscribe({
        next: () => this.terminarAlta(resultado, logoFallo, null),
        error: (e: unknown) => this.terminarAlta(resultado, logoFallo, mensajeDeError(e)),
      });
    };
    if (!logo) {
      subirLimite(false);
      return;
    }
    this.servicio.subirLogo(id, logo).subscribe({
      next: () => subirLimite(false),
      error: () => subirLimite(true),
    });
  }

  private terminarAlta(resultado: ComunaCreadaDto, logoFallo: boolean, motivoLimite: string | null): void {
    this.enviando.set(false);
    this.logoFallido.set(logoFallo);
    this.limiteFallido.set(motivoLimite !== null);
    this.limiteMotivo.set(motivoLimite);
    this.creada.set(resultado);
    this.invitacionEnviada.set(resultado.invitacionEnviada);
  }

  protected reintentarInvitacion(): void {
    const creada = this.creada();
    if (!creada || this.reenviando()) {
      return;
    }
    this.reenviando.set(true);
    this.errorReenvio.set(null);
    this.servicio.reenviarInvitacion(creada.comuna.id).subscribe({
      next: () => {
        this.reenviando.set(false);
        this.invitacionEnviada.set(true);
      },
      error: (e: unknown) => {
        this.reenviando.set(false);
        this.errorReenvio.set(mensajeDeError(e));
      },
    });
  }

  protected async copiarEnlace(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.enlaceCompleto());
      this.enlaceCopiado.set(true);
    } catch {
      // Sin permiso para copiar: el enlace está a la vista para copiarlo a mano.
    }
  }

  // Los datos opcionales vacíos no se mandan.
  private armarPedido(): NuevaComunaRequest {
    const v = this.form.getRawValue();
    const pedido: NuevaComunaRequest = {
      nombre: v.nombre.trim(),
      provincia: v.provincia,
      color: v.color,
      adminNombre: v.adminNombre.trim(),
      adminCorreo: limpiarCorreo(v.adminCorreo),
    };
    for (const campo of ['slug', 'departamento'] as const) {
      const valor = v[campo].trim();
      if (valor) {
        pedido[campo] = valor;
      }
    }
    // Teléfono y WhatsApp viajan solo con los dígitos (el navegador ya revisó que sean de 8 a 15).
    for (const campo of ['telefono', 'whatsapp'] as const) {
      const valor = soloDigitos(v[campo].trim());
      if (valor) {
        pedido[campo] = valor;
      }
    }
    return pedido;
  }
}
