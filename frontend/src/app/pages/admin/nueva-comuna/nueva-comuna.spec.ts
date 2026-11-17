import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import type { MockInstance } from 'vitest';
import { ComunaCreadaDto } from '../../../auth/modelos';
import { NuevaComuna } from './nueva-comuna';

const URL = '/api/admin/comunas';

const creada = (invitacionEnviada: boolean): ComunaCreadaDto => ({
  comuna: { id: 7, nombre: 'Salsipuedes', slug: 'salsipuedes-cordoba', provincia: 'Córdoba', departamento: null, color: 'CELESTE', activa: true, tieneLogo: false },
  adminCorreo: 'sergio.vera@salsipuedes.gob.ar',
  invitacionEnviada,
});

describe('NuevaComuna', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NuevaComuna],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(): ComponentFixture<NuevaComuna> {
    const fixture = TestBed.createComponent(NuevaComuna);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const campo = (f: ComponentFixture<unknown>, id: string) => el(f).querySelector(`#${id}`) as HTMLInputElement | HTMLSelectElement;
  function escribir(f: ComponentFixture<unknown>, id: string, valor: string) {
    const c = campo(f, id);
    c.value = valor;
    c.dispatchEvent(new Event(c instanceof HTMLSelectElement ? 'change' : 'input'));
    f.detectChanges();
  }
  function enviar(f: ComponentFixture<unknown>) {
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }
  function completarLoMinimo(f: ComponentFixture<unknown>) {
    escribir(f, 'nombre', 'Salsipuedes');
    escribir(f, 'provincia', 'CORDOBA');
    escribir(f, 'adminNombre', ' Sergio Vera ');
    escribir(f, 'adminCorreo', ' Sergio.Vera@Salsipuedes.gob.ar ');
  }
  const boton = (f: ComponentFixture<unknown>, texto: string) =>
    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes(texto));

  it('ofreceLas24Jurisdicciones', () => {
    const opciones = (campo(crear(), 'provincia') as HTMLSelectElement).options;
    // 24 jurisdicciones más la opción vacía "Elegí la provincia"
    expect(opciones.length).toBe(25);
    expect(Array.from(opciones).map((o) => o.value)).toContain('TIERRA_DEL_FUEGO');
  });

  it('elSlugSeSugiereAlEscribirNombreYProvincia', () => {
    const f = crear();
    escribir(f, 'nombre', 'Salsipuedes');
    expect(campo(f, 'slug').value).toBe('');
    escribir(f, 'provincia', 'CORDOBA');
    expect(campo(f, 'slug').value).toBe('salsipuedes-cordoba');
    escribir(f, 'nombre', 'Villa María');
    expect(campo(f, 'slug').value).toBe('villa-maria-cordoba');
  });

  it('unEnlaceEditadoAManoNoSeVuelveAPisar', () => {
    const f = crear();
    escribir(f, 'nombre', 'Salsipuedes');
    escribir(f, 'provincia', 'CORDOBA');
    escribir(f, 'slug', 'mi-enlace');
    escribir(f, 'nombre', 'Otro nombre');
    expect(campo(f, 'slug').value).toBe('mi-enlace');
  });

  it('unFormularioIncompletoNoSeEnviaYExplicaQueFalta', () => {
    const f = crear();
    enviar(f);

    http.expectNone(URL);
    const texto = el(f).textContent ?? '';
    expect(texto).toContain('Escribí el nombre de la comuna');
    expect(texto).toContain('Elegí la provincia');
    expect(texto).toContain('Escribí el nombre del administrador');
    expect(texto).toContain('Escribí el correo del administrador');
  });

  it('elWhatsappSoloAceptaNumerosDeDiezAQuinceDigitos', () => {
    const f = crear();
    completarLoMinimo(f);
    escribir(f, 'whatsapp', '351-123');
    enviar(f);

    http.expectNone(URL);
    expect(el(f).textContent).toContain('Solo números, con el código de país');
  });

  it('empiezaConElColorCeleste', () => {
    const f = crear();
    const elegido = el(f).querySelector('input[name="color"]:checked') as HTMLInputElement;
    expect(elegido.value).toBe('CELESTE');
    expect(el(f).querySelectorAll('input[name="color"]').length).toBe(7);
  });

  it('creaLaComunaConLoMinimo', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);

    const pedido = http.expectOne(URL);
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({
      nombre: 'Salsipuedes',
      provincia: 'CORDOBA',
      slug: 'salsipuedes-cordoba',
      color: 'CELESTE',
      adminNombre: 'Sergio Vera',
      adminCorreo: 'sergio.vera@salsipuedes.gob.ar',
    });
    pedido.flush(creada(true), { status: 201, statusText: 'Created' });
  });

  it('enviaLosDatosOpcionalesSoloSiSeCompletaron', () => {
    const f = crear();
    completarLoMinimo(f);
    escribir(f, 'departamento', 'Colón');
    escribir(f, 'telefono', '(0351) 000-0000');
    escribir(f, 'whatsapp', '5493510000000');
    (el(f).querySelector('input[name="color"][value="BOSQUE"]') as HTMLInputElement).click();
    f.detectChanges();
    enviar(f);

    const pedido = http.expectOne(URL);
    expect(pedido.request.body).toMatchObject({ departamento: 'Colón', telefono: '(0351) 000-0000', whatsapp: '5493510000000', color: 'BOSQUE' });
    pedido.flush(creada(true), { status: 201, statusText: 'Created' });
  });

  it('laInvitacionEnviadaSeMuestraSinNingunaClave', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);
    http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
    f.detectChanges();

    const texto = el(f).textContent ?? '';
    expect(texto).toContain('Comuna creada');
    expect(texto).toContain('Invitación enviada a sergio.vera@salsipuedes.gob.ar');
    expect(texto).toContain('72 horas');
    expect(texto).toContain('/salsipuedes-cordoba/login');
    expect(texto.toLowerCase()).not.toContain('clave temporal');
  });

  it('siElCorreoNoSalioOfreceReintentar', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);
    http.expectOne(URL).flush(creada(false), { status: 201, statusText: 'Created' });
    f.detectChanges();

    expect(el(f).textContent).toContain('La comuna se creó, pero el correo no salió');

    boton(f, 'Reintentar')!.click();
    const reenvio = http.expectOne(`${URL}/7/administrador/reenviar-invitacion`);
    expect(reenvio.request.method).toBe('POST');
    reenvio.flush(null, { status: 204, statusText: 'No Content' });
    f.detectChanges();

    expect(el(f).textContent).toContain('Invitación enviada a sergio.vera@salsipuedes.gob.ar');
  });

  it('siElReintentoFallaSeAvisa', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);
    http.expectOne(URL).flush(creada(false), { status: 201, statusText: 'Created' });
    f.detectChanges();

    boton(f, 'Reintentar')!.click();
    http.expectOne(`${URL}/7/administrador/reenviar-invitacion`).error(new ProgressEvent('error'));
    f.detectChanges();

    expect(el(f).textContent).toContain('No pudimos conectarnos');
    expect(el(f).textContent).toContain('el correo no salió');
  });

  it('unEnlaceRepetidoMuestraElErrorYDejaCorregir', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);

    http.expectOne(URL).flush(
      { detail: 'Ya existe una comuna con ese enlace', codigo: 'CONFLICTO' },
      { status: 409, statusText: 'Conflicto' },
    );
    f.detectChanges();

    expect(el(f).textContent).toContain('Ya existe una comuna con ese enlace');
    expect(el(f).querySelector('form')).not.toBeNull();
    expect(campo(f, 'nombre').value).toBe('Salsipuedes');
  });

  describe('logo opcional', () => {
    const URL_OBJETO = {} as {
      crear: MockInstance<(obj: Blob | MediaSource) => string>;
      liberar: MockInstance<(url: string) => void>;
    };
    const png = (bytes = 100, tipo = 'image/png') => new File([new Uint8Array(bytes)], 'escudo.png', { type: tipo });

    function elegirLogo(f: ComponentFixture<unknown>, archivo: File) {
      const input = el(f).querySelector('input[type="file"]') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: [archivo], configurable: true });
      input.dispatchEvent(new Event('change'));
      f.detectChanges();
    }
    const quitar = (f: ComponentFixture<unknown>) => {
      boton(f, 'Quitar logo')!.click();
      f.detectChanges();
    };

    beforeEach(() => {
      // `URL` de este archivo es la ruta de la API: el objeto del navegador se toma de globalThis.
      URL_OBJETO.crear = vi.spyOn(globalThis.URL, 'createObjectURL').mockReturnValue('blob:logo-1');
      URL_OBJETO.liberar = vi.spyOn(globalThis.URL, 'revokeObjectURL').mockImplementation(() => undefined);
    });
    afterEach(() => vi.restoreAllMocks());

    it('ofreceElegirElLogoConAyudaYSinQuitarAntesDeElegir', () => {
      const f = crear();
      const input = el(f).querySelector('input[type="file"]') as HTMLInputElement;
      expect(input.accept).toBe('image/png,image/jpeg');
      expect(boton(f, 'Elegir logo')).toBeTruthy();
      expect(boton(f, 'Quitar logo')).toBeUndefined();
      expect(el(f).textContent).toContain('Cuadrado, PNG o JPG, hasta 512 KB');
    });

    it('alElegirUnLogoMuestraElNombreLaVistaPreviaYElBotonQuitar', () => {
      const f = crear();
      elegirLogo(f, png());

      expect(el(f).textContent).toContain('escudo.png');
      expect(boton(f, 'Quitar logo')).toBeTruthy();
      const img = el(f).querySelector('.vista-previa .logo img') as HTMLImageElement;
      expect(img.getAttribute('src')).toBe('blob:logo-1');
      http.expectNone(URL);
    });

    it('unTipoInvalidoOUnArchivoPesadoSeRechazanSinLlamarAlBackend', () => {
      const f = crear();
      elegirLogo(f, png(100, 'image/gif'));
      expect(el(f).textContent).toContain('El logo tiene que ser una imagen PNG o JPG.');
      expect(el(f).querySelector('.vista-previa .logo')).toBeNull();

      elegirLogo(f, png(512 * 1024 + 1));
      expect(el(f).textContent).toContain('El logo pesa más de 512 KB. Elegí una imagen más liviana.');
      expect(el(f).querySelector('.vista-previa .logo')).toBeNull();
      expect(URL_OBJETO.crear).not.toHaveBeenCalled();
    });

    it('quitarElLogoLiberaLaUrlYVuelveALaVistaPreviaSinLogo', () => {
      const f = crear();
      elegirLogo(f, png());
      quitar(f);

      expect(URL_OBJETO.liberar).toHaveBeenCalledWith('blob:logo-1');
      expect(el(f).querySelector('.vista-previa .logo')).toBeNull();
      expect(el(f).textContent).not.toContain('escudo.png');
    });

    it('cambiarElLogoLiberaLaUrlAnterior', () => {
      const f = crear();
      elegirLogo(f, png());
      URL_OBJETO.crear.mockReturnValue('blob:logo-2');
      elegirLogo(f, png());
      expect(URL_OBJETO.liberar).toHaveBeenCalledWith('blob:logo-1');
    });

    it('alDestruirElComponenteLiberaLaUrl', () => {
      const f = crear();
      elegirLogo(f, png());
      f.destroy();
      expect(URL_OBJETO.liberar).toHaveBeenCalledWith('blob:logo-1');
    });

    it('creaLaComunaYDespuesSubeElLogo', () => {
      const f = crear();
      completarLoMinimo(f);
      const archivo = png();
      elegirLogo(f, archivo);
      enviar(f);

      http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
      const subida = http.expectOne(`${URL}/7/logo`);
      expect(subida.request.method).toBe('PUT');
      expect((subida.request.body as FormData).get('archivo')).toBeInstanceOf(File);
      subida.flush({ ...creada(true).comuna, tieneLogo: true });
      f.detectChanges();

      expect(el(f).textContent).toContain('Comuna creada');
      expect(el(f).textContent).not.toContain('el logo no se pudo subir');
    });

    it('sinLogoNoLlamaALaSubida', () => {
      const f = crear();
      completarLoMinimo(f);
      enviar(f);
      http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
      http.expectNone(`${URL}/7/logo`);
    });

    it('siLaSubidaFallaLaComunaQuedaCreadaYSeAvisaQueSeSubeDesdeLaLista', () => {
      const f = crear();
      completarLoMinimo(f);
      elegirLogo(f, png());
      enviar(f);

      http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
      http.expectOne(`${URL}/7/logo`).flush({ detail: 'Archivo inválido' }, { status: 400, statusText: 'Bad Request' });
      f.detectChanges();

      const texto = el(f).textContent ?? '';
      expect(texto).toContain('Comuna creada');
      expect(texto).toContain('el logo no se pudo subir');
      // Una comuna sin logo muestra "Subir logo" en la lista, no "Cambiar logo".
      expect(texto).toContain('con el botón "Subir logo"');
      expect(texto).not.toContain('Cambiar logo');
      expect(el(f).querySelector('form')).toBeNull();
      http.expectNone(URL);
    });

    it('noEnviaDosVecesMientrasSeSubeElLogo', () => {
      const f = crear();
      completarLoMinimo(f);
      elegirLogo(f, png());
      enviar(f);
      http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
      enviar(f);
      http.expectOne(`${URL}/7/logo`);
      http.expectNone(URL);
    });
  });

  it('noEnviaDosVecesMientrasEspera', () => {
    const f = crear();
    completarLoMinimo(f);
    enviar(f);
    enviar(f);

    http.expectOne(URL).flush(creada(true), { status: 201, statusText: 'Created' });
  });
});
