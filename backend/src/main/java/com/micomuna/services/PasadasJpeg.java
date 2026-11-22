package com.micomuna.services;

// Cuenta las pasadas (marcadores SOS, FF DA) de un JPEG sin abrir la imagen.
// Un JPEG progresivo se dibuja en varias pasadas; uno armado con miles de pasadas chiquitas hace trabajar al
// decodificador muchísimo más de lo que pesa el archivo. ImagenUtil lo rechaza antes de abrirlo.
// Recorre los segmentos como el decodificador: salta cada segmento por su largo y, después de cada SOS, los datos
// comprimidos hasta el próximo marcador (FF 00 es un FF de los datos y FF D0 a FF D7 son marcas de reinicio,
// no marcadores). Si encuentra bytes sueltos fuera de lugar, busca el próximo FF igual que el decodificador,
// así un byte basura entre pasadas no corta la cuenta.
final class PasadasJpeg {

	private PasadasJpeg() {
	}

	// Devuelve la cantidad de pasadas, o un número mayor que hasta en cuanto lo supera (no hace falta seguir).
	static int contar(byte[] b, int hasta) {
		int pasadas = 0;
		int i = 2; // después de la firma FF D8
		while (i + 1 < b.length) {
			if ((b[i] & 0xFF) != 0xFF) {
				i++;
				continue;
			}
			int marcador = b[i + 1] & 0xFF;
			if (marcador == 0xFF) {
				i++;
				continue;
			}
			if (marcador == 0xD9) { // fin de la imagen
				return pasadas;
			}
			if (marcador == 0x00 || marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7)) {
				i += 2;
				continue;
			}
			if (i + 4 > b.length) {
				return pasadas;
			}
			int largo = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
			int fin = i + 2 + largo;
			if (largo < 2 || fin > b.length) {
				return pasadas;
			}
			if (marcador != 0xDA) {
				i = fin;
				continue;
			}
			pasadas++;
			if (pasadas > hasta) {
				return pasadas;
			}
			i = finDeLosDatosComprimidos(b, fin);
		}
		return pasadas;
	}

	private static int finDeLosDatosComprimidos(byte[] b, int desde) {
		int j = desde;
		while (j + 1 < b.length) {
			if ((b[j] & 0xFF) == 0xFF) {
				int siguiente = b[j + 1] & 0xFF;
				if (siguiente == 0x00 || (siguiente >= 0xD0 && siguiente <= 0xD7)) {
					j += 2;
					continue;
				}
				if (siguiente != 0xFF) {
					return j; // un marcador de verdad
				}
			}
			j++;
		}
		return b.length;
	}
}
