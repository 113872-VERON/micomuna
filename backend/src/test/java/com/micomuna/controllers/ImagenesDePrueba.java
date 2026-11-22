package com.micomuna.controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// Imágenes de verdad, armadas en memoria: el logo y las fotos se abren con ImageIO, así que no alcanza con bytes
// que solo tengan la firma.
final class ImagenesDePrueba {

	static final Color ROJO = new Color(0xE0, 0x10, 0x10);
	static final Color AZUL = new Color(0x10, 0x10, 0xE0);

	private ImagenesDePrueba() {
	}

	static byte[] png(int ancho, int alto) {
		return imagen("png", ancho, alto);
	}

	static byte[] jpeg(int ancho, int alto) {
		return imagen("jpeg", ancho, alto);
	}

	static byte[] gif(int ancho, int alto) {
		return imagen("gif", ancho, alto);
	}

	// PNG con canal de transparencia (como un recorte o una captura de pantalla).
	static byte[] pngConTransparencia(int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
		Graphics2D dibujo = imagen.createGraphics();
		dibujo.setColor(new Color(0x2E, 0x86, 0xC1, 0x80));
		dibujo.fillRect(0, 0, ancho / 2, alto);
		dibujo.dispose();
		return escribir(imagen, "png");
	}

	// Mitad izquierda roja y mitad derecha azul: sirve para ver hacia dónde quedó girada una foto.
	static byte[] jpegDosColores(int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D dibujo = imagen.createGraphics();
		dibujo.setColor(ROJO);
		dibujo.fillRect(0, 0, ancho / 2, alto);
		dibujo.setColor(AZUL);
		dibujo.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
		dibujo.dispose();
		return escribir(imagen, "jpeg");
	}

	// Un JPEG como los de una cámara: con un segmento APP1 "Exif" justo después de la firma (SOI), que lleva
	// la orientación (etiqueta 0x0112) y un texto (etiqueta 0x010E, ImageDescription) que hace de dato oculto.
	static byte[] conExif(byte[] jpeg, int orientacion, String textoOculto) {
		byte[] texto = (textoOculto + "\0").getBytes(StandardCharsets.US_ASCII);
		int entradas = 2;
		int inicioTexto = 8 + 2 + entradas * 12 + 4;
		ByteBuffer tiff = ByteBuffer.allocate(inicioTexto + texto.length); // orden "MM" (big endian), el de Java
		tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
		tiff.putShort((short) entradas);
		tiff.putShort((short) 0x010E).putShort((short) 2).putInt(texto.length).putInt(inicioTexto);
		tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacion).putShort((short) 0);
		tiff.putInt(0); // no hay otra lista de etiquetas
		tiff.put(texto);

		byte[] cabecera = { 'E', 'x', 'i', 'f', 0, 0 };
		int largo = 2 + cabecera.length + tiff.capacity();
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.write(jpeg, 0, 2); // SOI
		salida.write(0xFF);
		salida.write(0xE1);
		salida.write(largo >> 8);
		salida.write(largo & 0xFF);
		salida.writeBytes(cabecera);
		salida.writeBytes(tiff.array());
		salida.write(jpeg, 2, jpeg.length - 2);
		return salida.toByteArray();
	}

	// Un PNG con un bloque de texto (tEXt) después de la cabecera IHDR, donde algunos programas guardan datos.
	static byte[] conTextoPng(byte[] png, String clave, String texto) {
		byte[] datos = (clave + "\0" + texto).getBytes(StandardCharsets.ISO_8859_1);
		byte[] tipo = "tEXt".getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(tipo);
		crc.update(datos);
		int finIhdr = 8 + 4 + 4 + 13 + 4;
		ByteBuffer salida = ByteBuffer.allocate(png.length + 12 + datos.length);
		salida.put(png, 0, finIhdr);
		salida.putInt(datos.length).put(tipo).put(datos).putInt((int) crc.getValue());
		salida.put(png, finIhdr, png.length - finIhdr);
		return salida.array();
	}

	// PNG de 16 bits por canal con transparencia: 8 bytes por píxel al abrirlo. Toda transparente, así el archivo
	// pesa poco aunque las medidas sean grandes.
	static byte[] png16ConTransparencia(int ancho, int alto) {
		ColorModel modelo = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false,
				Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT);
		BufferedImage imagen = new BufferedImage(modelo, modelo.createCompatibleWritableRaster(ancho, alto), false, null);
		return escribir(imagen, "png");
	}

	// JPEG progresivo armado por ImageIO (unas 10 pasadas), como el de muchas cámaras y programas.
	static byte[] jpegProgresivo(int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D dibujo = imagen.createGraphics();
		dibujo.setColor(ROJO);
		dibujo.fillRect(0, 0, ancho / 2, alto);
		dibujo.dispose();
		ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam parametros = escritor.getDefaultWriteParam();
		parametros.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
		return escribir(escritor, imagen, parametros);
	}

	// Repite la última pasada de un JPEG progresivo (encabezado SOS y sus datos comprimidos) antes del final.
	// En los datos comprimidos un FF siempre va seguido de 00, así que el último FF DA es el último marcador SOS.
	static byte[] conPasadasRepetidas(byte[] jpeg, int veces) {
		int finSinEoi = jpeg.length - 2;
		int ultimaPasada = -1;
		for (int i = finSinEoi - 2; i >= 2 && ultimaPasada < 0; i--) {
			if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xDA) {
				ultimaPasada = i;
			}
		}
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		salida.write(jpeg, 0, finSinEoi);
		for (int k = 0; k < veces; k++) {
			salida.write(jpeg, ultimaPasada, finSinEoi - ultimaPasada);
		}
		salida.write(0xFF);
		salida.write(0xD9);
		return salida.toByteArray();
	}

	// Ruido de colores guardado con poca calidad: el archivo es liviano, pero al volver a armarlo con buena calidad
	// (como hace el servidor) crece mucho, porque el ruido no se comprime.
	static byte[] jpegRuidoConPocaCalidad(int ancho, int alto, float calidad) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_3BYTE_BGR);
		new Random(15).nextBytes(((DataBufferByte) imagen.getRaster().getDataBuffer()).getData());
		ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam parametros = escritor.getDefaultWriteParam();
		parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		parametros.setCompressionQuality(calidad);
		return escribir(escritor, imagen, parametros);
	}

	private static byte[] escribir(ImageWriter escritor, BufferedImage imagen, ImageWriteParam parametros) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try (ImageOutputStream destino = new MemoryCacheImageOutputStream(salida)) {
			escritor.setOutput(destino);
			escritor.write(null, new IIOImage(imagen, null, null), parametros);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			escritor.dispose();
		}
		return salida.toByteArray();
	}

	static int pasadas(byte[] jpeg) {
		int cantidad = 0;
		for (int i = 0; i + 1 < jpeg.length; i++) {
			if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xDA) {
				cantidad++;
			}
		}
		return cantidad;
	}

	static BufferedImage leer(byte[] bytes) {
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static boolean contiene(byte[] bytes, String texto) {
		return new String(bytes, StandardCharsets.ISO_8859_1).contains(texto);
	}

	private static byte[] imagen(String formato, int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D dibujo = imagen.createGraphics();
		dibujo.setColor(new Color(0x2E, 0x86, 0xC1));
		dibujo.fillRect(0, 0, ancho, alto);
		dibujo.dispose();
		return escribir(imagen, formato);
	}

	private static byte[] escribir(BufferedImage imagen, String formato) {
		try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			if (!ImageIO.write(imagen, formato, salida)) {
				throw new IllegalStateException("No hay escritor de imágenes para " + formato);
			}
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
