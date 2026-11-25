import { TestBed } from '@angular/core/testing';
import { PuntoReclamoDto } from '../../../reclamos/modelos-panel';
import { MapaReclamos } from './mapa-reclamos';

const PUNTOS: PuntoReclamoDto[] = [
  { numero: 12, estado: 'RECIBIDO', tipo: 'BACHE', latitud: -31.1, longitud: -64.3 },
  { numero: 1234, estado: 'RESUELTO', tipo: 'LUZ', latitud: -31.2, longitud: -64.4 },
];

describe('MapaReclamos (con Leaflet de verdad)', () => {
  afterEach(() => vi.useRealTimers());

  async function crear(puntos: readonly PuntoReclamoDto[] = PUNTOS) {
    const f = TestBed.createComponent(MapaReclamos);
    f.componentRef.setInput('puntos', puntos);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  const pines = (f: { nativeElement: unknown }) => Array.from((f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.pin-reclamo'));

  it('dibujaUnPinPorReclamoConSuNumeroYElColorDeSuEstado', async () => {
    const f = await crear();

    const lista = pines(f);
    expect(lista.length).toBe(2);
    expect(lista[0].textContent).toContain('12');
    expect(lista[0].querySelector('path')?.getAttribute('fill')).toBe('#52606D');
    expect(lista[1].textContent).toContain('1234');
    expect(lista[1].querySelector('path')?.getAttribute('fill')).toBe('#14532D');
    f.destroy();
  });

  it('cadaPinTieneNombreAccesibleConElEstadoEnPalabras', async () => {
    const f = await crear();

    expect(pines(f).map((p) => p.getAttribute('title'))).toEqual(['Reclamo n.º 12, Recibido', 'Reclamo n.º 1234, Resuelto']);
    f.destroy();
  });

  it('tocarUnPinAvisaCualReclamoEs', async () => {
    const f = await crear();
    const elegidos: number[] = [];
    f.componentInstance.elegido.subscribe((n) => elegidos.push(n));

    pines(f)[1].click();

    expect(elegidos).toEqual([1234]);
    f.destroy();
  });

  it('alCambiarLosPuntosSeRedibujan', async () => {
    const f = await crear();

    f.componentRef.setInput('puntos', [PUNTOS[0]]);
    f.detectChanges();

    expect(pines(f).length).toBe(1);
    f.destroy();
  });

  it('elReclamoResaltadoSeMarca', async () => {
    const f = await crear();

    f.componentRef.setInput('resaltado', 12);
    f.detectChanges();

    expect(pines(f).map((p) => p.classList.contains('resaltado'))).toEqual([true, false]);
    f.destroy();
  });

  it('sinPuntosNoDibujaNadaYNoFalla', async () => {
    const f = await crear([]);

    expect(pines(f).length).toBe(0);
    f.destroy();
  });

  describe('mascara de fuera de la comuna', () => {
    const cuadrado = (d: number) => ({ type: 'Polygon', coordinates: [[[-64.3 - d, -31.1 - d], [-64.3 + d, -31.1 - d], [-64.3 + d, -31.1 + d], [-64.3 - d, -31.1 + d], [-64.3 - d, -31.1 - d]]] });
    const mascaras = (f: { nativeElement: unknown }) => (f.nativeElement as HTMLElement).querySelectorAll('path.mascara-fuera');

    it('conLimiteDibujaLaMascaraYLaLineaPunteada', async () => {
      const f = await crear();
      f.componentRef.setInput('limite', cuadrado(0.1));
      f.detectChanges();

      expect(mascaras(f).length).toBe(1);
      expect((f.nativeElement as HTMLElement).querySelectorAll('path[stroke-dasharray]').length).toBe(1);
      f.destroy();
    });

    it('sinLimiteNoDibujaMascara', async () => {
      const f = await crear();

      expect(mascaras(f).length).toBe(0);
      f.destroy();
    });

    it('alCambiarElLimiteReemplazaLaMascaraYNoLaDuplica', async () => {
      const f = await crear();
      f.componentRef.setInput('limite', cuadrado(0.1));
      f.detectChanges();
      f.componentRef.setInput('limite', cuadrado(0.2));
      f.detectChanges();
      expect(mascaras(f).length).toBe(1);

      f.componentRef.setInput('limite', null);
      f.detectChanges();
      expect(mascaras(f).length).toBe(0);
      f.destroy();
    });
  });

  it('unPuntoConCoordenadasRarasSeSaltea', async () => {
    const f = await crear([...PUNTOS, { numero: 5, estado: 'RECIBIDO', tipo: 'OTRO', latitud: NaN, longitud: 0 }]);

    expect(pines(f).length).toBe(2);
    f.destroy();
  });
});
