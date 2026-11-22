import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../../auth/auth.service';
import { UsuarioComunaDto } from '../../../auth/modelos';
import { NuevoUsuario } from './nuevo-usuario/nuevo-usuario';
import { Usuarios } from './usuarios';

const base = '/salsipuedes-cordoba/panel/usuarios';
const creadoEn = '2026-11-03T10:00:00Z';
const SERGIO: UsuarioComunaDto = { id: 1, nombre: 'Sergio Vera', correo: 'sergio@salsipuedes.gob.ar', rol: 'ADMIN_COMUNA', estado: 'ACTIVO', creadoEn };
const NUEVO: UsuarioComunaDto = { id: 7, nombre: 'Ana Pérez', correo: 'ana@salsipuedes.gob.ar', rol: 'AGENTE', estado: 'PENDIENTE', creadoEn };

describe('Usuarios: de la lista al alta y de vuelta', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: ':slug/panel/usuarios', component: Usuarios },
            { path: ':slug/panel/usuarios/nuevo', component: NuevoUsuario },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).guardarSesion({
      token: 'tk',
      usuario: { id: 1, nombre: 'Sergio Vera', correo: SERGIO.correo, rol: 'ADMIN_COMUNA', comunaSlug: 'salsipuedes-cordoba' },
    });
  });

  afterEach(() => http.verify());

  it('listaANuevoYCancelarVuelveAListaSinEmpujarLaTabla', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(base);
    http.expectOne('/api/usuarios').flush([SERGIO]);
    harness.detectChanges();
    const el = () => harness.routeNativeElement as HTMLElement;

    (el().querySelector('#boton-nuevo-usuario') as HTMLAnchorElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(TestBed.inject(Location).path()).toBe(`${base}/nuevo`);
    expect(el().querySelector('h1')?.textContent).toContain('Nuevo usuario');
    expect(el().querySelector('table')).toBeNull();

    const cancelar = Array.from(el().querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Cancelar')!;
    cancelar.click();
    await harness.fixture.whenStable();
    // La lista se vuelve a cargar al entrar: el usuario nuevo aparece.
    http.expectOne('/api/usuarios').flush([SERGIO, NUEVO]);
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe(base);
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Ana Pérez');
  });
});
