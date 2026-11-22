import { Component, DestroyRef, ElementRef, Injector, afterNextRender, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { ComunaDto } from '../../auth/modelos';
import { TemaComunaService } from '../../tema/tema-comuna.service';
import { AvisoSalir } from './aviso-salir/aviso-salir';
import { BorradorReclamo } from './borrador-reclamo';
import { PasoDonde } from './paso-donde/paso-donde';
import { PasoFotos } from './paso-fotos/paso-fotos';
import { PasoQuePaso } from './paso-que-paso/paso-que-paso';
import { PasoRevisar } from './paso-revisar/paso-revisar';
import { pasoDeSegmento } from './pasos';
import { ReclamoRecibido } from './reclamo-recibido/reclamo-recibido';

const TOTAL_PASOS = 4;

// Reportar un problema (MC-15): cuatro pasos y la confirmación, una cosa por pantalla.
// Marco propio de una columna, pensado para el celular: sin la cabecera grande de la comuna,
// con "Volver", "Paso n de 4" y la barra de progreso. El estado vive en BorradorReclamo y
// el paso que se ve sale de la dirección (/<slug>/reportar/<paso>).
@Component({
  selector: 'app-reportar',
  imports: [PasoQuePaso, PasoFotos, PasoDonde, PasoRevisar, ReclamoRecibido, AvisoSalir],
  providers: [BorradorReclamo],
  templateUrl: './reportar.html',
  styleUrl: './reportar.scss',
  host: { '(window:beforeunload)': 'antesDeCerrar($event)' },
})
export class Reportar {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();
  // El segmento de la dirección: que-paso, fotos, donde, revisar o listo.
  readonly paso = input<string>();

  protected readonly b = inject(BorradorReclamo);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly tema = inject(TemaComunaService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly total = TOTAL_PASOS;
  protected readonly segmentos = Array.from({ length: TOTAL_PASOS }, (_, i) => i + 1);
  protected readonly numeroDePaso = computed(() => {
    const paso = this.b.paso();
    return paso === 'listo' ? null : paso;
  });

  // Mientras el aviso está abierto, la respuesta que espera la navegación.
  protected readonly preguntando = signal<((salir: boolean) => void) | null>(null);

  constructor() {
    effect(() => this.tema.aplicar(this.comuna().color));
    inject(DestroyRef).onDestroy(() => {
      this.tema.restablecer();
      this.preguntando()?.(false);
    });
    this.b.cargarTipos();

    effect(() => {
      const segmento = this.paso();
      untracked(() => this.mostrarPaso(segmento));
    });

    // Al cambiar de paso, el foco va al título: el lector de pantalla anuncia dónde está
    // y el teclado sigue desde arriba. Al abrir la pantalla no se mueve.
    let primera = true;
    effect(() => {
      this.b.paso();
      if (primera) {
        primera = false;
        return;
      }
      afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('h1')?.focus(), { injector: this.injector });
    });
  }

  // La dirección pide un paso; se muestra si los anteriores están completos. Si no (alguien entra
  // directo a /revisar, o usa "adelante" después de borrar algo), se corrige la dirección.
  private mostrarPaso(segmento: string | undefined): void {
    const pedido = pasoDeSegmento(segmento);
    // Con el reclamo ya enviado, "atrás" no vuelve a abrir los pasos: lleva al inicio.
    if (this.b.creado() && pedido !== 'listo') {
      void this.router.navigateByUrl('/inicio', { replaceUrl: true });
      return;
    }
    const permitido = this.b.pasoPermitido(pedido ?? 1);
    this.b.paso.set(permitido);
    if (permitido !== pedido) {
      this.b.ir(permitido, { reemplazar: true });
    }
  }

  // Lo usa la guarda de la ruta. Entre pasos del recorrido no se pregunta nada.
  puedeSalirHacia(url: string): boolean | Promise<boolean> {
    const base = this.router.serializeUrl(this.router.createUrlTree(['/', this.slug(), 'reportar']));
    if (url === base || url.startsWith(`${base}/`) || !this.hayQuePreguntar()) {
      return true;
    }
    this.preguntando()?.(false);
    return new Promise<boolean>((resolver) =>
      this.preguntando.set((salir) => {
        this.preguntando.set(null);
        resolver(salir);
      }),
    );
  }

  // Al recargar o cerrar la pestaña, el navegador muestra su propio aviso.
  protected antesDeCerrar(evento: BeforeUnloadEvent): void {
    if (this.hayQuePreguntar()) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  // Sin sesión (venció y el servidor la cerró) o con el reclamo enviado no hay nada que cuidar.
  private hayQuePreguntar(): boolean {
    return this.auth.estaAutenticado() && !this.b.creado() && this.b.tieneDatos();
  }

  protected volver(): void {
    const paso = this.b.paso();
    if (paso === 1 || paso === 'listo') {
      void this.router.navigateByUrl('/inicio');
      return;
    }
    this.b.ir((paso - 1) as 1 | 2 | 3);
  }
}
