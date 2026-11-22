import { Component, ElementRef, Injector, afterNextRender, computed, inject } from '@angular/core';
import { MAX_FOTOS } from '../../../reclamos/modelos';
import { BorradorReclamo } from '../borrador-reclamo';

// Paso 2: hasta 3 fotos, opcionales. Dos entradas de archivo: una abre la cámara (`capture`) y la otra
// la galería. En una computadora las dos abren el selector de archivos.
@Component({
  selector: 'app-paso-fotos',
  templateUrl: './paso-fotos.html',
  styleUrl: './paso-fotos.scss',
})
export class PasoFotos {
  protected readonly b = inject(BorradorReclamo);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly maximo = MAX_FOTOS;
  protected readonly hayFotos = computed(() => this.b.fotos().length > 0);
  protected readonly puedeAgregar = computed(() => this.b.lugaresLibres() > 0);
  protected readonly titulo = computed(() => {
    const cantidad = this.b.fotos().length;
    return cantidad === 0 ? 'Sacá una foto' : cantidad === 1 ? '¿Se ve bien?' : '¿Se ven bien?';
  });
  protected readonly bajada = computed(() =>
    this.hayFotos()
      ? 'Si no se ve bien, quitala y sacá otra. Podés agregar hasta 3.'
      : 'Ayuda a la comuna a resolverlo más rápido. No es obligatoria.',
  );

  protected elegir(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivos = Array.from(input.files ?? []);
    // Se vacía para poder volver a elegir la misma foto.
    input.value = '';
    void this.b.agregarFotos(archivos);
  }

  protected quitar(id: number): void {
    this.b.quitarFoto(id);
    // El botón tocado desaparece: el foco pasa a la próxima acción para no perderse.
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>('.quitar, .agregar')?.focus(),
      { injector: this.injector },
    );
  }
}
