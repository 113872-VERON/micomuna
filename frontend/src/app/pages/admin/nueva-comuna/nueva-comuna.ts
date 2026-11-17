import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { merge, startWith } from 'rxjs';
import { ComunasAdminService } from '../../../admin/comunas-admin.service';
import { validarLogo } from '../../../admin/logo';
import { sugerirSlug } from '../../../admin/slug';
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
    telefono: ['', Validators.pattern(/^[0-9 +\-()]{0,30}$/)],
    whatsapp: ['', Validators.pattern(/^(\d{10,15})?$/)],
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
        const logo = this.logoArchivo();
        if (!logo) {
          this.terminarAlta(resultado, false);
          return;
        }
        // La comuna ya existe: si el logo falla no se vuelve a crear, se sube después desde la lista.
        this.servicio.subirLogo(resultado.comuna.id, logo).subscribe({
          next: () => this.terminarAlta(resultado, false),
          error: () => this.terminarAlta(resultado, true),
        });
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  private terminarAlta(resultado: ComunaCreadaDto, logoFallo: boolean): void {
    this.enviando.set(false);
    this.logoFallido.set(logoFallo);
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
    for (const campo of ['slug', 'departamento', 'telefono', 'whatsapp'] as const) {
      const valor = v[campo].trim();
      if (valor) {
        pedido[campo] = valor;
      }
    }
    return pedido;
  }
}
