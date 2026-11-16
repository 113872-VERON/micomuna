import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RedirectCommand, RouterStateSnapshot, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ComunaDto } from './modelos';
import { comunaResolver } from './comuna.resolver';

const comuna: ComunaDto = {
  nombre: 'Salsipuedes',
  provincia: 'Córdoba',
  departamento: 'Colón',
  slug: 'salsipuedes-cordoba',
  tieneLogo: false,
  color: 'CELESTE',
};

function resolver(slug: string): Promise<ComunaDto | RedirectCommand> {
  const ruta = { paramMap: convertToParamMap({ slug }) } as ActivatedRouteSnapshot;
  const resultado = TestBed.runInInjectionContext(() => comunaResolver(ruta, {} as RouterStateSnapshot));
  return firstValueFrom(resultado as Observable<ComunaDto | RedirectCommand>);
}

describe('comunaResolver', () => {
  let controlador: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    controlador = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controlador.verify());

  it('devuelveLaComunaSiExiste', async () => {
    const resultado = resolver('salsipuedes-cordoba');
    controlador.expectOne('/api/comunas/salsipuedes-cordoba').flush(comuna);
    expect(await resultado).toEqual(comuna);
  });

  it('elResolverRedirigeSiLaComunaNoExiste', async () => {
    const resultado = resolver('no-existe');
    controlador.expectOne('/api/comunas/no-existe').flush(null, { status: 404, statusText: 'No encontrada' });
    const r = await resultado;
    expect(r).toBeInstanceOf(RedirectCommand);
    expect((r as RedirectCommand).redirectTo.toString()).toBe('/no-encontrada');
  });

  it('codificaElSlugEnLaURL', async () => {
    const resultado = resolver('a/b');
    controlador.expectOne('/api/comunas/a%2Fb').flush(null, { status: 404, statusText: 'No encontrada' });
    await resultado;
  });
});
