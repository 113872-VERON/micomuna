import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EMPTY, expand, toArray } from 'rxjs';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { Dialogo } from '../../../compartido/dialogo/dialogo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { ChipPrioridad } from '../../../compartido/prioridad/chip-prioridad';
import { OrdenAgente, conDistancias, ordenar, textoDeDistancia } from '../../../agente/distancia';
import { GrupoAgente, ReclamoAgenteDto } from '../../../agente/modelos-agente';
import { ReclamosAgenteService } from '../../../agente/reclamos-agente.service';
import { Coordenadas } from '../../../reclamos/modelos';
import { SinUbicacion, UbicacionService } from '../../../reclamos/ubicacion.service';

const POR_PAGINA = 100;

export const OPCIONES_DE_ORDEN: readonly { valor: OrdenAgente; nombre: string; explicacion: string }[] = [
  { valor: 'fecha', nombre: 'Fecha', explicacion: 'Los más antiguos primero.' },
  { valor: 'tipo', nombre: 'Tipo', explicacion: 'Agrupados por tipo de problema, con un título por cada grupo.' },
  { valor: 'distancia', nombre: 'Distancia', explicacion: 'Los más cerca de donde estás primero, en línea recta.' },
  { valor: 'prioridad', nombre: 'Prioridad', explicacion: 'Los de prioridad alta primero; a igual prioridad, el más cerca si tenemos tu ubicación y si no, el más antiguo. Sin prioridad al final.' },
];

type EstadoUbicacion = 'pidiendo' | 'ok' | 'permiso' | 'no-disponible' | 'otro';

// "Mis reclamos" del agente (MC-20): los que tiene asignados, en "Para hacer" y "Resueltos". La distancia se calcula
// acá, en el teléfono, con el GPS: la ubicación del agente no se manda al servidor ni se guarda.
@Component({
  selector: 'app-mis-reclamos',
  imports: [DatePipe, RouterLink, ChipEstado, ChipPrioridad, IconoTipo, Dialogo],
  templateUrl: './mis-reclamos.html',
  styleUrl: './mis-reclamos.scss',
})
export class MisReclamos {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly api = inject(ReclamosAgenteService);
  private readonly ubicaciones = inject(UbicacionService);

  protected readonly opciones = OPCIONES_DE_ORDEN;
  protected readonly grupo = signal<GrupoAgente>('PARA_HACER');
  protected readonly orden = signal<OrdenAgente>('fecha');
  protected readonly hojaAbierta = signal(false);

  protected readonly carga = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly reclamos = signal<readonly ReclamoAgenteDto[]>([]);
  protected readonly total = signal(0);
  protected readonly cargandoMas = signal(false);
  protected readonly errorMas = signal(false);
  private readonly intento = signal(0);

  protected readonly ubicacion = signal<Coordenadas | null>(null);
  protected readonly estadoUbicacion = signal<EstadoUbicacion>('pidiendo');

  protected readonly paraHacer = computed(() => this.grupo() === 'PARA_HACER');
  // Sin ubicación no se puede ordenar por distancia: se usa la fecha.
  protected readonly ordenEfectivo = computed<OrdenAgente>(() => (this.orden() === 'distancia' && !this.ubicacion() ? 'fecha' : this.orden()));
  protected readonly nombreDelOrden = computed(() => this.opciones.find((o) => o.valor === this.ordenEfectivo())?.nombre ?? 'Fecha');
  protected readonly cayoAFecha = computed(() => this.orden() === 'distancia' && !this.ubicacion());
  protected readonly grupos = computed(() =>
    this.paraHacer()
      ? ordenar(conDistancias(this.reclamos(), this.ubicacion()), this.ordenEfectivo())
      : // Los resueltos vienen del servidor del más reciente al más viejo y no llevan distancia.
        [{ titulo: null, tarjetas: conDistancias(this.reclamos(), null) }],
  );
  // "Ver más" es solo de Resueltos: Para hacer ya viene completo.
  protected readonly hayMas = computed(() => !this.paraHacer() && this.reclamos().length < this.total());
  protected readonly sinUbicacion = computed(() => this.paraHacer() && this.estadoUbicacion() !== 'ok' && this.estadoUbicacion() !== 'pidiendo');
  protected readonly textoDeDistancia = textoDeDistancia;

