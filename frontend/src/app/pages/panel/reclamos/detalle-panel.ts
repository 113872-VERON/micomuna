import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { afterNextRender, Component, computed, effect, ElementRef, inject, Injector, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeDeError } from '../../../auth/errores';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
import { TipoReclamoDto } from '../../../reclamos/modelos';
import { ReclamosService } from '../../../reclamos/reclamos.service';
import { AgenteConCargaDto, CambioDeEstadoPanelDto, ReclamoElegido, ReclamoPanelDetalleDto, sePuedeDescartar, textoDeAgenteConCarga } from '../../../reclamos/modelos-panel';
import { textoDeMotivo } from '../../../reclamos/seguimiento';
import { DialogoCambiarEstado } from './dialogo-cambiar-estado';
import { DialogoDescartar } from './dialogo-descartar';
import { SinAgentes } from './sin-agentes';

function mensajeDeAsignar(e: unknown, codigo: string | undefined): string {
  switch (codigo) {
    case 'YA_ASIGNADO':
      return 'Ese reclamo ya está asignado a ese agente. Elegí otro si querés reasignarlo.';
    case 'RECLAMO_CERRADO':
      return 'El reclamo ya está cerrado: no se puede asignar.';
    case 'CONFLICTO':
      return 'Otra persona está cambiando este reclamo en este momento. Esperá un instante y probá de nuevo.';
    case 'AGENTE_INVALIDO':
      return 'Ese agente ya no está disponible. Elegí otro de la lista.';
    default:
      return mensajeDeError(e);
  }
}

function mensajeDeTipo(e: unknown, codigo: string | undefined): string {
  switch (codigo) {
    case 'RECLAMO_CERRADO':
      return 'El reclamo ya está cerrado: no se puede cambiar el tipo.';
    case 'MISMO_TIPO':
      return 'El reclamo ya tiene ese tipo. Elegí otro si querés cambiarlo.';
    case 'TIPO_INVALIDO':
      return 'Ese tipo ya no está disponible. Elegí otro de la lista.';
    case 'CONFLICTO':
      return 'Otra persona está cambiando este reclamo. Probá de nuevo.';
    default:
      return mensajeDeError(e);
  }
}

function mensajeDeNoEsDuplicado(e: unknown, codigo: string | undefined): string {
  switch (codigo) {
    case 'RECLAMO_CERRADO':
      return 'El reclamo ya está cerrado: no hace falta revisar duplicados.';
    case 'YA_MARCADO':
      return 'Este reclamo ya se había dejado como nuevo.';
    case 'SIN_CANDIDATOS':
      return 'Ya no hay reclamos parecidos cerca: alguno se resolvió o se descartó.';
    case 'CONFLICTO':
      return 'Otra persona está cambiando este reclamo en este momento. Esperá un instante y probá de nuevo.';
    default:
      return mensajeDeError(e);
  }
}

// Detalle de un reclamo para el personal (MC-18): lo que reportó el vecino, la foto, el mapa, el historial
// completo con quién hizo cada cambio, el descarte, lo que se hizo y los duplicados vinculados. Todo texto de
// usuarios se muestra como texto plano. Desde acá también se asigna o reasigna el agente (MC-19), con la carga de cada uno.
@Component({
  selector: 'app-detalle-panel',
  imports: [DatePipe, RouterLink, ChipEstado, FotoReclamo, IconoTipo, MapaUbicacion, DialogoCambiarEstado, DialogoDescartar, SinAgentes],
  providers: [FotosDeReclamos],
  templateUrl: './detalle-panel.html',
  styleUrl: './detalle-panel.scss',
})
export class DetallePanel {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  // Viene de la dirección (/:slug/panel/reclamos/:numero), como texto.
  readonly numero = input.required<string>();

  private readonly api = inject(ReclamosPanelService);
  private readonly reclamosApi = inject(ReclamosService);

  protected readonly carga = signal<'cargando' | 'lista' | 'noEncontrado' | 'error'>('cargando');
  protected readonly reclamo = signal<ReclamoPanelDetalleDto | null>(null);
  protected readonly agentes = signal<readonly AgenteConCargaDto[]>([]);
  protected readonly errorAgentes = signal(false);
  protected readonly confirmandoReasignar = signal(false);
  protected readonly sinMapa = signal(false);
  protected readonly dialogo = signal<'estado' | 'descarte' | null>(null);
  private readonly intento = signal(0);

