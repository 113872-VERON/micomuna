package com.micomuna.services;

import java.io.IOException;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micomuna.exceptions.PeticionInvalidaException;

// Lee el GeoJSON del límite de una comuna y lo valida de verdad (no se confía en el archivo).
// Acepta un Polygon o un MultiPolygon, solo o dentro de un Feature o de un FeatureCollection con una sola figura.
// Cada posición va como manda GeoJSON: [longitud, latitud]. En JTS la longitud es x y la latitud es y.
// Lo que se guarda es solo la figura normalizada ({"type":..., "coordinates":...}), sin propiedades ni otros datos del archivo.
final class LimiteGeoJson {

	static final String CODIGO = "LIMITE_INVALIDO";

	static final int MAXIMO_PUNTOS = 100_000;
	private static final int PROFUNDIDAD_MAXIMA = 20;

	private static final ObjectMapper JSON = JsonMapper.builder(JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(PROFUNDIDAD_MAXIMA).build())
			.build())
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.build();
	private static final GeometryFactory FABRICA = new GeometryFactory();

	private LimiteGeoJson() {
	}

	// Devuelve la figura normalizada como texto GeoJSON. Lanza PeticionInvalidaException (LIMITE_INVALIDO) si no sirve.
	static String normalizar(byte[] contenido) {
		return aTexto(leer(contenido));
	}

	// Arma la geometría de JTS y comprueba que sea una figura válida. También sirve para releer lo ya guardado.
	static Geometry leer(byte[] contenido) {
		JsonNode raiz;
		try {
			raiz = JSON.readTree(contenido);
		} catch (IOException e) {
			throw invalido("El archivo no es un GeoJSON válido. Subí el archivo .geojson o .json tal como lo exportaste.");
		}
		if (raiz == null || !raiz.isObject()) {
			throw invalido("El archivo no es un GeoJSON válido. Tiene que ser un objeto con un campo \"type\".");
		}
		Geometry figura = figuraDe(raiz, new int[1]);
		if (figura.isEmpty()) {
			throw invalido("El límite no tiene ninguna figura.");
		}
		if (!figura.isValid()) {
			throw invalido("El límite no es una figura válida: algún borde se cruza o se toca a sí mismo, "
					+ "o un agujero queda fuera del polígono. Corregilo y volvé a subirlo.");
		}
		return figura;
	}

	private static Geometry figuraDe(JsonNode nodo, int[] puntos) {
		String tipo = texto(nodo, "type");
		if (tipo == null) {
			throw invalido("El archivo no es un GeoJSON válido: falta el campo \"type\".");
		}
		return switch (tipo) {
			case "Polygon", "MultiPolygon" -> geometria(nodo, puntos);
			case "Feature" -> geometriaDeUnFeature(nodo, puntos);
			case "FeatureCollection" -> {
				JsonNode features = nodo.get("features");
				if (features == null || !features.isArray() || features.isEmpty()) {
					throw invalido("El archivo no tiene ninguna figura.");
				}
				if (features.size() > 1) {
					throw invalido("El archivo tiene más de una figura. Subí un archivo con una sola (el límite de la comuna).");
				}
				JsonNode unico = features.get(0);
				if (unico == null || !unico.isObject() || !"Feature".equals(texto(unico, "type"))) {
					throw invalido("El archivo no es un GeoJSON válido: el elemento de la colección no es un Feature.");
				}
				yield geometriaDeUnFeature(unico, puntos);
			}
			default -> throw invalido("El archivo trae un " + mostrar(tipo)
					+ " y hace falta un polígono (Polygon o MultiPolygon).");
		};
	}

	private static Geometry geometriaDeUnFeature(JsonNode feature, int[] puntos) {
		JsonNode geometria = feature.get("geometry");
		if (geometria == null || !geometria.isObject()) {
			throw invalido("El archivo no tiene una figura: el Feature no trae \"geometry\".");
		}
		String tipo = texto(geometria, "type");
		if (!"Polygon".equals(tipo) && !"MultiPolygon".equals(tipo)) {
			throw invalido("El archivo trae un " + mostrar(tipo) + " y hace falta un polígono (Polygon o MultiPolygon).");
		}
		return geometria(geometria, puntos);
	}

	private static Geometry geometria(JsonNode nodo, int[] puntos) {
		JsonNode coordenadas = nodo.get("coordinates");
		if (coordenadas == null || !coordenadas.isArray()) {
			throw invalido("La figura no tiene coordenadas.");
		}
		if ("Polygon".equals(texto(nodo, "type"))) {
			return poligono(coordenadas, puntos);
		}
		if (coordenadas.isEmpty()) {
			throw invalido("La figura no tiene ningún polígono.");
		}
		Polygon[] poligonos = new Polygon[coordenadas.size()];
		for (int i = 0; i < poligonos.length; i++) {
			if (!coordenadas.get(i).isArray()) {
				throw invalido("Las coordenadas de la figura no tienen el formato de un MultiPolygon.");
			}
			poligonos[i] = poligono(coordenadas.get(i), puntos);
		}
		return FABRICA.createMultiPolygon(poligonos);
	}

	private static Polygon poligono(JsonNode anillos, int[] puntos) {
		if (anillos.isEmpty()) {
			throw invalido("El polígono no tiene ningún anillo.");
		}
		LinearRing exterior = anillo(anillos.get(0), puntos);
		LinearRing[] agujeros = new LinearRing[anillos.size() - 1];
		for (int i = 1; i < anillos.size(); i++) {
			agujeros[i - 1] = anillo(anillos.get(i), puntos);
		}
		return FABRICA.createPolygon(exterior, agujeros);
	}

	// total[0] lleva la suma de puntos de todos los anillos: pasado el tope se corta antes de armar nada.
	private static LinearRing anillo(JsonNode puntos, int[] total) {
		if (!puntos.isArray()) {
			throw invalido("Las coordenadas de la figura no tienen el formato de un polígono.");
		}
		if (puntos.size() < 4) {
			throw invalido("Cada borde del polígono necesita al menos 4 puntos (el último repite al primero).");
		}
		total[0] += puntos.size();
		if (total[0] > MAXIMO_PUNTOS) {
			throw invalido("El límite tiene demasiados puntos. Simplificalo y volvé a subirlo.");
		}
		Coordinate[] coordenadas = new Coordinate[puntos.size()];
		for (int i = 0; i < coordenadas.length; i++) {
			coordenadas[i] = posicion(puntos.get(i));
		}
		if (!coordenadas[0].equals2D(coordenadas[coordenadas.length - 1])) {
			throw invalido("Un borde del polígono está abierto: el último punto tiene que ser igual al primero.");
		}
		return FABRICA.createLinearRing(coordenadas);
	}

	// [longitud, latitud] (se ignora una altura opcional). Los números tienen que ser finitos y estar en rango.
	private static Coordinate posicion(JsonNode punto) {
		if (!punto.isArray() || punto.size() < 2 || !punto.get(0).isNumber() || !punto.get(1).isNumber()) {
			throw invalido("Hay un punto que no tiene el formato [longitud, latitud].");
		}
		double longitud = punto.get(0).doubleValue();
		double latitud = punto.get(1).doubleValue();
		if (!Double.isFinite(longitud) || !Double.isFinite(latitud)
				|| longitud < -180 || longitud > 180 || latitud < -90 || latitud > 90) {
			throw invalido("Hay coordenadas fuera de rango. Tienen que estar en grados, primero la longitud "
					+ "(entre -180 y 180) y después la latitud (entre -90 y 90).");
		}
		return new Coordinate(longitud, latitud);
	}

	private static String aTexto(Geometry figura) {
		ObjectNode salida = JSON.createObjectNode();
		if (figura instanceof MultiPolygon multi) {
			salida.put("type", "MultiPolygon");
			ArrayNode poligonos = salida.putArray("coordinates");
			for (int i = 0; i < multi.getNumGeometries(); i++) {
				poligonos.add(coordenadasDe((Polygon) multi.getGeometryN(i)));
			}
		} else {
			salida.put("type", "Polygon");
			salida.set("coordinates", coordenadasDe((Polygon) figura));
		}
		return salida.toString();
	}

	private static ArrayNode coordenadasDe(Polygon poligono) {
		ArrayNode anillos = JSON.createArrayNode();
		anillos.add(puntosDe(poligono.getExteriorRing()));
		for (int i = 0; i < poligono.getNumInteriorRing(); i++) {
			anillos.add(puntosDe(poligono.getInteriorRingN(i)));
		}
		return anillos;
	}

	private static ArrayNode puntosDe(org.locationtech.jts.geom.LineString anillo) {
		ArrayNode puntos = JSON.createArrayNode();
		for (Coordinate c : anillo.getCoordinates()) {
			puntos.addArray().add(c.x).add(c.y);
		}
		return puntos;
	}

	private static String texto(JsonNode nodo, String campo) {
		JsonNode valor = nodo.get(campo);
		return valor != null && valor.isTextual() ? valor.asText() : null;
	}

	// El tipo viene del archivo: se recorta para no devolver un texto enorme en el mensaje.
	private static String mostrar(String tipo) {
		if (tipo == null) {
			return "tipo de figura que no se reconoce";
		}
		return tipo.length() > 30 ? tipo.substring(0, 30) + "..." : tipo;
	}

	private static PeticionInvalidaException invalido(String mensaje) {
		return new PeticionInvalidaException(CODIGO, mensaje);
	}
}
