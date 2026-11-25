import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, afterRenderEffect, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AyudaFlotante } from '../../../compartido/ayuda-flotante/ayuda-flotante';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { ComunaDto } from '../../../auth/modelos';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import {
  EstadoDePantalla,
  FiltrosPanel,
  MAX_BUSCAR,
  SIN_FILTROS,
  VistaPanel,
  aQueryParams,
  buscarValido,
  hayFiltros,
  leerEstado,
} from '../../../reclamos/filtros-panel';
import { EstadoReclamo, TipoReclamoDto } from '../../../reclamos/modelos';
import { diasDesde } from '../../../reclamos/estilo-estados';
import {
  AgenteConCargaDto,
  PaginaReclamosPanelDto,
  PuntoReclamoDto,
  ReclamoElegido,
  ReclamoPanelDto,
  ResumenPanelDto,
  sePuedeDescartar,
} from '../../../reclamos/modelos-panel';
import { ReclamosService } from '../../../reclamos/reclamos.service';
import { NOMBRE_ESTADO } from '../../../reclamos/seguimiento';
import { MapaReclamos } from './mapa-reclamos';
import { DialogoCambiarEstado } from './dialogo-cambiar-estado';
import { DialogoDescartar, textoDeAplicables } from './dialogo-descartar';

const POR_PAGINA = 25;
const ESPERA_BUSQUEDA_MS = 400;
const ESTADOS: readonly EstadoReclamo[] = ['RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO'];

// Alto mínimo del mapa en escritorio, margen que queda debajo y ancho desde el que hay dos columnas.
export const ALTO_MINIMO_MAPA = 480;
export const MARGEN_INFERIOR_MAPA = 16;
const ESCRITORIO = '(min-width: 1001px)';

// Alto para que el mapa entre justo debajo de lo que tiene arriba: lo que queda de ventana, sin bajar del mínimo.
export function altoDelMapa(altoVentana: number, arriba: number, minimo = ALTO_MINIMO_MAPA, margen = MARGEN_INFERIOR_MAPA): number {
  return Math.max(minimo, Math.round(altoVentana - arriba - margen));
}

const mismos = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

// "El más antiguo es de hace 5 días."
export function textoDelAviso(resumen: ResumenPanelDto, ahora = new Date()): string {
  const n = resumen.sinAsignar;
  const principal = n === 1 ? '1 reclamo espera que lo asignes.' : `${n} reclamos esperan que los asignes.`;
  if (!resumen.sinAsignarMasAntiguo) {
    return principal;
  }
  const dias = diasDesde(resumen.sinAsignarMasAntiguo, ahora);
  const cuando = dias === 0 ? 'de hoy' : dias === 1 ? 'de hace 1 día' : `de hace ${dias} días`;
  return `${principal} El más antiguo es ${cuando}.`;
}