  constructor() {
    this.actualizarUbicacion();
    effect((alLimpiar) => {
      const grupo = this.grupo();
      this.intento();
      untracked(() => {
        this.carga.set('cargando');
        this.reclamos.set([]);
        this.errorMas.set(false);
      });
      // "Para hacer" se carga entera (de a 100 hasta completar el total): así ordenar por distancia o por tipo es sobre
      // todos los reclamos del agente. "Resueltos" sigue paginado con "Ver más".
      const sub = this.api
        .listar(grupo, 0, POR_PAGINA)
        .pipe(
          expand((p, i) => (grupo === 'PARA_HACER' && p.reclamos.length > 0 && (i + 1) * POR_PAGINA < p.total ? this.api.listar(grupo, i + 1, POR_PAGINA) : EMPTY)),
          toArray(),
        )
        .subscribe({
          next: (paginas) => {
            this.reclamos.set(paginas.flatMap((p) => p.reclamos));
            this.total.set(paginas[paginas.length - 1]?.total ?? 0);
            this.carga.set('lista');
          },
          error: () => this.carga.set('error'),
        });
      alLimpiar(() => sub.unsubscribe());
    });
  }

  // Toma la ubicación una sola vez (al abrir, o cuando el agente lo pide). Solo se usa para calcular distancias acá.
  protected async actualizarUbicacion(): Promise<void> {
    this.estadoUbicacion.set('pidiendo');
    try {
      this.ubicacion.set(await this.ubicaciones.obtener());
      this.estadoUbicacion.set('ok');
    } catch (e) {
      this.ubicacion.set(null);
      this.estadoUbicacion.set(e instanceof SinUbicacion ? (e.motivo === 'permiso' ? 'permiso' : e.motivo === 'no-disponible' ? 'no-disponible' : 'otro') : 'otro');
    }
  }

  // "Actualizar": vuelve a pedir la lista y la ubicación.
  protected actualizar(): void {
    void this.actualizarUbicacion();
    this.intento.update((n) => n + 1);
  }

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  protected elegirGrupo(grupo: GrupoAgente): void {
    this.grupo.set(grupo);
  }

  // Flechas izquierda y derecha pasan de una pestaña a la otra (patrón de pestañas del teclado).
  protected teclaEnPestanas(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowRight' || evento.key === 'ArrowLeft') {
      evento.preventDefault();
      const nuevo: GrupoAgente = this.grupo() === 'PARA_HACER' ? 'RESUELTOS' : 'PARA_HACER';
      this.grupo.set(nuevo);
      queueMicrotask(() => document.getElementById(nuevo === 'PARA_HACER' ? 'pestana-para-hacer' : 'pestana-resueltos')?.focus());
    }
  }

  protected elegirOrden(orden: OrdenAgente): void {
    this.orden.set(orden);
    this.hojaAbierta.set(false);
  }

  protected verMas(): void {
    if (this.cargandoMas()) {
      return;
    }
    this.cargandoMas.set(true);
    this.errorMas.set(false);
    this.api.listar(this.grupo(), Math.floor(this.reclamos().length / POR_PAGINA), POR_PAGINA).subscribe({
      next: (p) => {
        this.reclamos.update((lista) => [...lista, ...p.reclamos]);
        this.total.set(p.total);
        this.cargandoMas.set(false);
      },
      error: () => {
        this.errorMas.set(true);
        this.cargandoMas.set(false);
      },
    });
  }

  protected distanciaDe(metros: number | null): string | null {
    return metros === null ? null : textoDeDistancia(metros) || null;
  }
}
