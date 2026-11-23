import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { ComunaDto } from '../../../auth/modelos';
import { mensajeDeError } from '../../../auth/errores';
import { ReclamosPanelService } from '../../../panel/reclamos-panel.service';
import { MetricasDto } from '../../../reclamos/modelos-panel';
import {
  ENTERO,
  anchoDeBarra,
  errorDelRango,
  esFechaIso,
  hoyEnArgentina,
  textoDeSemana,
  textoDeTiempo,
  textoDelPeriodo,
  ultimosDias,
} from '../../../reclamos/metricas-formato';

const ATAJOS = [
  { dias: 7, nombre: 'Últimos 7 días' },
  { dias: 30, nombre: 'Últimos 30 días' },
] as const;

// Métricas simples de la comuna (MC-21): cuatro números y dos gráficos de barras para un rango de fechas. Las fechas
// quedan en la URL (?desde=2026-10-01&hasta=2026-11-03); sin ellas, el servidor toma los últimos 30 días.
// Antes de pedir se revisa el rango (desde posterior a hasta, o más de 366 días) y no se llama al servidor.
@Component({
  selector: 'app-metricas',
  templateUrl: './metricas.html',
  styleUrl: './metricas.scss',
})
export class Metricas {
  readonly slug = input.required<string>();
  readonly comuna = input.required<ComunaDto>();

  private readonly api = inject(ReclamosPanelService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly atajos = ATAJOS;
  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  // Lo que dice la URL: las dos fechas, o ninguna (30 días del servidor).
  protected readonly enUrl = computed(() => ({ desde: this.params().get('desde'), hasta: this.params().get('hasta') }), {
    equal: (a, b) => a.desde === b.desde && a.hasta === b.hasta,
  });

  protected readonly desdeEscrita = signal('');
  protected readonly hastaEscrita = signal('');
  protected readonly errorFechas = signal<string | null>(null);

  protected readonly datos = signal<MetricasDto | null>(null);
  protected readonly cargando = signal(true);
  // El texto del servidor (por ejemplo FECHAS_INVALIDAS o RANGO_MUY_LARGO), o un aviso de falla de red.
  protected readonly errorServidor = signal<string | null>(null);
  protected readonly fallo = signal(false);
  private readonly intento = signal(0);

  protected readonly periodo = computed(() => {
    const d = this.datos();
    return d ? textoDelPeriodo(d.desde, d.hasta, d.dias) : '';
  });
  protected readonly tiempo = computed(() => textoDeTiempo(this.datos()?.tiempoPromedioResolucionHoras));
  protected readonly maximoTipo = computed(() => Math.max(0, ...(this.datos()?.porTipo ?? []).map((t) => t.cantidad)));
  protected readonly maximoSemana = computed(() => Math.max(0, ...(this.datos()?.porSemana ?? []).map((s) => s.cantidad)));
  protected readonly vacio = computed(() => this.datos()?.reclamosDelPeriodo === 0);

  protected readonly numero = (n: number) => ENTERO.format(n);
  protected readonly ancho = anchoDeBarra;
  protected readonly semana = textoDeSemana;

  constructor() {
    // Lo escrito en los campos sigue a la URL (por ejemplo al volver atrás); sin fechas en la URL, a lo que informó el servidor.
    effect(() => {
      const u = this.enUrl();
      untracked(() => {
        if (u.desde !== null || u.hasta !== null) {
          this.desdeEscrita.set(u.desde ?? '');
          this.hastaEscrita.set(u.hasta ?? '');
        }
      });
    });

    effect((alLimpiar) => {
      const { desde, hasta } = this.enUrl();
      this.intento();
      const conFechas = desde !== null || hasta !== null;
      if (conFechas) {
        const error = errorDelRango(desde, hasta);
        if (error) {
          // No se llama al servidor con un rango que ya se sabe inválido.
          untracked(() => {
            this.errorFechas.set(error);
            this.errorServidor.set(null);
            this.fallo.set(false);
            this.datos.set(null);
            this.cargando.set(false);
          });
          return;
        }
      }
      untracked(() => {
        this.errorFechas.set(null);
        this.errorServidor.set(null);
        this.fallo.set(false);
        this.cargando.set(true);
      });
      const sub = this.api.metricas(desde ?? undefined, hasta ?? undefined).subscribe({
        next: (m) => {
          this.datos.set(m);
          this.cargando.set(false);
          if (!conFechas) {
            this.desdeEscrita.set(m.desde);
            this.hastaEscrita.set(m.hasta);
          }
        },
        error: (e: unknown) => {
          this.datos.set(null);
          this.cargando.set(false);
          if (e instanceof HttpErrorResponse && e.status === 400) {
            this.errorServidor.set(mensajeDeError(e));
          } else {
            this.fallo.set(true);
          }
        },
      });
      alLimpiar(() => sub.unsubscribe());
    });
  }

  protected escribirDesde(evento: Event): void {
    this.desdeEscrita.set((evento.target as HTMLInputElement).value);
  }

  protected escribirHasta(evento: Event): void {
    this.hastaEscrita.set((evento.target as HTMLInputElement).value);
  }

  // "Ver métricas": revisa las fechas escritas y, si están bien, las pone en la URL (eso dispara el pedido).
  protected aplicar(evento?: Event): void {
    evento?.preventDefault();
    const desde = this.desdeEscrita();
    const hasta = this.hastaEscrita();
    const error = errorDelRango(desde, hasta);
    this.errorFechas.set(error);
    if (!error && esFechaIso(desde) && esFechaIso(hasta)) {
      this.irA(desde, hasta);
    }
  }

  protected usarAtajo(dias: number): void {
    const { desde, hasta } = ultimosDias(dias, hoyEnArgentina());
    this.desdeEscrita.set(desde);
    this.hastaEscrita.set(hasta);
    this.errorFechas.set(null);
    this.irA(desde, hasta);
  }

  // El atajo está activo cuando el rango mostrado es justo ese. Sin fechas en la URL rige el de 30 días.
  protected atajoActivo(dias: number): boolean {
    const u = this.enUrl();
    if (u.desde === null && u.hasta === null) {
      return dias === 30;
    }
    const r = ultimosDias(dias, hoyEnArgentina());
    return u.desde === r.desde && u.hasta === r.hasta;
  }

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  private irA(desde: string, hasta: string): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { desde, hasta }, queryParamsHandling: 'merge' });
  }
}
