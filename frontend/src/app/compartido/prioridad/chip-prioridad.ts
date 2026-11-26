import { Component, computed, input } from '@angular/core';
import { PrioridadReclamoDto, nombreDePrioridad } from '../../reclamos/prioridad';

// La prioridad de un reclamo en una pastilla: ícono, palabra y color. La palabra siempre va escrita; el color y el
// dibujo solo acompañan. Si la sugirió la IA (y nadie la fijó) lleva la marca "Sugerida" con un borde punteado, que
// se distingue sin depender del color; los lectores de pantalla oyen "sugerida por la IA".
@Component({
  selector: 'app-chip-prioridad',
  templateUrl: './chip-prioridad.html',
  styleUrl: './chip-prioridad.scss',
})
export class ChipPrioridad {
  // Un reclamo sin dato (un servidor viejo) se muestra como "Sin prioridad".
  readonly prioridad = input<PrioridadReclamoDto | null | undefined>(null);
  protected readonly valor = computed(() => this.prioridad()?.valor ?? null);
  protected readonly nombre = computed(() => nombreDePrioridad(this.valor()));
  protected readonly sugerida = computed(() => this.valor() !== null && this.prioridad()?.origen === 'IA');
}
