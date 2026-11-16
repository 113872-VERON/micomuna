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
