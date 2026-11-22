package com.micomuna.services;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.micomuna.entities.ComunaLimite;
import com.micomuna.repositories.ComunaLimiteRepository;

import lombok.RequiredArgsConstructor;

// Responde "este punto está dentro de la comuna". Guarda en memoria la figura ya armada de cada comuna para no
// leer el CLOB ni parsear el GeoJSON en cada alta de un reclamo. Se descarta cuando el superadmin carga un
// límite nuevo (ver invalidar). Una comuna sin límite también se recuerda (como "sin límite").
@Component
@RequiredArgsConstructor
public class LimiteDeComuna {

	private static final Logger log = LoggerFactory.getLogger(LimiteDeComuna.class);
	private static final GeometryFactory FABRICA = new GeometryFactory();

	private final ComunaLimiteRepository limites;
	private final ConcurrentHashMap<Long, Optional<PreparedGeometry>> cache = new ConcurrentHashMap<>();

	public enum Resultado {
		DENTRO, FUERA, SIN_LIMITE
	}

	// Un punto sobre el borde cuenta como adentro (covers, no contains). Sin límite cargado no se restringe nada.
	public Resultado ubicar(Long comunaId, double latitud, double longitud) {
		Optional<PreparedGeometry> figura = figuraDe(comunaId);
		if (figura.isEmpty()) {
			return Resultado.SIN_LIMITE;
		}
		// JTS: x = longitud, y = latitud.
		Point punto = FABRICA.createPoint(new Coordinate(longitud, latitud));
		return figura.get().covers(punto) ? Resultado.DENTRO : Resultado.FUERA;
	}

	// Se llama al cargar un límite nuevo. Se descarta enseguida y otra vez cuando termina la transacción:
	// entre medio otro pedido pudo volver a leer el límite viejo, que todavía era el vigente en la base.
	public void invalidar(Long comunaId) {
		cache.remove(comunaId);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int estado) {
					cache.remove(comunaId);
				}
			});
		}
	}

	private Optional<PreparedGeometry> figuraDe(Long comunaId) {
		Optional<PreparedGeometry> guardada = cache.get(comunaId);
		if (guardada != null) {
			return guardada;
		}
		String texto = limites.findById(comunaId).map(ComunaLimite::getLimite).filter(t -> !t.isBlank()).orElse(null);
		if (texto == null) {
			Optional<PreparedGeometry> previa = cache.putIfAbsent(comunaId, Optional.empty());
			return previa != null ? previa : Optional.empty();
		}
		Optional<PreparedGeometry> leida = armar(comunaId, texto);
		if (leida.isEmpty()) {
			// Dato dañado: no se recuerda, así se reintenta en cada alta y se retoma el control apenas se arregle.
			return leida;
		}
		Optional<PreparedGeometry> previa = cache.putIfAbsent(comunaId, leida);
		return previa != null ? previa : leida;
	}

	// El texto guardado ya pasó la validación al cargarse. Si aun así no se puede leer (dato dañado), no se bloquea
	// a los vecinos: se registra el error (sin el contenido) y no se restringe, pero sin cachear ese resultado.
	private Optional<PreparedGeometry> armar(Long comunaId, String texto) {
		try {
			return Optional.of(PreparedGeometryFactory.prepare(LimiteGeoJson.leer(texto.getBytes(StandardCharsets.UTF_8))));
		} catch (RuntimeException e) {
			log.error("No se pudo leer el límite guardado de la comuna {}: no se restringe la ubicación hasta que se corrija", comunaId);
			return Optional.empty();
		}
	}
}
