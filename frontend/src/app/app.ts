import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';
import { SaludService } from './services/salud.service';

@Component({
  imports: [RouterOutlet, MatToolbarModule, MatButtonModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly saludService = inject(SaludService);

  protected readonly titulo = signal('MiComuna');
  protected readonly backend = signal<'consultando' | 'conectado' | 'sin-conexion'>('consultando');

  ngOnInit(): void {
    this.saludService.consultar().subscribe({
      next: () => this.backend.set('conectado'),
      error: () => this.backend.set('sin-conexion'),
    });
  }
}
