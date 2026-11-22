package com.micomuna.services;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// Reconoce imágenes PNG y JPEG por su contenido, nunca por el nombre ni por el tipo que declara el cliente.
final class ImagenUtil {

	static final String PNG = "image/png";
	static final String JPEG = "image/jpeg";

	// Calidad con la que se vuelve a armar un JPEG: casi no se nota la diferencia y no agranda el archivo.
	private static final float CALIDAD_JPEG = 0.9f;

	// Tope de memoria que puede ocupar una imagen abierta, contando la copia que se hace para girarla según el EXIF.
	// 40 MiB alcanza justo para el caso legítimo más grande de una foto de reclamo: un JPEG de 2560 x 2560 que hay
	// que girar (2560 x 2560 x 3 bytes = 18,75 MiB, por dos con la copia = 37,5 MiB). Lo que lo supera es una imagen
	// rara para una foto (por ejemplo un PNG de 16 bits con transparencia, que ocupa 8 bytes por píxel).
	// Con 3 fotos procesándose a la vez (TurnosDeFotos) son como mucho unos 120 MiB de píxeles en memoria.
	static final long MEMORIA_MAXIMA_BYTES = 40L * 1024 * 1024;

	// Un JPEG común tiene 1 pasada (o 3); uno progresivo de cámara o de ImageIO, alrededor de 10.
	// 30 deja margen para cualquier programa real. Con 100, una foto armada tardaba unas 5 veces más en abrirse
	// (medido: 2 s contra 0,4 s en 1600x1200) y alcanzaba para tener ocupado el procesador.
	static final int PASADAS_MAXIMAS = 30;

	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	// Una imagen ya limpia, lista para guardar, con su tipo decidido por el contenido.
	record Imagen(String tipo, byte[] bytes) {
	}

	private ImagenUtil() {
	}

	// Devuelve "image/png" o "image/jpeg" si el archivo es una imagen de ese tipo que se puede abrir
	// y no supera el lado máximo en píxeles; si no, null.
	static String tipoSiEsValida(byte[] bytes, int ladoMaximo) {
		String tipo = tipoPorFirma(bytes);
		if (tipo == null || decodificar(bytes, tipo, ladoMaximo, OrientacionExif.SIN_GIRAR) == null) {
			return null;
		}
		return tipo;
	}

	// Valida la imagen igual que tipoSiEsValida y además la vuelve a armar desde sus píxeles, en el mismo formato.
	// Lo que no es la imagen en sí queda afuera: los datos EXIF (por ejemplo, la ubicación GPS de la cámara,
	// el modelo del celular o la fecha), las miniaturas, los comentarios y los bloques de texto de un PNG.
	// Como la orientación de la cámara también viaja en el EXIF, en un JPEG se aplica antes de descartarla:
	// si no, una foto sacada con el celular parado se vería acostada.
	// Devuelve null si el archivo no es una imagen válida o no se pudo volver a armar.
	static Imagen limpiar(byte[] bytes, int ladoMaximo) {
		String tipo = tipoPorFirma(bytes);
		if (tipo == null) {
			return null;
		}
		int orientacion = JPEG.equals(tipo) ? OrientacionExif.leer(bytes) : OrientacionExif.SIN_GIRAR;
		BufferedImage imagen = decodificar(bytes, tipo, ladoMaximo, orientacion);
		if (imagen == null) {
			return null;
		}
		imagen = orientar(imagen, orientacion);
		byte[] limpia = codificar(imagen, tipo);
		return limpia == null ? null : new Imagen(tipo, limpia);
	}

