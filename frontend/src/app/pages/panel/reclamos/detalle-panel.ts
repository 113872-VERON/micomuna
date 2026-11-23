import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeDeError } from '../../../auth/errores';
import { ComunaDto } from '../../../auth/modelos';
import { ChipEstado } from '../../../compartido/estado-reclamo/chip-estado';
import { FotoReclamo } from '../../../compartido/foto-reclamo/foto-reclamo';
import { IconoTipo } from '../../../compartido/icono-tipo/icono-tipo';
import { MapaUbicacion, VistaMapa } from '../../../mapas/mapa-ubicacion/mapa-ubicacion';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { FotosDeReclamos } from '../../../reclamos/fotos-de-reclamos';
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

  protected readonly carga = signal<'cargando' | 'lista' | 'noEncontrado' | 'error'>('cargando');
  protected readonly reclamo = signal<ReclamoPanelDetalleDto | null>(null);
  protected readonly agentes = signal<readonly AgenteConCargaDto[]>([]);
  protected readonly errorAgentes = signal(false);
  protected readonly confirmandoReasignar = signal(false);
  protected readonly sinMapa = signal(false);
  protected readonly dialogo = signal<'estado' | 'descarte' | null>(null);
  private readonly intento = signal(0);

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
