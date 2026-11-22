import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { AvisoPanelService } from '../../../admin/aviso-panel.service';
import { ComunasAdminService } from '../../../admin/comunas-admin.service';
import { AYUDA_TELEFONO, MENSAJE_TELEFONO, soloDigitos, validarTelefono } from '../../../admin/telefono';
import { mensajeDeError } from '../../../auth/errores';
import { ActualizarComunaRequest, ColorComuna, ComunaAdminDto, ComunaDto, PROVINCIAS } from '../../../auth/modelos';
import { CabeceraComuna } from '../../../compartido/cabecera-comuna/cabecera-comuna';
import { COLORES_COMUNA, NOMBRES_COLOR } from '../../../tema/tema-comuna.service';

// Modificar una comuna (solo el superadmin). El enlace y la provincia se ven pero no se cambian.
// Los datos iniciales salen de la lista: la API no tiene un GET por id.
@Component({
  selector: 'app-editar-comuna',
  imports: [ReactiveFormsModule, RouterLink, CabeceraComuna],
  templateUrl: './editar-comuna.html',
  // Mismo aspecto que "Nueva comuna".
  styleUrls: ['../nueva-comuna/nueva-comuna.scss'],
})
export class EditarComuna implements OnInit {
  private readonly servicio = inject(ComunasAdminService);
  private readonly router = inject(Router);
  private readonly avisos = inject(AvisoPanelService);

  // Viene de la ruta (/admin/comunas/:id/editar).
  readonly id = input.required<string>();

  protected readonly mensajeTelefono = MENSAJE_TELEFONO;
  protected readonly ayudaTelefono = AYUDA_TELEFONO;
  protected readonly colores = (Object.keys(COLORES_COMUNA) as ColorComuna[]).map((clave) => ({
    clave,
    nombre: NOMBRES_COLOR[clave],
    hex: COLORES_COMUNA[clave],
  }));

  protected readonly form = inject(NonNullableFormBuilder).group({
    nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    departamento: ['', Validators.maxLength(80)],
    telefono: ['', validarTelefono],
    whatsapp: ['', validarTelefono],
    color: ['CELESTE'],
  });

  protected readonly comuna = signal<ComunaAdminDto | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal(false);
  protected readonly noEncontrada = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  protected readonly provincia = computed(() => {
    const p = this.comuna()?.provincia ?? '';
    return PROVINCIAS.find((x) => x.clave === p)?.nombre ?? p;
  });

  protected readonly vistaPrevia = computed<ComunaDto>(() => {
    const v = this.valores();
    const c = this.comuna();
    return {
      nombre: v.nombre?.trim() || 'Nombre de la comuna',
      provincia: this.provincia(),
      departamento: v.departamento?.trim() || null,
      slug: c?.slug ?? 'enlace',
      tieneLogo: c?.tieneLogo ?? false,
      color: v.color ?? 'CELESTE',
    };
  });
  protected readonly colorVistaPrevia = computed(() => COLORES_COMUNA[this.valores().color as ColorComuna] ?? COLORES_COMUNA.CELESTE);

  ngOnInit(): void {
    this.cargar();
  }

  protected get c() {
    return this.form.controls;
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.noEncontrada.set(false);
    this.servicio.listar().subscribe({
      next: (lista) => {
        const comuna = lista.find((x) => String(x.id) === this.id());
        this.cargando.set(false);
        if (!comuna) {
          this.noEncontrada.set(true);
          return;
        }
        this.comuna.set(comuna);
        this.form.reset({
          nombre: comuna.nombre,
          departamento: comuna.departamento ?? '',
          telefono: comuna.telefono ?? '',
          whatsapp: comuna.whatsapp ?? '',
          color: comuna.color,
        });
      },
      error: () => {
        this.cargando.set(false);
        this.errorCarga.set(true);
      },
    });
  }

  // Solo viaja lo que cambió. Un teléfono, WhatsApp o departamento vaciado se manda como texto vacío: así se borra.
  private cambios(comuna: ComunaAdminDto): ActualizarComunaRequest {
    const v = this.form.getRawValue();
    const cambios: ActualizarComunaRequest = {};
    const nombre = v.nombre.trim();
    if (nombre !== comuna.nombre) {
      cambios.nombre = nombre;
    }
    const departamento = v.departamento.trim();
    if (departamento !== (comuna.departamento ?? '')) {
      cambios.departamento = departamento;
    }
    if (v.color !== comuna.color) {
      cambios.color = v.color;
    }
    const telefono = soloDigitos(v.telefono.trim());
    if (telefono !== soloDigitos(comuna.telefono ?? '')) {
      cambios.telefono = telefono;
    }
    const whatsapp = soloDigitos(v.whatsapp.trim());
    if (whatsapp !== soloDigitos(comuna.whatsapp ?? '')) {
      cambios.whatsapp = whatsapp;
    }
    return cambios;
  }

  protected enviar(): void {
    const comuna = this.comuna();
    this.form.markAllAsTouched();
    if (!comuna || this.form.invalid || this.enviando()) {
      return;
    }
    const cambios = this.cambios(comuna);
    if (Object.keys(cambios).length === 0) {
      this.error.set('No hay cambios para guardar. Si no querés modificar nada, tocá Cancelar.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.servicio.actualizar(comuna.id, cambios).subscribe({
      next: (actualizada) => {
        this.avisos.mostrar(`Guardamos los cambios de ${actualizada.nombre}`);
        void this.router.navigate(['/admin/comunas']);
      },
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
