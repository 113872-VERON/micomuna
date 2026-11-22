import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BorradorReclamo } from '../borrador-reclamo';

// "Hoy, 3 de noviembre" o "el 2 de noviembre" (el año solo si no es el actual, como pide la guía).
export function cuandoSeEnvio(creadoEn: string | undefined, ahora = new Date()): string | null {
  const fecha = creadoEn ? new Date(creadoEn) : null;
  if (!fecha || Number.isNaN(fecha.getTime())) {
    return null;
  }
  const mismoAnio = fecha.getFullYear() === ahora.getFullYear();
  const texto = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long', ...(mismoAnio ? {} : { year: 'numeric' }) }).format(fecha);
  return fecha.toDateString() === ahora.toDateString() ? `Lo enviaste hoy, ${texto}.` : `Lo enviaste el ${texto}.`;
}

// Confirmación: el número de seguimiento en grande y el estado inicial.
@Component({
  selector: 'app-reclamo-recibido',
  imports: [RouterLink],
  templateUrl: './reclamo-recibido.html',
  styleUrl: './reclamo-recibido.scss',
})
export class ReclamoRecibido {
  protected readonly b = inject(BorradorReclamo);
  // La comuna sale de la dirección (/<slug>/reportar/listo): es la misma de la sesión.
  protected readonly slug = inject(ActivatedRoute).snapshot.paramMap.get('slug') ?? '';
  protected readonly cuando = computed(() => cuandoSeEnvio(this.b.creado()?.creadoEn));
}
