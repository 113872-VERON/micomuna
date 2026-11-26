import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { mensajeDeError } from '../../../auth/errores';
import { ChipPrioridad } from '../../../compartido/prioridad/chip-prioridad';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { ReclamoPanelDetalleDto } from '../../../reclamos/modelos-panel';
import { NOMBRE_PRIORIDAD, PrioridadPanelDto, SIN_PRIORIDAD, VALORES_DE_PRIORIDAD, ValorPrioridad, nombreDePrioridad } from '../../../reclamos/prioridad';

export function mensajeDePrioridad(e: unknown, codigo: string | undefined): string {
  switch (codigo) {
    case 'RECLAMO_CERRADO':
      return 'El reclamo ya está cerrado: no se puede cambiar la prioridad.';
    case 'MISMA_PRIORIDAD':
      return 'El reclamo ya tiene esa prioridad fijada. Elegí otra si querés cambiarla.';
    case 'CONFLICTO':
      return 'Otra persona está cambiando este reclamo en este momento. Esperá un instante y probá de nuevo.';
    default:
      return mensajeDeError(e);
  }
}

// La prioridad del reclamo y el control para fijarla (MC-27). La IA solo sugiere: mientras nadie la fija se usa la
// sugerida, y elegirla la confirma. Con el reclamo cerrado se ve, sin poder cambiarla.
@Component({
  selector: 'app-control-prioridad',
  imports: [DatePipe, ChipPrioridad],
  templateUrl: './control-prioridad.html',
  styleUrl: './control-prioridad.scss',
})
export class ControlPrioridad {
  readonly numero = input.required<number>();
  readonly prioridad = input<PrioridadPanelDto | null | undefined>(null);
  readonly cerrado = input(false);
  // El servidor devolvió el detalle con la prioridad fijada.
  readonly actualizado = output<ReclamoPanelDetalleDto>();
  // Algo cambió del lado del servidor (cerrado, conflicto, ya fijada): hay que volver a pedir el detalle.
  readonly desactualizado = output<void>();

  private readonly api = inject(ReclamosPanelService);

  protected readonly valores = VALORES_DE_PRIORIDAD;
  protected readonly nombres = NOMBRE_PRIORIDAD;
  protected readonly guardando = signal<ValorPrioridad | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmado = signal<string | null>(null);

  protected readonly actual = computed(() => this.prioridad() ?? SIN_PRIORIDAD);
  protected readonly origen = computed(() => this.actual().origen);
  protected readonly nombreActual = computed(() => nombreDePrioridad(this.actual().valor));

  // Si fijó el personal, esa opción ya está elegida (mandarla de nuevo da 409). Si es la sugerida, elegirla la confirma.
  protected estaElegida(v: ValorPrioridad): boolean {
    return this.origen() === 'PERSONAL' && this.actual().valor === v;
  }

  protected esLaSugerida(v: ValorPrioridad): boolean {
    return this.origen() === 'IA' && this.actual().valor === v;
  }

  protected fijar(v: ValorPrioridad): void {
    if (this.guardando() || this.cerrado() || this.estaElegida(v)) {
      return;
    }
    const confirmaLaSugerida = this.esLaSugerida(v);
    this.guardando.set(v);
    this.error.set(null);
    this.confirmado.set(null);
    this.api.cambiarPrioridad(this.numero(), v).subscribe({
      next: (nuevo) => {
        this.guardando.set(null);
        this.confirmado.set(confirmaLaSugerida ? `Confirmaste la prioridad ${NOMBRE_PRIORIDAD[v].toLowerCase()} que sugirió la IA.` : `Prioridad fijada: ${NOMBRE_PRIORIDAD[v].toLowerCase()}.`);
        this.actualizado.emit(nuevo);
      },
      error: (e: unknown) => {
        this.guardando.set(null);
        const codigo = e instanceof HttpErrorResponse ? (e.error?.codigo as string | undefined) : undefined;
        this.error.set(mensajeDePrioridad(e, codigo));
        // Si el reclamo cambió del lado del servidor, se vuelve a mirar para no mostrar datos viejos.
        if (codigo === 'RECLAMO_CERRADO' || codigo === 'CONFLICTO' || codigo === 'MISMA_PRIORIDAD') {
          this.desactualizado.emit();
        }
      },
    });
  }
}
