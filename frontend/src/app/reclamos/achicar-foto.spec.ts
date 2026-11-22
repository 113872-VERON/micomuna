import { CALIDAD_JPEG, LADO_MAXIMO, achicarFoto, medidasAchicadas } from './achicar-foto';

describe('medidasAchicadas', () => {
  it('dejaElLadoMasLargoEn1600YMantieneLaProporcion', () => {
    expect(medidasAchicadas(4000, 3000)).toEqual({ ancho: 1600, alto: 1200 });
    expect(medidasAchicadas(3000, 4000)).toEqual({ ancho: 1200, alto: 1600 });
    expect(medidasAchicadas(4032, 1816)).toEqual({ ancho: 1600, alto: 721 });
  });

  it('nuncaAgrandaUnaFotoChica', () => {
    expect(medidasAchicadas(800, 600)).toEqual({ ancho: 800, alto: 600 });
    expect(medidasAchicadas(1600, 1600)).toEqual({ ancho: 1600, alto: 1600 });
  });

  it('unaPanoramicaMuyAngostaNoQuedaEnCero', () => {
    expect(medidasAchicadas(20000, 4)).toEqual({ ancho: 1600, alto: 1 });
  });

  it('unaImagenSinTamanoNoSePuedeUsar', () => {
    expect(() => medidasAchicadas(0, 300)).toThrow();
    expect(() => medidasAchicadas(Number.NaN, 300)).toThrow();
  });
});

// jsdom no tiene canvas ni createImageBitmap: se simulan para ver qué se le pide al navegador.
describe('achicarFoto', () => {
  let dibujar: ReturnType<typeof vi.fn>;
  let toBlob: ReturnType<typeof vi.spyOn>;
  let cerrar: ReturnType<typeof vi.fn>;
  let lienzo: HTMLCanvasElement | null;

  beforeEach(() => {
    dibujar = vi.fn();
    cerrar = vi.fn();
    lienzo = null;
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
      lienzo = this;
      return { fillRect: vi.fn(), drawImage: dibujar, fillStyle: '', imageSmoothingQuality: 'low' } as unknown as CanvasRenderingContext2D;
    } as unknown as HTMLCanvasElement['getContext']);
    toBlob = vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((listo: BlobCallback) =>
      listo(new Blob(['jpeg'], { type: 'image/jpeg' })),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const foto = () => new File([new Uint8Array(10)], 'IMG_0001.jpg', { type: 'image/jpeg' });

  it('achicaA1600ComoJpegAl80YRespetaLaOrientacion', async () => {
    const mapa = { width: 4000, height: 3000, close: cerrar };
    const leer = vi.fn().mockResolvedValue(mapa);
    vi.stubGlobal('createImageBitmap', leer);

    const resultado = await achicarFoto(foto());

    expect(leer).toHaveBeenCalledWith(expect.any(File), { imageOrientation: 'from-image' });
    expect(lienzo?.width).toBe(LADO_MAXIMO);
    expect(lienzo?.height).toBe(1200);
    expect(dibujar).toHaveBeenCalledWith(mapa, 0, 0, 1600, 1200);
    expect(toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', CALIDAD_JPEG);
    expect(resultado.type).toBe('image/jpeg');
    // La imagen leída se libera aunque todo salga bien.
    expect(cerrar).toHaveBeenCalled();
  });

  it('unArchivoQueNoEsImagenSeRechazaSinLeerlo', async () => {
    const leer = vi.fn();
    vi.stubGlobal('createImageBitmap', leer);

    await expect(achicarFoto(new File(['hola'], 'nota.pdf', { type: 'application/pdf' }))).rejects.toThrow();
    expect(leer).not.toHaveBeenCalled();
  });

  it('siElNavegadorNoPuedeArmarElJpegFallaYLiberaLaImagen', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 100, height: 100, close: cerrar }));
    toBlob.mockImplementation((listo: BlobCallback) => listo(null));

    await expect(achicarFoto(foto())).rejects.toThrow();
    expect(cerrar).toHaveBeenCalled();
  });

  it('unaImagenSinTamanoFalla', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ width: 0, height: 0, close: cerrar }));

    await expect(achicarFoto(foto())).rejects.toThrow();
    expect(toBlob).not.toHaveBeenCalled();
  });
});
