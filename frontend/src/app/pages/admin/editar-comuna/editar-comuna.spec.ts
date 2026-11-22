import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AvisoPanelService } from '../../../admin/aviso-panel.service';
import { ComunaAdminDto } from '../../../auth/modelos';
import { EditarComuna } from './editar-comuna';

const URL = '/api/admin/comunas';
const agua: ComunaAdminDto = {
  id: 5, nombre: 'Agua de Oro', slug: 'agua-de-oro-cordoba', provincia: 'Córdoba', departamento: 'Colón', color: 'CELESTE',
  activa: true, tieneLogo: false, telefono: '5493543123456', whatsapp: '5493543654321',
};
const otra: ComunaAdminDto = { ...agua, id: 6, nombre: 'Otra', slug: 'otra-cordoba' };

describe('EditarComuna', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditarComuna],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function crear(lista: ComunaAdminDto[] = [otra, agua], id = '5'): ComponentFixture<EditarComuna> {
    const fixture = TestBed.createComponent(EditarComuna);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    http.expectOne(URL).flush(lista);
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: ComponentFixture<unknown>) => f.nativeElement as HTMLElement;
  const campo = (f: ComponentFixture<unknown>, id: string) => el(f).querySelector(`#${id}`) as HTMLInputElement;
  function escribir(f: ComponentFixture<unknown>, id: string, valor: string) {
    const c = campo(f, id);
    c.value = valor;
    c.dispatchEvent(new Event('input'));
    f.detectChanges();
  }
  function guardar(f: ComponentFixture<unknown>) {
    el(f).querySelector('form')!.dispatchEvent(new Event('submit'));
    f.detectChanges();
  }

  it('cargaLosDatosDeLaListaYMuestraEnlaceYProvinciaSinPoderEditarlos', () => {
    const f = crear();

    expect(campo(f, 'nombre').value).toBe('Agua de Oro');
    expect(campo(f, 'departamento').value).toBe('Colón');
    expect(campo(f, 'telefono').value).toBe('5493543123456');
    expect(campo(f, 'whatsapp').value).toBe('5493543654321');
    expect((el(f).querySelector('input[name="color"]:checked') as HTMLInputElement).value).toBe('CELESTE');
    expect(campo(f, 'slug').value).toBe('agua-de-oro-cordoba');
    expect(campo(f, 'slug').readOnly).toBe(true);
    expect(campo(f, 'provincia').value).toBe('Córdoba');
    expect(campo(f, 'provincia').readOnly).toBe(true);
    expect(el(f).textContent).toContain('El enlace y la provincia no se pueden cambiar');
  });

  it('muestraLaVistaPreviaYLaActualizaAlEscribir', () => {
    const f = crear();
    expect(el(f).textContent).toContain('Así lo van a ver los vecinos');
    expect(el(f).querySelector('app-cabecera-comuna')?.textContent).toContain('Agua de Oro');

    escribir(f, 'nombre', 'Agua de Oro Norte');
    expect(el(f).querySelector('app-cabecera-comuna')?.textContent).toContain('Agua de Oro Norte');
  });

  it('guardaSoloLoQueCambio', () => {
    const f = crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    escribir(f, 'nombre', '  Agua de Oro Sur ');
    (el(f).querySelector('input[name="color"][value="BOSQUE"]') as HTMLInputElement).click();
    f.detectChanges();
    guardar(f);

    const pedido = http.expectOne(`${URL}/5`);
    expect(pedido.request.method).toBe('PATCH');
    expect(pedido.request.body).toEqual({ nombre: 'Agua de Oro Sur', color: 'BOSQUE' });
    pedido.flush({ ...agua, nombre: 'Agua de Oro Sur', color: 'BOSQUE' });

    expect(navegar).toHaveBeenCalledWith(['/admin/comunas']);
    expect(TestBed.inject(AvisoPanelService).consumir()).toBe('Guardamos los cambios de Agua de Oro Sur');
  });

  it('unTelefonoConEspaciosOSignoSeMandaSoloConDigitos', () => {
    const f = crear();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    escribir(f, 'telefono', '+54 9 3543-111222');
    guardar(f);

    const pedido = http.expectOne(`${URL}/5`);
    expect(pedido.request.body).toEqual({ telefono: '5493543111222' });
    pedido.flush(agua);
  });

  it('vaciarTelefonoYWhatsappMandaTextoVacioParaBorrarlos', () => {
    const f = crear();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    escribir(f, 'telefono', '');
    escribir(f, 'whatsapp', '');
    guardar(f);

    const pedido = http.expectOne(`${URL}/5`);
    expect(pedido.request.body).toEqual({ telefono: '', whatsapp: '' });
    pedido.flush({ ...agua, telefono: null, whatsapp: null });
  });

  it('sinCambiosNoLlamaAlServidorYLoAvisa', () => {
    const f = crear();
    guardar(f);

    http.expectNone(`${URL}/5`);
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No hay cambios para guardar');
  });

  it('unTelefonoInvalidoNoSeEnviaYMuestraElMensaje', () => {
    const f = crear();
    escribir(f, 'telefono', '1234');
    guardar(f);

    http.expectNone(`${URL}/5`);
    expect(el(f).textContent).toContain('Escribí el número con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456).');
  });

  it('laAyudaDelContactoDaElEjemploConCodigoDePais', () => {
    expect(el(crear()).textContent).toContain('Con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456)');
  });

  it('unNombreVacioNoSeEnvia', () => {
    const f = crear();
    escribir(f, 'nombre', '   ');
    guardar(f);

    http.expectNone(`${URL}/5`);
    expect(el(f).textContent).toContain('Escribí el nombre de la comuna');
  });

  it('muestraElErrorDelServidorYSigueEnLaPantalla', () => {
    const f = crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    escribir(f, 'nombre', 'Otra');
    guardar(f);

    http.expectOne(`${URL}/5`).flush(
      { status: 409, detail: 'Ya existe una comuna con ese nombre en la provincia.' },
      { status: 409, statusText: 'Conflict' },
    );
    f.detectChanges();

    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('Ya existe una comuna con ese nombre en la provincia.');
    expect(navegar).not.toHaveBeenCalled();
    expect(el(f).querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });

  it('cancelarVuelveALaListaSinGuardar', () => {
    const f = crear();
    const cancelar = Array.from(el(f).querySelectorAll('a')).find((a) => a.textContent?.includes('Cancelar'));

    expect(cancelar?.getAttribute('href')).toBe('/admin/comunas');
    http.expectNone(`${URL}/5`);
  });

  it('siLaComunaNoEstaEnLaListaLoAvisa', () => {
    const f = crear([otra], '99');
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No encontramos esa comuna');
    expect(el(f).querySelector('form')).toBeNull();
  });

  it('siNoSePuedeCargarLaListaOfreceReintentar', () => {
    const f = TestBed.createComponent(EditarComuna);
    f.componentRef.setInput('id', '5');
    f.detectChanges();
    http.expectOne(URL).error(new ProgressEvent('error'));
    f.detectChanges();
    expect(el(f).textContent).toContain('No pudimos cargar la comuna');

    Array.from(el(f).querySelectorAll('button')).find((b) => b.textContent?.includes('Reintentar'))!.click();
    http.expectOne(URL).flush([agua]);
    f.detectChanges();
    expect(campo(f, 'nombre').value).toBe('Agua de Oro');
  });
});