// Panel de reclamos del personal (MC-18): dos vistas, "Lista y mapa" y "Solo lista", con los filtros en la URL.
@Component({
  selector: 'app-reclamos-panel',
  imports: [DatePipe, RouterLink, ChipEstado, IconoTipo, AyudaFlotante, MapaReclamos, DialogoCambiarEstado, DialogoDescartar],
  templateUrl: './reclamos-panel.html',
  styleUrl: './reclamos-panel.scss',
  host: { '(window:resize)': 'medirMapa()' },
})
export class ReclamosPanel {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly api = inject(ReclamosPanelService);
  private readonly reclamosApi = inject(ReclamosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly estados = ESTADOS;
  protected readonly nombres = NOMBRE_ESTADO;
  protected readonly maxBuscar = MAX_BUSCAR;
  protected readonly porPagina = POR_PAGINA;

  // ---- La URL manda: filtros, vista y página ----
  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  protected readonly pantalla = computed<EstadoDePantalla>(() => leerEstado(this.params()), { equal: mismos });
  protected readonly filtros = computed<FiltrosPanel>(() => this.pantalla().filtros, { equal: mismos });
  protected readonly vista = computed<VistaPanel>(() => this.pantalla().vista);
  protected readonly pagina = computed(() => this.pantalla().pagina);
  // El resumen no lleva el filtro de estado (así el desplegable muestra cuántos hay en cada uno).
  private readonly filtrosDelResumen = computed(() => ({ ...this.filtros(), estado: null }), { equal: mismos });
  protected readonly conFiltros = computed(() => hayFiltros(this.filtros()));

  // ---- Datos ----
  protected readonly resumen = signal<ResumenPanelDto | null>(null);
  protected readonly datos = signal<PaginaReclamosPanelDto | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorLista = signal(false);
  protected readonly puntos = signal<readonly PuntoReclamoDto[]>([]);
  protected readonly recortado = signal(false);
  protected readonly errorMapa = signal(false);
  protected readonly sinMapa = signal(false);
  protected readonly limite = signal<unknown>(null);
  protected readonly agentes = signal<readonly AgenteConCargaDto[]>([]);
  protected readonly errorAgentes = signal(false);
  protected readonly tipos = signal<readonly TipoReclamoDto[]>([]);
  private readonly recarga = signal(0);

  // ---- Estado de la pantalla ----
  protected readonly busqueda = signal('');
  protected readonly avisoCerrado = signal(false);
  protected readonly seleccion = signal<ReadonlySet<number>>(new Set());
  protected readonly filaActiva = signal<number | null>(null);
  protected readonly dialogo = signal<'estado' | 'descarte' | null>(null);
  protected readonly elegidosEnDialogo = signal<readonly ReclamoElegido[]>([]);

  protected readonly reclamos = computed(() => this.datos()?.reclamos ?? []);
  protected readonly totalPaginas = computed(() => Math.max(1, Math.ceil((this.datos()?.total ?? 0) / POR_PAGINA)));
  protected readonly aviso = computed(() => {
    const r = this.resumen();
    return r && r.sinAsignar > 0 && !this.avisoCerrado() ? textoDelAviso(r) : null;
  });
  protected readonly busquedaCorta = computed(() => {
    const t = this.busqueda().trim();
    return t.length === 1 && !/^\d$/.test(t) ? 'Escribí al menos 2 letras, o un número de reclamo.' : null;
  });
  protected readonly seleccionados = computed<ReclamoElegido[]>(() =>
    this.reclamos().filter((r) => this.seleccion().has(r.numero)).map((r) => ({ numero: r.numero, estado: r.estado })),
  );
  protected readonly avisoDeAplicables = computed(() => textoDeAplicables(this.seleccionados()));
  protected readonly todosElegidos = computed(() => this.reclamos().length > 0 && this.seleccionados().length === this.reclamos().length);
  protected readonly algunosElegidos = computed(() => this.seleccionados().length > 0 && !this.todosElegidos());
  protected readonly mostrarMapa = computed(() => this.vista() === 'mapa' && !this.sinMapa());

  // Alto del mapa en escritorio (ej. "612px"); null en angosto, donde manda el valor del CSS.
  protected readonly altoMapa = signal<string | null>(null);
  private readonly cuerpo = viewChild<ElementRef<HTMLElement>>('cuerpo');
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  private temporizador: ReturnType<typeof setTimeout> | undefined;
  private ultimoEnviado = '';

  constructor() {
    this.cargarApoyos();

    // Lo que se escribe en el buscador vuelve a la caja solo si el cambio vino de afuera (por ejemplo "atrás").
    effect(() => {
      const enUrl = this.filtros().buscar;
      untracked(() => {
        if (enUrl !== this.ultimoEnviado) {
          this.ultimoEnviado = enUrl;
          this.busqueda.set(enUrl);
        }
      });
    });

    effect((alLimpiar) => {
      const filtros = this.filtrosDelResumen();
      this.recarga();
      const sub = this.api.resumen(filtros).subscribe({ next: (r) => this.resumen.set(r), error: () => this.resumen.set(null) });
      alLimpiar(() => sub.unsubscribe());
    });

    effect((alLimpiar) => {
      const filtros = this.filtros();
      const pagina = this.pagina();
      this.recarga();
      untracked(() => {
        this.cargando.set(true);
        this.errorLista.set(false);
        this.seleccion.set(new Set());
      });
      const sub = this.api.listar(filtros, pagina, POR_PAGINA).subscribe({
        next: (d) => {
          // Una página fuera de rango (una URL vieja, o menos resultados al filtrar) lleva a la última con reclamos.
          if (d.reclamos.length === 0 && pagina > 0) {
            this.navegar({ pagina: Math.max(0, Math.ceil(d.total / POR_PAGINA) - 1) }, true);
            return;
          }
          this.datos.set(d);
          this.cargando.set(false);
        },
        error: () => {
          this.errorLista.set(true);
          this.cargando.set(false);
        },
      });
      alLimpiar(() => sub.unsubscribe());
    });

    effect((alLimpiar) => {
      const filtros = this.filtros();
      this.recarga();
      if (this.vista() !== 'mapa') {
        return;
      }
      untracked(() => this.errorMapa.set(false));
      const sub = this.api.mapa(filtros).subscribe({
        next: (m) => {
          this.puntos.set(m.puntos);
          this.recortado.set(m.recortado);
        },
        error: () => {
          this.puntos.set([]);
          this.recortado.set(false);
          this.errorMapa.set(true);
        },
      });
      alLimpiar(() => sub.unsubscribe());
    });

    // Se mide cada vez que aparece el mapa, y cuando cambia lo de arriba (aviso, filtros, barra) o la ventana.
    afterRenderEffect(() => {
      this.mostrarMapa();
      this.medirMapa();
    });
    let observador: ResizeObserver | undefined;
    afterNextRender(() => {
      if (typeof ResizeObserver !== 'undefined') {
        observador = new ResizeObserver(() => this.medirMapa());
        observador.observe(this.anfitrion.nativeElement);
      }
    });

    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.temporizador);
      observador?.disconnect();
    });
  }

  // Alto visible de la ventana menos lo que hay arriba del mapa (posición en la página, no en la ventana).
  protected medirMapa(): void {
    const cuerpo = this.cuerpo()?.nativeElement;
    const escritorio = typeof matchMedia === 'function' && matchMedia(ESCRITORIO).matches;
    if (!cuerpo || !this.mostrarMapa() || !escritorio) {
      this.altoMapa.set(null);
      return;
    }
    const arriba = cuerpo.getBoundingClientRect().top + window.scrollY;
    this.altoMapa.set(`${altoDelMapa(window.innerHeight, arriba)}px`);
  }

  protected cargarAgentes(): void {
    this.api.agentes().subscribe({
      next: (a) => {
        this.agentes.set(a);
        this.errorAgentes.set(false);
      },
      error: () => this.errorAgentes.set(this.agentes().length === 0),
    });
  }

  // Agentes (para el filtro y para asignar), tipos y límite de la comuna: se piden una vez. Si alguno falla, la
  // pantalla sigue sin ese dato.
  private cargarApoyos(): void {
    this.cargarAgentes();
    this.reclamosApi.listarTipos().subscribe({ next: (t) => this.tipos.set(t), error: () => this.tipos.set([]) });
    this.reclamosApi.limiteDeMiComuna().subscribe({ next: (l) => this.limite.set(l ?? null), error: () => this.limite.set(null) });
  }

  // ---- Navegar: todo cambio de filtro pasa por la URL ----
  private navegar(cambios: Partial<EstadoDePantalla>, reemplazar = false): void {
    const nuevo: EstadoDePantalla = { ...this.pantalla(), ...cambios };
    void this.router.navigate([], { relativeTo: this.route, queryParams: aQueryParams(nuevo), queryParamsHandling: 'merge', replaceUrl: reemplazar });
  }

  protected cambiarFiltro(clave: 'estado' | 'tipo' | 'agente', evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    const filtros = { ...this.filtros(), [clave]: valor ? (clave === 'agente' ? Number(valor) : valor) : null } as FiltrosPanel;
    this.navegar({ filtros, pagina: 0 });
  }

  protected cambiarVista(vista: VistaPanel): void {
    if (vista !== this.vista()) {
      this.navegar({ vista });
    }
  }

  protected escribirBusqueda(evento: Event): void {
    this.busqueda.set((evento.target as HTMLInputElement).value);
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.buscar(true), ESPERA_BUSQUEDA_MS);
  }

  protected buscar(reemplazar = false): void {
    clearTimeout(this.temporizador);
    const texto = this.busqueda().trim();
    // Un texto que el servidor no aceptaría (una sola letra) no se manda; vacío limpia la búsqueda.
    if (texto && !buscarValido(texto)) {
      return;
    }
    if (texto === this.filtros().buscar.trim()) {
      return;
    }
    this.ultimoEnviado = texto;
    this.navegar({ filtros: { ...this.filtros(), buscar: texto }, pagina: 0 }, reemplazar);
  }

  protected limpiarFiltros(): void {
    clearTimeout(this.temporizador);
    this.ultimoEnviado = '';
    this.busqueda.set('');
    this.navegar({ filtros: SIN_FILTROS, pagina: 0 });
  }

  protected irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas()) {
      this.navegar({ pagina });
    }
  }

  // ---- Selección (solo en "Solo lista") ----
  protected estaElegido(numero: number): boolean {
    return this.seleccion().has(numero);
  }

  protected alternar(numero: number): void {
    const nueva = new Set(this.seleccion());
    if (!nueva.delete(numero)) {
      nueva.add(numero);
    }
    this.seleccion.set(nueva);
  }

  protected alternarTodos(): void {
    this.seleccion.set(this.todosElegidos() ? new Set() : new Set(this.reclamos().map((r) => r.numero)));
  }

  protected quitarSeleccion(): void {
    this.seleccion.set(new Set());
  }

  // ---- Ventanas ----
  // Por qué no se puede descartar (para la ayuda del botón), o null si se puede.
  protected motivoSinDescarte(r: ReclamoPanelDto): string | null {
    if (sePuedeDescartar(r.estado)) {
      return null;
    }
    return r.estado === 'EN_REPARACION' ? 'No se puede descartar: el reclamo está en reparación' : 'No se puede descartar: el reclamo ya está cerrado';
  }

  protected abrirEstado(reclamos: readonly ReclamoElegido[]): void {
    this.elegidosEnDialogo.set(reclamos);
    this.dialogo.set('estado');
  }

  protected abrirDescarte(reclamos: readonly ReclamoElegido[]): void {
    this.elegidosEnDialogo.set(reclamos);
    this.dialogo.set('descarte');
  }

  protected elegido(r: ReclamoPanelDto): ReclamoElegido {
    return { numero: r.numero, estado: r.estado };
  }

  protected descartarFila(r: ReclamoPanelDto): void {
    if (sePuedeDescartar(r.estado)) {
      this.abrirDescarte([this.elegido(r)]);
    }
  }

  protected cerrarDialogo(): void {
    this.dialogo.set(null);
  }

  // Reintentar la lista, el resumen y el mapa (no los agentes).
  protected reintentar(): void {
    this.recarga.update((n) => n + 1);
  }

  // Algo cambió en el servidor: se vuelve a pedir el resumen, la lista y el mapa.
  protected alAplicar(): void {
    this.recarga.update((n) => n + 1);
    this.cargarAgentes();
  }

  // ---- Mapa ----
  protected alElegirPin(numero: number): void {
    if (this.reclamos().some((r) => r.numero === numero)) {
      this.filaActiva.set(numero);
      document.getElementById(`fila-${numero}`)?.scrollIntoView?.({ block: 'nearest' });
    } else {
      void this.router.navigate(['/', this.slug(), 'panel', 'reclamos', numero]);
    }
  }

  protected cantidadDeEstado(estado: EstadoReclamo): number {
    return this.resumen()?.porEstado?.[estado] ?? 0;
  }

  protected textoDuplicado(r: ReclamoPanelDto): string | null {
    if (r.numeroOriginal) {
      return `Duplicado de n.º ${r.numeroOriginal}`;
    }
    if (r.cantidadDuplicados > 0) {
      return r.cantidadDuplicados === 1 ? '1 duplicado vinculado' : `${r.cantidadDuplicados} duplicados vinculados`;
    }
    return null;
  }
}