	private static String tipoPorFirma(byte[] b) {
		if (b == null) {
			return null;
		}
		if (b.length >= FIRMA_PNG.length && Arrays.equals(b, 0, FIRMA_PNG.length, FIRMA_PNG, 0, FIRMA_PNG.length)) {
			return PNG;
		}
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return JPEG;
		}
		return null;
	}

	private static String formato(String tipo) {
		return PNG.equals(tipo) ? "png" : "jpeg";
	}

	// Antes de abrir la imagen completa se revisa lo que se puede saber sin abrirla: la cantidad de pasadas de un JPEG,
	// las medidas de la cabecera y cuánta memoria ocuparía abierta. Así un archivo chico que declara medidas enormes,
	// muchos bytes por píxel o miles de pasadas no llega a ocupar memoria ni procesador.
	// Devuelve null si no se puede abrir, si supera el lado máximo, las pasadas o la memoria.
	private static BufferedImage decodificar(byte[] bytes, String tipo, int ladoMaximo, int orientacion) {
		if (JPEG.equals(tipo) && PasadasJpeg.contar(bytes, PASADAS_MAXIMAS) > PASADAS_MAXIMAS) {
			return null;
		}
		Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato(tipo));
		if (!lectores.hasNext()) {
			return null;
		}
		ImageReader lector = lectores.next();
		// Un JPEG cortado no hace fallar al lector: avisa y rellena con gris lo que falta. Ese aviso ("premature end")
		// se toma como imagen rota. Otros avisos (por ejemplo, que falta solo el marcador final) no pierden píxeles.
		boolean[] incompleta = { false };
		lector.addIIOReadWarningListener((origen, aviso) -> {
			if (aviso != null && aviso.toLowerCase(Locale.ROOT).contains("premature end")) {
				incompleta[0] = true;
			}
		});
		try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			// ignoreMetadata = true: los metadatos del archivo ni se leen.
			lector.setInput(entrada, true, true);
			int ancho = lector.getWidth(0);
			int alto = lector.getHeight(0);
			if (ancho < 1 || alto < 1 || ancho > ladoMaximo || alto > ladoMaximo) {
				return null;
			}
			if (memoriaAlAbrir(lector, ancho, alto, orientacion) > MEMORIA_MAXIMA_BYTES) {
				return null;
			}
			BufferedImage imagen = lector.read(0);
			return incompleta[0] ? null : imagen;
		} catch (IOException | RuntimeException e) {
			// Los lectores de ImageIO fallan de varias formas con archivos rotos; cualquiera significa "no es una imagen".
			return null;
		} finally {
			lector.dispose();
		}
	}

	// Bytes que ocuparía la imagen abierta: ancho x alto x lo que ocupa cada píxel según el tipo en que la abre el lector
	// (por ejemplo 3 en un JPEG color, 4 en un PNG con transparencia, 8 en un PNG de 16 bits con transparencia).
	// Si hay que girarla, se suma la copia girada que arma orientar (1 o 3 bytes por píxel).
	// Si el lector no dice en qué tipo la abriría, se rechaza: no hay forma de saber cuánto ocuparía.
	private static long memoriaAlAbrir(ImageReader lector, int ancho, int alto, int orientacion) throws IOException {
		ImageTypeSpecifier tipoAlAbrir = lector.getRawImageType(0);
		if (tipoAlAbrir == null) {
			Iterator<ImageTypeSpecifier> tipos = lector.getImageTypes(0);
			tipoAlAbrir = tipos.hasNext() ? tipos.next() : null;
		}
		if (tipoAlAbrir == null) {
			return Long.MAX_VALUE;
		}
		SampleModel muestras = tipoAlAbrir.getSampleModel();
		long bytesPorPixel = (long) muestras.getNumDataElements() * DataBuffer.getDataTypeSize(muestras.getDataType()) / 8;
		long pixeles = (long) ancho * alto;
		long total = pixeles * Math.max(1, bytesPorPixel);
		if (seGira(orientacion)) {
			total += pixeles * (muestras.getNumBands() == 1 ? 1 : 3);
		}
		return total;
	}

	private static boolean seGira(int orientacion) {
		return orientacion >= 2 && orientacion <= 8;
	}

	// Se escribe la imagen desde cero, sin pasarle metadatos: el archivo nuevo solo lleva los píxeles.
	private static byte[] codificar(BufferedImage imagen, String tipo) {
		Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName(formato(tipo));
		if (!escritores.hasNext()) {
			return null;
		}
		ImageWriter escritor = escritores.next();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ImageOutputStream destino = new MemoryCacheImageOutputStream(salida)) {
			escritor.setOutput(destino);
			ImageWriteParam parametros = escritor.getDefaultWriteParam();
			if (JPEG.equals(tipo)) {
				parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				parametros.setCompressionQuality(CALIDAD_JPEG);
			}
			escritor.write(null, new IIOImage(imagen, null, null), parametros);
		} catch (IOException | RuntimeException e) {
			return null;
		} finally {
			escritor.dispose();
		}
		return salida.toByteArray();
	}

	// Gira o espeja la imagen según la orientación EXIF (1 a 8), para que quede derecha sin depender del EXIF.
	private static BufferedImage orientar(BufferedImage imagen, int orientacion) {
		if (!seGira(orientacion)) {
			return imagen;
		}
		int ancho = imagen.getWidth();
		int alto = imagen.getHeight();
		boolean seAcuesta = orientacion >= 5;
		AffineTransform giro = switch (orientacion) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, ancho, 0); // espejo horizontal
			case 3 -> new AffineTransform(-1, 0, 0, -1, ancho, alto); // 180°
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, alto); // espejo vertical
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // transpuesta
			case 6 -> new AffineTransform(0, 1, -1, 0, alto, 0); // 90° a la derecha
			case 7 -> new AffineTransform(0, -1, -1, 0, alto, ancho); // transversa
			default -> new AffineTransform(0, -1, 1, 0, 0, ancho); // 8: 90° a la izquierda
		};
		int tipoDestino = imagen.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY
				: BufferedImage.TYPE_3BYTE_BGR;
		BufferedImage derecha = new BufferedImage(seAcuesta ? alto : ancho, seAcuesta ? ancho : alto, tipoDestino);
		Graphics2D dibujo = derecha.createGraphics();
		try {
			dibujo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			dibujo.drawImage(imagen, giro, null);
		} finally {
			dibujo.dispose();
		}
		return derecha;
	}
}
