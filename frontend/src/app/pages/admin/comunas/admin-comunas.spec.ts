import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ComunaAdminDto } from '../../../auth/modelos';
import { AdminComunas } from './admin-comunas';

const URL = '/api/admin/comunas';
const salsipuedes: ComunaAdminDto = {
  id: 1, nombre: 'Salsipuedes', slug: 'salsipuedes-cordoba', provincia: 'Córdoba', departamento: 'Colón', color: 'CELESTE', activa: true, tieneLogo: false,
};
const neuquen: ComunaAdminDto = {
  id: 2, nombre: 'Salsipuedes', slug: 'salsipuedes-neuquen', provincia: 'Neuquén', departamento: null, color: 'OCRE', activa: false, tieneLogo: true,
};

describe('AdminComunas', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminComunas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(respuesta: ComunaAdminDto[] = [salsipuedes, neuquen]): ComponentFixture<AdminComunas> {
    const fixture = TestBed.createComponent(AdminComunas);
    fixture.detectChanges();
    http.expectOne(URL).flush(respuesta);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const boton = (f: ComponentFixture<unknown>, texto: string) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes(texto));

  it('listaLasComunasConSuEnlaceYSuEstado', () => {
    const texto = el(crear()).textContent ?? '';
    expect(texto).toContain('/salsipuedes-cordoba');
    expect(texto).toContain('/salsipuedes-neuquen');
    expect(texto).toContain('Colón, Córdoba');
    expect(texto).toContain('Activa');
    expect(texto).toContain('Inactiva');
    expect(texto).toContain('Celeste');
    expect(texto).toContain('Ocre');
  });

  it('sinComunasInvitaACrearLaPrimera', () => {
    expect(el(crear([])).textContent).toContain('Todavía no hay comunas');
  });

  it('llevaAlAltaDeUnaComuna', () => {
    const enlaces = Array.from(el(crear()).querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(enlaces).toContain('/admin/comunas/nueva');
  });

  it('desactivarPideConfirmacionYLlamaAlBackend', () => {
    const f = crear();

    boton(f, 'Desactivar')!.click();
    f.detectChanges();
    http.expectNone(`${URL}/1`);
    expect(el(f).textContent).toContain('Nadie de esta comuna podrá ingresar');

    boton(f, 'Sí, desactivar')!.click();
    const pedido = http.expectOne(`${URL}/1`);
    expect(pedido.request.method).toBe('PATCH');
    expect(pedido.request.body).toEqual({ activa: false });
    pedido.flush({ ...salsipuedes, activa: false });
    f.detectChanges();

    expect(el(f).querySelectorAll('.estado-comuna.inactiva').length).toBe(2);
  });

  it('cancelarLaConfirmacionNoCambiaNada', () => {
    const f = crear();
    boton(f, 'Desactivar')!.click();
    f.detectChanges();
    boton(f, 'Cancelar')!.click();
    f.detectChanges();

    http.expectNone(`${URL}/1`);
    expect(el(f).textContent).not.toContain('Nadie de esta comuna podrá ingresar');
  });

  it('activarUnaComunaInactivaLlamaAlBackendSinPreguntar', () => {
    const f = crear();
    boton(f, 'Activar')!.click();

    const pedido = http.expectOne(`${URL}/2`);
    expect(pedido.request.body).toEqual({ activa: true });
    pedido.flush({ ...neuquen, activa: true });
    f.detectChanges();

    expect(el(f).querySelectorAll('.estado-comuna.inactiva').length).toBe(0);
  });

  it('siFallaElCambioDeEstadoSeAvisa', () => {
    const f = crear();
    boton(f, 'Activar')!.click();
    http.expectOne(`${URL}/2`).error(new ProgressEvent('error'));
    f.detectChanges();

    expect(el(f).textContent).toContain('No pudimos conectarnos');
  });

  describe('logo', () => {
    const botonLogo = (f: ComponentFixture<unknown>, id: number) =>
      el(f).querySelector<HTMLButtonElement>(`[data-logo-boton="${id}"]`)!;
    const entrada = (f: ComponentFixture<unknown>, id: number) =>
      el(f).querySelector<HTMLInputElement>(`[data-logo-entrada="${id}"]`)!;

    function elegir(f: ComponentFixture<unknown>, id: number, archivo: File): HTMLInputElement {
      const input = entrada(f, id);
      // El entorno de pruebas no trae DataTransfer: se simula la lista de archivos.
      Object.defineProperty(input, 'files', { value: [archivo], configurable: true });
      input.dispatchEvent(new Event('change'));
      f.detectChanges();
      return input;
    }
    const png = (bytes = 10) => new File([new Uint8Array(bytes)], 'logo.png', { type: 'image/png' });

    it('elBotonDiceSubirOCambiarSegunTengaLogo', () => {
      const f = crear();
      expect(botonLogo(f, 1).textContent).toContain('Subir logo');
      expect(botonLogo(f, 2).textContent).toContain('Cambiar logo');
    });

    it('lasEntradasAceptanSoloPngYJpegYLaAyudaDiceLosRequisitos', () => {
      const f = crear();
      expect(entrada(f, 1).getAttribute('accept')).toBe('image/png,image/jpeg');
      expect(el(f).textContent).toContain('cuadrado, PNG o JPG, hasta 512 KB');
    });

    const miniatura = (f: ComponentFixture<unknown>, id: number) =>
      el(f).querySelector<HTMLImageElement>(`[data-logo-miniatura="${id}"] img`);
    const nota = (f: ComponentFixture<unknown>, id: number) => el(f).querySelector(`[data-logo-nota="${id}"]`);
    const activaConLogo: ComunaAdminDto = { ...salsipuedes, tieneLogo: true };

    function cambiarEstado(f: ComponentFixture<unknown>, comuna: ComunaAdminDto, activa: boolean): void {
      if (activa) {
        boton(f, 'Activar')!.click();
      } else {
        boton(f, 'Desactivar')!.click();
        f.detectChanges();
        boton(f, 'Sí, desactivar')!.click();
      }
      http.expectOne(`${URL}/${comuna.id}`).flush({ ...comuna, activa });
      f.detectChanges();
    }

    it('muestraMiniaturaDeUnaComunaActivaConLogo', () => {
      const f = crear([activaConLogo]);
      expect(miniatura(f, 1)?.getAttribute('src')).toBe('/api/comunas/salsipuedes-cordoba/logo');
      expect(miniatura(f, 1)?.getAttribute('alt')).toBe('Logo actual de Salsipuedes');
      expect(nota(f, 1)).toBeNull();
    });

    it('sinLogoNoHayMiniaturaNiNota', () => {
      const f = crear([salsipuedes]);
      expect(miniatura(f, 1)).toBeNull();
      expect(nota(f, 1)).toBeNull();
    });

    it('unaComunaInactivaConLogoNoPideLaMiniaturaYAvisaQueElLogoEstaCargado', () => {
      const f = crear([neuquen]);
      // El endpoint público responde 404 para comunas inactivas: no tiene que haber ningún <img>.
      expect(el(f).querySelector('img')).toBeNull();
      expect(nota(f, 2)?.textContent).toContain('Logo cargado (se ve cuando la comuna está activa)');
      expect(botonLogo(f, 2).textContent).toContain('Cambiar logo');
    });

    it('alReactivarUnaComunaConLogoVuelveLaMiniatura', () => {
      const f = crear([neuquen]);
      cambiarEstado(f, neuquen, true);

      expect(miniatura(f, 2)?.getAttribute('src')).toBe('/api/comunas/salsipuedes-neuquen/logo');
      expect(nota(f, 2)).toBeNull();
    });

    it('alDesactivarUnaComunaConLogoSeQuitaLaMiniaturaYApareceLaNota', () => {
      const f = crear([activaConLogo]);
      cambiarEstado(f, activaConLogo, false);

      expect(miniatura(f, 1)).toBeNull();
      expect(nota(f, 1)).not.toBeNull();
    });

    it('unaMiniaturaQueFalloSeRecuperaAlDesactivarYReactivarLaComuna', () => {
      const f = crear([activaConLogo]);
      miniatura(f, 1)!.dispatchEvent(new Event('error'));
      f.detectChanges();
      expect(miniatura(f, 1)).toBeNull();

      cambiarEstado(f, activaConLogo, false);
      cambiarEstado(f, activaConLogo, true);

      expect(miniatura(f, 1)).not.toBeNull();
    });

    it('unaMiniaturaQueFalloSeRecuperaAlSubirUnLogoNuevo', () => {
      const f = crear([activaConLogo]);
      miniatura(f, 1)!.dispatchEvent(new Event('error'));
      f.detectChanges();
      expect(miniatura(f, 1)).toBeNull();

      elegir(f, 1, png());
      http.expectOne(`${URL}/1/logo`).flush(activaConLogo);
      f.detectChanges();

      expect(miniatura(f, 1)?.getAttribute('src')).toMatch(/\/logo\?v=\d+$/);
    });

    it('rechazaSinLlamarAlBackendUnArchivoDeMasDe512KB', () => {
      const f = crear();
      const input = elegir(f, 1, png(512 * 1024 + 1));

      http.expectNone(`${URL}/1/logo`);
      expect(el(f).textContent).toContain('El logo pesa más de 512 KB. Elegí una imagen más liviana.');
      expect(input.value).toBe('');
    });

    it('rechazaSinLlamarAlBackendUnArchivoQueNoEsPngNiJpeg', () => {
      const f = crear();
      elegir(f, 1, new File(['<svg/>'], 'logo.svg', { type: 'image/svg+xml' }));

      http.expectNone(`${URL}/1/logo`);
      expect(el(f).textContent).toContain('El logo tiene que ser una imagen PNG o JPG.');
    });

    it('aceptaJpegYElLimiteExactoDe512KB', () => {
      const f = crear();
      elegir(f, 1, new File([new Uint8Array(512 * 1024)], 'logo.jpg', { type: 'image/jpeg' }));

      http.expectOne(`${URL}/1/logo`).flush({ ...salsipuedes, tieneLogo: true });
    });

    it('mientrasSubeMuestraSubiendoYDeshabilitaElBoton', () => {
      const f = crear();
      elegir(f, 1, png());

      expect(botonLogo(f, 1).disabled).toBe(true);
      expect(botonLogo(f, 1).textContent).toContain('Subiendo…');
      http.expectOne(`${URL}/1/logo`).flush({ ...salsipuedes, tieneLogo: true });
    });

    it('alTerminarBienActualizaLaFilaYMuestraLaMiniaturaConParametroDeCache', () => {
      const f = crear();
      elegir(f, 1, png());
      const pedido = http.expectOne(`${URL}/1/logo`);
      expect(pedido.request.method).toBe('PUT');
      expect((pedido.request.body as FormData).get('archivo')).toBeTruthy();
      pedido.flush({ ...salsipuedes, tieneLogo: true });
      f.detectChanges();

      expect(botonLogo(f, 1).textContent).toContain('Cambiar logo');
      expect(botonLogo(f, 1).disabled).toBe(false);
      const src = el(f).querySelector('[data-logo-miniatura="1"] img')?.getAttribute('src');
      expect(src).toMatch(/^\/api\/comunas\/salsipuedes-cordoba\/logo\?v=\d+$/);
    });

    it('conLogoInvalidoDelServidorMuestraSuMensajeYVuelveAHabilitar', () => {
      const f = crear();
      elegir(f, 2, png());
      http.expectOne(`${URL}/2/logo`).flush(
        { codigo: 'LOGO_INVALIDO', detail: 'El logo tiene que ser una imagen PNG o JPG de hasta 512 KB y 2000 x 2000 píxeles' },
        { status: 400, statusText: 'Bad Request' },
      );
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('El logo tiene que ser una imagen PNG o JPG de hasta 512 KB y 2000 x 2000 píxeles');
      expect(botonLogo(f, 2).disabled).toBe(false);
    });

    it('despuesDeElegirUnArchivoElInputQuedaVacioParaPoderRepetirlo', () => {
      const f = crear();
      const input = elegir(f, 1, png());
      expect(input.value).toBe('');
      http.expectOne(`${URL}/1/logo`).flush({ ...salsipuedes, tieneLogo: true });
    });
  });

  it('siFallaLaCargaOfreceReintentar', () => {
    const f = TestBed.createComponent(AdminComunas);
    f.detectChanges();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Error' });
    f.detectChanges();
    expect(el(f).textContent).toContain('No pudimos cargar las comunas');

    boton(f, 'Reintentar')!.click();
    http.expectOne(URL).flush([salsipuedes]);
    f.detectChanges();
    expect(el(f).textContent).toContain('/salsipuedes-cordoba');
  });
  describe('límite de la comuna', () => {
    const botonLimite = (f: ComponentFixture<unknown>, id: number) =>
      el(f).querySelector<HTMLButtonElement>(`[data-limite-boton="${id}"]`)!;
    const estadoLimite = (f: ComponentFixture<unknown>, id: number) =>
      el(f).querySelector(`[data-limite-estado="${id}"]`)?.textContent?.trim();
    const FIGURA = { type: 'Polygon', coordinates: [[[-64.31, -31.13], [-64.29, -31.13], [-64.29, -31.11], [-64.31, -31.11], [-64.31, -31.13]]] };
    const conLimite: ComunaAdminDto = { ...salsipuedes, tieneLimite: true, limiteCargadoEn: '2026-11-20T15:30:00Z' };

    async function elegir(f: ComponentFixture<unknown>, id: number, archivo: File): Promise<HTMLInputElement> {
      const input = el(f).querySelector<HTMLInputElement>(`[data-limite-entrada="${id}"]`)!;
      Object.defineProperty(input, 'files', { value: [archivo], configurable: true });
      input.dispatchEvent(new Event('change'));
      // El archivo se lee en el navegador antes de mandarlo.
      for (let i = 0; i < 5; i++) {
        await new Promise((listo) => setTimeout(listo, 0));
      }
      f.detectChanges();
      return input;
    }
    const geojson = (contenido: unknown = FIGURA) => new File([JSON.stringify(contenido)], 'limite.geojson', { type: 'application/geo+json' });

    it('sinLimiteAvisaQueLosVecinosPuedenReportarDesdeCualquierLugar', () => {
      const f = crear();

      expect(estadoLimite(f, 1)).toBe('Sin límite: los vecinos pueden reportar desde cualquier lugar.');
      expect(botonLimite(f, 1).textContent).toContain('Subir límite (GeoJSON)');
    });

    it('conLimiteMuestraLaFechaYOfreceCambiarlo', () => {
      const f = crear([conLimite]);

      expect(estadoLimite(f, 1)).toMatch(/^Límite cargado el 20 de noviembre de 2026$/);
      expect(botonLimite(f, 1).textContent).toContain('Cambiar límite');
    });

    it('elInputTieneEtiquetaYElBotonMide44', () => {
      const f = crear();

      expect(el(f).querySelector('[data-limite-entrada="1"]')?.getAttribute('aria-label')).toBe('Límite de Salsipuedes (GeoJSON)');
      expect(botonLimite(f, 1).classList.contains('chico')).toBe(true);
    });

    it('subeElGeoJsonPorPutYActualizaLaFila', async () => {
      const f = crear();
      const input = await elegir(f, 1, geojson());

      const pedido = http.expectOne(`${URL}/1/limite`);
      expect(pedido.request.method).toBe('PUT');
      expect(pedido.request.body).toEqual(FIGURA);
      expect(botonLimite(f, 1).textContent).toContain('Subiendo…');
      expect(botonLimite(f, 1).disabled).toBe(true);
      pedido.flush(conLimite);
      f.detectChanges();

      expect(estadoLimite(f, 1)).toContain('Límite cargado el 20 de noviembre de 2026');
      expect(botonLimite(f, 1).textContent).toContain('Cambiar límite');
      expect(botonLimite(f, 1).disabled).toBe(false);
      expect(input.value).toBe('');
    });

    it('rechazaSinLlamarAlBackendUnArchivoDeMasDe1MB', async () => {
      const f = crear();

      await elegir(f, 1, new File([new Uint8Array(1024 * 1024 + 1)], 'grande.geojson'));

      http.expectNone(`${URL}/1/limite`);
      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('pesa más de 1 MB');
      expect(botonLimite(f, 1).disabled).toBe(false);
    });

    it('rechazaSinLlamarAlBackendUnArchivoQueNoEsJson', async () => {
      const f = crear();

      await elegir(f, 1, new File(['<kml></kml>'], 'limite.kml'));

      http.expectNone(`${URL}/1/limite`);
      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('no es un GeoJSON');
      expect(estadoLimite(f, 1)).toContain('Sin límite');
    });

    it('muestraElMensajeDelServidorSiElLimiteNoSirve', async () => {
      const f = crear();
      await elegir(f, 1, geojson({ type: 'Polygon', coordinates: [] }));

      http.expectOne(`${URL}/1/limite`).flush(
        { status: 400, codigo: 'LIMITE_INVALIDO', detail: 'El polígono se corta a sí mismo.' },
        { status: 400, statusText: 'Bad Request' },
      );
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('El polígono se corta a sí mismo.');
      expect(estadoLimite(f, 1)).toContain('Sin límite');
      expect(botonLimite(f, 1).disabled).toBe(false);
    });

    it('unaSubidaNuevaLimpiaElErrorAnterior', async () => {
      const f = crear();
      await elegir(f, 1, new File(['no'], 'x.geojson'));
      expect(el(f).querySelector('[role="alert"]')).not.toBeNull();

      await elegir(f, 1, geojson());
      http.expectOne(`${URL}/1/limite`).flush(conLimite);
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')).toBeNull();
    });
  });
});
