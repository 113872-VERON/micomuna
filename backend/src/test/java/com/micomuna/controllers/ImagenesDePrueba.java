package com.micomuna.controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

// Imágenes de verdad, armadas en memoria: el logo se abre con ImageIO, así que no alcanza con bytes que solo tengan la firma.
final class ImagenesDePrueba {

	private ImagenesDePrueba() {
	}

	static byte[] png(int ancho, int alto) {
		return imagen("png", ancho, alto);
	}

	static byte[] jpeg(int ancho, int alto) {
		return imagen("jpeg", ancho, alto);
	}

	private static byte[] imagen(String formato, int ancho, int alto) {
		BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
		Graphics2D dibujo = imagen.createGraphics();
		dibujo.setColor(new Color(0x2E, 0x86, 0xC1));
		dibujo.fillRect(0, 0, ancho, alto);
		dibujo.dispose();
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
