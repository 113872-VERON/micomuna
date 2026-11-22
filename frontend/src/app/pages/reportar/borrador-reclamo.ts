import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { startWith } from 'rxjs';
import { mensajeDeError } from '../../auth/errores';
import { ACHICAR_FOTO } from '../../reclamos/achicar-foto';
import { mensajeDelEnvio } from '../../reclamos/errores-reclamo';
import {
  CodigoTipo,
  Coordenadas,
  MAX_DESCRIPCION,
  MAX_FOTOS,
  MAX_REFERENCIA,
  ReclamoCreadoDto,
  TipoReclamoDto,
} from '../../reclamos/modelos';
import { ReclamosService } from '../../reclamos/reclamos.service';
import { SinUbicacion, UbicacionService } from '../../reclamos/ubicacion.service';
import { Paso, SEGMENTO_DE_PASO } from './pasos';

export type { Paso } from './pasos';

export interface FotoElegida {
  id: number;
  archivo: Blob;
  // URL de objeto para la vista previa: se libera al quitarla, al enviar y al salir de la pantalla.
  url: string;
}

// Unos espacios no cuentan como descripción.
function conTexto(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() ? null : { required: true };
}

// Lo que el vecino va cargando en el recorrido. Vive mientras está abierta la pantalla de reportar
// (se provee en ese componente, que se reutiliza al pasar de un paso a otro): si vuelve un paso
// no pierde nada, y al salir se descarta.
// El paso que se ve sale de la dirección: `ir()` navega y la pantalla actualiza `paso`.
@Injectable()
export class BorradorReclamo {
  private readonly servicio = inject(ReclamosService);
  private readonly achicar = inject(ACHICAR_FOTO);
  private readonly gps = inject(UbicacionService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly paso = signal<Paso>(1);
  // El paso más lejano al que llegó con "Siguiente": "adelante" del navegador no pasa de ahí.
  private readonly alcanzado = signal<1 | 2 | 3 | 4>(1);

  readonly tipos = signal<TipoReclamoDto[]>([]);
  readonly cargandoTipos = signal(false);
  readonly errorTipos = signal<string | null>(null);

  readonly form = inject(NonNullableFormBuilder).group({
    tipo: ['' as CodigoTipo | '', Validators.required],
    descripcion: ['', [conTexto, Validators.maxLength(MAX_DESCRIPCION)]],
    referencia: ['', Validators.maxLength(MAX_REFERENCIA)],
  });
  private readonly valores = toSignal(this.form.valueChanges.pipe(startWith(this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  readonly tipoElegido = computed(() => this.tipos().find((t) => t.codigo === this.valores().tipo) ?? null);
  readonly largoDescripcion = computed(() => this.valores().descripcion?.length ?? 0);
  readonly largoReferencia = computed(() => this.valores().referencia?.length ?? 0);
  readonly referencia = computed(() => this.valores().referencia?.trim() ?? '');

  readonly fotos = signal<FotoElegida[]>([]);
  // Cuántas se están achicando ahora: también ocupan lugar entre las 3.
  readonly preparando = signal(0);
  readonly fotoIlegible = signal(false);
  readonly avisoFotos = signal<string | null>(null);
  readonly lugaresLibres = computed(() => MAX_FOTOS - this.fotos().length - this.preparando());

  readonly ubicacion = signal<Coordenadas | null>(null);
  readonly buscandoUbicacion = signal(false);
  readonly errorUbicacion = signal<string | null>(null);
  readonly tieneLugar = computed(() => this.ubicacion() !== null || this.referencia() !== '');

  readonly enviando = signal(false);
  readonly errorEnvio = signal<string | null>(null);
  readonly creado = signal<ReclamoCreadoDto | null>(null);

  // Hay algo que se perdería al salir: se pide confirmación.
  readonly tieneDatos = computed(() => {
    const v = this.valores();
    return (
      !!v.tipo ||
      !!v.descripcion?.trim() ||
      !!v.referencia?.trim() ||
      this.fotos().length > 0 ||
      this.preparando() > 0 ||
      this.ubicacion() !== null ||
      this.enviando()
    );
  });

  private siguienteId = 1;
  private cerrado = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.cerrado = true;
      this.liberarFotos();
    });
  }

  // Navega a la dirección del paso. Avanzar con "Siguiente" o "Cambiar" agrega una entrada al
  // historial (así "atrás" vuelve); las correcciones automáticas la reemplazan.
  ir(paso: Paso, { reemplazar = false } = {}): void {
    if (paso !== 'listo' && paso > this.alcanzado()) {
      this.alcanzado.set(paso);
    }
    const slug = this.ruta.snapshot.paramMap.get('slug') ?? '';
    void this.router.navigate(['/', slug, 'reportar', SEGMENTO_DE_PASO[paso]], { replaceUrl: reemplazar });
  }

  // El paso más avanzado que se puede abrir sin saltear nada, hasta el pedido.
  // Un paso se abre si ya se llegó a él y los anteriores están completos (las fotos son opcionales).
  pasoPermitido(pedido: Paso): Paso {
    if (pedido === 'listo') {
      return this.creado() ? 'listo' : this.pasoPermitido(4);
    }
    const primeroCompleto = this.form.controls.tipo.valid && this.form.controls.descripcion.valid;
    const maximo = Math.min(pedido, this.alcanzado());
    if (maximo >= 4 && primeroCompleto && this.tieneLugar()) {
      return 4;
    }
    if (maximo >= 2 && primeroCompleto) {
      return maximo >= 3 ? 3 : 2;
    }
    return 1;
  }

  cargarTipos(): void {
    this.cargandoTipos.set(true);
    this.errorTipos.set(null);
    this.servicio.listarTipos().subscribe({
      next: (tipos) => {
        this.tipos.set(tipos);
        this.cargandoTipos.set(false);
      },
      error: (e: unknown) => {
        this.errorTipos.set(mensajeDeError(e));
        this.cargandoTipos.set(false);
      },
    });
  }

  // Se achican de a una para no llenar la memoria de un celular viejo.
  async agregarFotos(archivos: readonly File[]): Promise<void> {
    const lugares = this.lugaresLibres();
    if (archivos.length === 0 || lugares <= 0) {
      return;
    }
    const tomadas = archivos.slice(0, lugares);
    this.fotoIlegible.set(false);
    this.avisoFotos.set(
      archivos.length > lugares
        ? `Elegiste ${archivos.length} fotos y entran ${lugares === 1 ? '1 más' : `${lugares} más`}: agregamos las primeras.`
        : null,
    );
    this.preparando.update((n) => n + tomadas.length);
    for (const archivo of tomadas) {
      try {
        const achicada = await this.achicar(archivo);
        if (this.cerrado) {
          return;
        }
        this.fotos.update((lista) => [...lista, { id: this.siguienteId++, archivo: achicada, url: URL.createObjectURL(achicada) }]);
      } catch {
        this.fotoIlegible.set(true);
      } finally {
        this.preparando.update((n) => n - 1);
      }
    }
  }

  quitarFoto(id: number): void {
    const foto = this.fotos().find((f) => f.id === id);
    if (!foto) {
      return;
    }
    URL.revokeObjectURL(foto.url);
    this.fotos.update((lista) => lista.filter((f) => f.id !== id));
    this.fotoIlegible.set(false);
    this.avisoFotos.set(null);
  }

  async usarMiUbicacion(): Promise<void> {
    if (this.buscandoUbicacion()) {
      return;
    }
    this.buscandoUbicacion.set(true);
    this.errorUbicacion.set(null);
    try {
      const coordenadas = await this.gps.obtener();
      this.ubicacion.set(coordenadas);
    } catch (e) {
      this.errorUbicacion.set(
        e instanceof SinUbicacion && e.motivo === 'permiso'
          ? 'No pudimos ver dónde estás porque el teléfono no dio permiso. Escribí la dirección acá abajo, o permití la ubicación y probá de nuevo.'
          : 'No pudimos ver dónde estás. Escribí la dirección acá abajo o probá de nuevo.',
      );
    } finally {
      this.buscandoUbicacion.set(false);
    }
  }

  quitarUbicacion(): void {
    this.ubicacion.set(null);
  }

  enviar(): void {
    const { tipo, descripcion, referencia } = this.form.getRawValue();
    if (this.enviando() || !tipo) {
      return;
    }
    this.enviando.set(true);
    this.errorEnvio.set(null);
    this.servicio
      .crear({ tipo, descripcion, referencia, ubicacion: this.ubicacion(), fotos: this.fotos().map((f) => f.archivo) })
      .subscribe({
        next: (creado) => {
          this.enviando.set(false);
          this.creado.set(creado);
          this.liberarFotos();
          // Reemplaza a "revisar": "atrás" desde la confirmación no vuelve a abrir el reclamo enviado.
          this.ir('listo', { reemplazar: true });
        },
        // Lo cargado queda como estaba: se corrige lo que haga falta y se vuelve a enviar.
        error: (e: unknown) => {
          this.enviando.set(false);
          this.errorEnvio.set(mensajeDelEnvio(e));
        },
      });
  }

  // "Reportar otro problema": todo de cero, pero los tipos ya cargados se conservan.
  reiniciar(): void {
    this.liberarFotos();
    this.form.reset();
    this.fotoIlegible.set(false);
    this.avisoFotos.set(null);
    this.ubicacion.set(null);
    this.errorUbicacion.set(null);
    this.errorEnvio.set(null);
    this.creado.set(null);
    this.alcanzado.set(1);
    this.ir(1, { reemplazar: true });
  }

  private liberarFotos(): void {
    for (const foto of this.fotos()) {
      URL.revokeObjectURL(foto.url);
    }
    this.fotos.set([]);
  }
}