  // ---- Sugerencia de la IA (MC-25): la IA solo sugiere, el tipo lo decide el personal ----
  protected readonly eligiendoTipo = signal(false);
  protected readonly tipos = signal<readonly TipoReclamoDto[]>([]);
  protected readonly errorTipos = signal(false);
  protected readonly tipoElegido = signal('');
  protected readonly intentoTipo = signal(false);
  protected readonly guardandoTipo = signal(false);
  protected readonly errorTipo = signal<string | null>(null);
  protected readonly actualizandoSugerencia = signal(false);
  protected readonly sugerencia = computed(() => this.reclamo()?.sugerenciaIa ?? null);
  protected readonly estaCerrado = computed(() => {
    const e = this.reclamo()?.estado;
    return e === 'RESUELTO' || e === 'DESCARTADO';
  });
  // Hay sugerencia distinta del tipo elegido y nadie decidió todavía.
  protected readonly pendiente = computed(() => {
    const s = this.sugerencia();
    return s?.estado === 'HECHA' && !s.coincide && !s.decision;
  });
  protected readonly errorTipoElegido = computed(() => (this.intentoTipo() && !this.tipoElegido() ? 'Elegí un tipo.' : null));

  protected aceptarSugerencia(): void {
    const t = this.sugerencia()?.tipoSugerido;
    if (t) {
      this.guardarTipo(t);
    }
  }

  protected mantenerTipo(): void {
    const r = this.reclamo();
    if (r) {
      this.guardarTipo(r.tipo);
    }
  }

  protected abrirElegirTipo(): void {
    this.eligiendoTipo.set(true);
    this.errorTipo.set(null);
    this.intentoTipo.set(false);
    this.tipoElegido.set('');
    if (this.tipos().length === 0) {
      this.cargarTipos();
    }
  }

  protected cancelarElegirTipo(): void {
    this.eligiendoTipo.set(false);
    this.errorTipo.set(null);
  }

  protected cargarTipos(): void {
    this.errorTipos.set(false);
    this.reclamosApi.listarTipos().subscribe({
      next: (t) => this.tipos.set(t),
      error: () => this.errorTipos.set(true),
    });
  }

  protected elegirTipo(evento: Event): void {
    this.tipoElegido.set((evento.target as HTMLSelectElement).value);
    this.errorTipo.set(null);
  }

  protected guardarTipoElegido(): void {
    this.intentoTipo.set(true);
    const t = this.tipoElegido();
    if (t) {
      this.guardarTipo(t);
    }
  }

  // Cada acción manda el tipo que decide el personal; el servidor registra si fue aceptar o corregir.
  private guardarTipo(tipo: string): void {
    const r = this.reclamo();
    if (!r || this.guardandoTipo()) {
      return;
    }
    this.guardandoTipo.set(true);
    this.errorTipo.set(null);
    this.api.cambiarTipo(r.numero, tipo).subscribe({
      next: (nuevo) => {
        this.reclamo.set(nuevo);
        this.guardandoTipo.set(false);
        this.eligiendoTipo.set(false);
      },
      error: (e: unknown) => {
        this.guardandoTipo.set(false);
        const codigo = e instanceof HttpErrorResponse ? (e.error?.codigo as string | undefined) : undefined;
        this.errorTipo.set(mensajeDeTipo(e, codigo));
        // Si el reclamo cambió del lado del servidor, se vuelve a mirar para no mostrar datos viejos.
        if (codigo === 'RECLAMO_CERRADO' || codigo === 'CONFLICTO' || codigo === 'MISMO_TIPO') {
          this.eligiendoTipo.set(false);
          this.intento.update((n) => n + 1);
        }
      },
    });
  }

  // La sugerencia puede aparecer unos segundos después del alta: se vuelve a pedir el detalle.
  protected actualizarSugerencia(): void {
    const r = this.reclamo();
    if (!r || this.actualizandoSugerencia()) {
      return;
    }
    this.actualizandoSugerencia.set(true);
    this.api.detalle(r.numero).subscribe({
      next: (nuevo) => {
        this.reclamo.set(nuevo);
        this.actualizandoSugerencia.set(false);
      },
      error: () => {
        this.actualizandoSugerencia.set(false);
        this.errorTipo.set('No pudimos actualizar la sugerencia. Probá de nuevo.');
      },
    });
  }

  // ---- Posibles duplicados (MC-26): es una regla del servidor; vincular reutiliza el descarte y "nuevo" queda registrado ----
  protected readonly duplicados = computed(() => this.reclamo()?.posiblesDuplicados ?? null);
  protected readonly candidatos = computed(() => this.duplicados()?.candidatos ?? []);
  protected readonly hayAvisoDuplicados = computed(() => this.candidatos().length > 0 || !!this.duplicados()?.marcadoNuevoEn);
  protected readonly textoCandidatos = computed(() => {
    const n = this.candidatos().length;
    return n === 1 ? 'Hay 1 reclamo parecido cerca' : `Hay ${n} reclamos parecidos cerca`;
  });
  protected readonly marcandoNuevo = signal(false);
  protected readonly confirmandoNuevo = signal(false);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  protected readonly errorDuplicados = signal<string | null>(null);
  // Número del original con el que se abre la ventana de descarte (null si se abrió desde el botón de arriba).
  protected readonly originalElegido = signal<number | null>(null);

