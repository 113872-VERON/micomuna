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

    it('muestraMiniaturaSoloSiTieneLogo', () => {
      const f = crear();
      expect(el(f).querySelector('[data-logo-miniatura="1"]')).toBeNull();
      const img = el(f).querySelector('[data-logo-miniatura="2"] img');
      expect(img?.getAttribute('src')).toBe('/api/comunas/salsipuedes-neuquen/logo');
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
        { codigo: 'LOGO_INVALIDO', detail: 'El archivo no es una imagen PNG o JPEG válida.' },
        { status: 400, statusText: 'Bad Request' },
      );
      f.detectChanges();

      expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('El archivo no es una imagen PNG o JPEG válida.');
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
});
