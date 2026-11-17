package com.micomuna.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

// Reconoce imágenes PNG y JPEG por su contenido, nunca por el nombre ni por el tipo que declara el cliente.
final class ImagenUtil {

	static final String PNG = "image/png";
	static final String JPEG = "image/jpeg";

	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private ImagenUtil() {
	}

	// Devuelve "image/png" o "image/jpeg" si el archivo es una imagen de ese tipo que se puede abrir
	// y no supera el lado máximo en píxeles; si no, null.
	static String tipoSiEsValida(byte[] bytes, int ladoMaximo) {
		String tipo = tipoPorFirma(bytes);
		if (tipo == null || !seDecodifica(bytes, PNG.equals(tipo) ? "png" : "jpeg", ladoMaximo)) {
			return null;
		}
		return tipo;
	}

	private static String tipoPorFirma(byte[] b) {
		if (b.length >= FIRMA_PNG.length && Arrays.equals(b, 0, FIRMA_PNG.length, FIRMA_PNG, 0, FIRMA_PNG.length)) {
			return PNG;
		}
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return JPEG;
		}
		return null;
	}

	// Primero se leen las medidas de la cabecera y recién después se abre la imagen completa:
	// así un archivo chico que declara medidas enormes no llega a ocupar memoria.
	private static boolean seDecodifica(byte[] bytes, String formato, int ladoMaximo) {
		Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato);
		if (!lectores.hasNext()) {
			return false;
		}
		ImageReader lector = lectores.next();
		try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			lector.setInput(entrada, true, true);
			int ancho = lector.getWidth(0);
			int alto = lector.getHeight(0);
			if (ancho < 1 || alto < 1 || ancho > ladoMaximo || alto > ladoMaximo) {
				return false;
			}
			return lector.read(0) != null;
		} catch (IOException | RuntimeException e) {
			// Los lectores de ImageIO fallan de varias formas con archivos rotos; cualquiera significa "no es una imagen".
			return false;
		} finally {
			lector.dispose();
		}
	}
}