  protected esDuplicadoDe(numero: number): void {
    if (this.sinDescarte() !== null) {
      return;
    }
    this.originalElegido.set(numero);
    this.dialogo.set('descarte');
  }

  protected abrirDescarte(): void {
    this.originalElegido.set(null);
    this.dialogo.set('descarte');
  }

  // Marcar como nuevo no se puede deshacer: primero se pide confirmar, en la misma tarjeta.
  protected pedirConfirmarNuevo(): void {
    this.errorDuplicados.set(null);
    this.confirmandoNuevo.set(true);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('#texto-nuevo')?.focus(), { injector: this.injector });
  }

  protected cancelarNuevo(): void {
    this.confirmandoNuevo.set(false);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('#btn-nuevo')?.focus(), { injector: this.injector });
  }

  protected marcarComoNuevo(): void {
    const r = this.reclamo();
    if (!r || this.marcandoNuevo()) {
      return;
    }
    this.marcandoNuevo.set(true);
    this.errorDuplicados.set(null);
    this.api.marcarNoEsDuplicado(r.numero).subscribe({
      next: (nuevo) => {
        this.reclamo.set(nuevo);
        this.marcandoNuevo.set(false);
        this.confirmandoNuevo.set(false);
      },
      error: (e: unknown) => {
        this.marcandoNuevo.set(false);
        this.confirmandoNuevo.set(false);
        const codigo = e instanceof HttpErrorResponse ? (e.error?.codigo as string | undefined) : undefined;
        this.errorDuplicados.set(mensajeDeNoEsDuplicado(e, codigo));
        // Si el reclamo cambió del lado del servidor, se vuelve a mirar para no mostrar datos viejos.
        if (codigo === 'RECLAMO_CERRADO' || codigo === 'YA_MARCADO' || codigo === 'SIN_CANDIDATOS' || codigo === 'CONFLICTO') {
          this.intento.update((n) => n + 1);
        }
      },
    });
  }

  // ---- Asignar a un agente ----
  protected readonly textoAgente = textoDeAgenteConCarga;
  // Un reclamo En reparación que ya tiene agente: reasignarlo lo devuelve a Asignado, así que se pide confirmar.
  protected readonly pideConfirmar = computed(() => {
    const r = this.reclamo();
    return r?.estado === 'EN_REPARACION' && !!r.agente;
  });
  protected readonly agenteElegido = signal<number | null>(null);
  protected readonly asignando = signal(false);
  protected readonly errorAsignar = signal<string | null>(null);
  protected readonly intentoAsignar = signal(false);
  protected readonly asignado = signal<string | null>(null);
  // Se asigna mientras el reclamo no está cerrado.
  protected readonly sePuedeAsignar = computed(() => {
    const e = this.reclamo()?.estado;
    return e === 'RECIBIDO' || e === 'ASIGNADO' || e === 'EN_REPARACION';
  });
  protected readonly textoBotonAsignar = computed(() => (this.reclamo()?.agente ? 'Reasignar' : 'Asignar'));
  protected readonly errorAgenteElegido = computed(() =>
    this.intentoAsignar() && this.agenteElegido() === null && this.agentes().length > 0 ? 'Elegí a qué agente se lo asignás.' : null,
  );

  protected readonly fotosDelVecino = computed(() => (this.reclamo()?.fotos ?? []).filter((f) => f.origen === 'VECINO'));
  // Las del trabajo solo con el reclamo resuelto, como en la pantalla del vecino.
  protected readonly fotosDelTrabajo = computed(() => {
    const r = this.reclamo();
    return r?.estado === 'RESUELTO' ? r.fotos.filter((f) => f.origen === 'TRABAJO') : [];
  });
  protected readonly vistaMapa = computed<VistaMapa | null>(() => {
    const r = this.reclamo();
    return r && r.latitud !== null && r.longitud !== null && !this.sinMapa() ? { centro: { latitud: r.latitud, longitud: r.longitud }, zoom: 16 } : null;
  });
  protected readonly sinDescarte = computed(() => {
    const estado = this.reclamo()?.estado ?? 'RECIBIDO';
    if (sePuedeDescartar(estado)) {
      return null;
    }
    return estado === 'EN_REPARACION' ? 'No se puede descartar: el reclamo está en reparación' : 'No se puede descartar: el reclamo ya está cerrado';
  });
  protected readonly elegido = computed<readonly ReclamoElegido[]>(() => {
    const r = this.reclamo();
    return r ? [{ numero: r.numero, estado: r.estado }] : [];
  });
  protected readonly motivo = computed(() => textoDeMotivo(this.reclamo()?.descarte?.motivo ?? 'OTRO'));

  constructor() {
    this.recargarAgentes();
    effect((alLimpiar) => {
      const texto = this.numero();
      this.intento();
      const numero = /^\d{1,9}$/.test(texto) ? Number(texto) : 0;
      untracked(() => {
        // Al recargar el mismo reclamo (después de un cambio) se sigue mostrando lo que había.
        if (this.reclamo()?.numero !== numero) {
          this.carga.set('cargando');
          this.reclamo.set(null);
          this.sinMapa.set(false);
        }
      });
      if (numero < 1) {
        untracked(() => this.carga.set('noEncontrado'));
        return;
      }
      const sub = this.api.detalle(numero).subscribe({
        next: (r) => {
          this.reclamo.set(r);
          this.carga.set('lista');
        },
        error: (e: unknown) => this.carga.set(e instanceof HttpErrorResponse && e.status === 404 ? 'noEncontrado' : 'error'),
      });
      alLimpiar(() => sub.unsubscribe());
    });
  }

  protected elegirAgente(evento: Event): void {
    const v = (evento.target as HTMLSelectElement).value;
    this.agenteElegido.set(v ? Number(v) : null);
    this.confirmandoReasignar.set(false);
    this.errorAsignar.set(null);
    this.asignado.set(null);
  }

  // Primer paso: valida y, si el reclamo está En reparación, pide confirmar antes de enviar.
  protected asignar(): void {
    this.intentoAsignar.set(true);
    if (this.agenteElegido() === null || this.asignando()) {
      return;
    }
    if (this.pideConfirmar()) {
      this.confirmandoReasignar.set(true);
      return;
    }
    this.enviarAsignacion();
  }

  protected cancelarReasignar(): void {
    this.confirmandoReasignar.set(false);
  }

  protected enviarAsignacion(): void {
    const r = this.reclamo();
    const id = this.agenteElegido();
    this.confirmandoReasignar.set(false);
    if (!r || id === null || this.asignando()) {
      return;
    }
    this.asignando.set(true);
    this.errorAsignar.set(null);
    this.asignado.set(null);
    this.api.asignarAgente(r.numero, id).subscribe({
      next: (nuevo) => {
        this.reclamo.set(nuevo);
        this.asignado.set(`Quedó asignado a ${nuevo.agente?.nombre ?? 'el agente'}.`);
        this.agenteElegido.set(null);
        this.intentoAsignar.set(false);
        this.asignando.set(false);
        this.recargarAgentes();
      },
      error: (e: unknown) => {
        this.asignando.set(false);
        const codigo = e instanceof HttpErrorResponse ? (e.error?.codigo as string | undefined) : undefined;
        this.errorAsignar.set(mensajeDeAsignar(e, codigo));
        // Si cambió algo del lado del servidor, se vuelve a mirar el reclamo y los agentes.
        if (codigo === 'RECLAMO_CERRADO' || codigo === 'CONFLICTO' || codigo === 'YA_ASIGNADO') {
          this.alAplicar();
        }
        if (codigo === 'AGENTE_INVALIDO') {
          this.agenteElegido.set(null);
          this.recargarAgentes();
        }
      },
    });
  }

  // Si falla y todavía no había lista, se muestra el error con "Reintentar"; si ya había, se deja la que está.
  protected recargarAgentes(): void {
    this.api.agentes().subscribe({
      next: (a) => {
        this.agentes.set(a);
        this.errorAgentes.set(false);
      },
      error: () => this.errorAgentes.set(this.agentes().length === 0),
    });
  }

  // Cómo se lee cada fila del historial cuando trae agente: "Asignado a X" o "Reasignado de X a Y".
  protected textoDeAgente(c: CambioDeEstadoPanelDto): string | null {
    if (!c.agente) {
      return null;
    }
    return c.agenteAnterior ? `Reasignado de ${c.agenteAnterior.nombre} a ${c.agente.nombre}` : `Asignado a ${c.agente.nombre}`;
  }

  // Mismo estado y sin agente: no cambió el estado ni se reasignó, solo se cambió el tipo (MC-25).
  protected esCambioDeTipo(c: CambioDeEstadoPanelDto): boolean {
    return c.estadoAnterior === c.estado && !c.agente;
  }

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  protected cerrarDialogo(): void {
    this.dialogo.set(null);
  }

  // Algo cambió: se vuelve a pedir el detalle, sin esperar a que se cierre la ventana.
  protected alAplicar(): void {
    this.intento.update((n) => n + 1);
    this.recargarAgentes();
  }
}
