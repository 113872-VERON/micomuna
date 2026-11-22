package com.micomuna.services;

// Lee la orientación de la cámara (etiqueta EXIF 0x0112) de un JPEG, sin abrir la imagen ni leer el resto del EXIF.
// La usa ImagenUtil para dejar la foto derecha antes de descartar los metadatos.
// Ante cualquier cosa rara (EXIF cortado, desplazamientos fuera del archivo) responde 1, "sin girar":
// lo peor que puede pasar es que la foto quede como venía.
final class OrientacionExif {

	static final int SIN_GIRAR = 1;

	private static final int ETIQUETA_ORIENTACION = 0x0112;
	private static final int TIPO_SHORT = 3;
	private static final byte[] CABECERA_EXIF = { 'E', 'x', 'i', 'f', 0, 0 };

	private OrientacionExif() {
	}

	// Recorre los segmentos del JPEG hasta el comienzo de los datos de la imagen (SOS) buscando el APP1 "Exif".
	static int leer(byte[] b) {
		int i = 2; // después de la firma FF D8
		while (i + 4 <= b.length) {
			if ((b[i] & 0xFF) != 0xFF) {
				return SIN_GIRAR;
			}
			int marcador = b[i + 1] & 0xFF;
			if (marcador == 0xFF) { // relleno entre segmentos
				i++;
				continue;
			}
			if (marcador == 0xDA || marcador == 0xD9) { // empiezan los datos de la imagen, o terminó el archivo
				return SIN_GIRAR;
			}
			if (marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7)) { // marcadores sin largo
				i += 2;
				continue;
			}
			int largo = u16(b, i + 2, false);
			int fin = i + 2 + largo;
			if (largo < 2 || fin > b.length) {
				return SIN_GIRAR;
			}
			int datos = i + 4;
			if (marcador == 0xE1 && empiezaCon(b, datos, fin, CABECERA_EXIF)) {
				int orientacion = orientacionTiff(b, datos + CABECERA_EXIF.length, fin);
				if (orientacion != 0) {
					return orientacion;
				}
			}
			i = fin;
		}
		return SIN_GIRAR;
	}

	// El EXIF es un bloque TIFF: orden de bytes ("II" o "MM"), el número 42 y el desplazamiento de la primera lista
	// de etiquetas (IFD0), donde está la orientación. Devuelve 0 si no la encuentra.
	private static int orientacionTiff(byte[] b, int base, int fin) {
		if (base + 8 > fin) {
			return 0;
		}
		boolean intel = b[base] == 'I' && b[base + 1] == 'I';
		boolean motorola = b[base] == 'M' && b[base + 1] == 'M';
		if ((!intel && !motorola) || u16(b, base + 2, intel) != 42) {
			return 0;
		}
		long ifd = base + u32(b, base + 4, intel);
		if (ifd + 2 > fin) {
			return 0;
		}
		int cantidad = u16(b, (int) ifd, intel);
		for (int k = 0; k < cantidad; k++) {
			long entrada = ifd + 2 + 12L * k;
			if (entrada + 12 > fin) {
				return 0;
			}
			int e = (int) entrada;
			if (u16(b, e, intel) == ETIQUETA_ORIENTACION) {
				if (u16(b, e + 2, intel) != TIPO_SHORT) {
					return 0;
				}
				int valor = u16(b, e + 8, intel);
				return valor >= 1 && valor <= 8 ? valor : 0;
			}
		}
		return 0;
	}

	private static boolean empiezaCon(byte[] b, int desde, int fin, byte[] prefijo) {
		if (desde + prefijo.length > fin) {
			return false;
		}
		for (int k = 0; k < prefijo.length; k++) {
			if (b[desde + k] != prefijo[k]) {
				return false;
			}
		}
		return true;
	}

	private static int u16(byte[] b, int i, boolean intel) {
		int a = b[i] & 0xFF;
		int c = b[i + 1] & 0xFF;
		return intel ? (c << 8) | a : (a << 8) | c;
	}

	private static long u32(byte[] b, int i, boolean intel) {
		long alto = u16(b, intel ? i + 2 : i, intel);
		long bajo = u16(b, intel ? i : i + 2, intel);
		return (alto << 16) | bajo;
	}
}
