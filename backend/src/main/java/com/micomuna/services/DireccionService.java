package com.micomuna.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.micomuna.client.NominatimClient;

// Direccion aproximada de un punto, para mostrarsela al vecino. Es solo informativa.
// Cuida el servicio externo: guarda en memoria las direcciones ya pedidas (punto redondeado a 5 decimales, unos
// 1,1 m, con tope de entradas: se descarta la mas vieja) y deja salir como mucho un pedido por segundo en total
// (LimitadorPorSegundo, sin hacer esperar a nadie), mas un tope de 10 pedidos por minuto por usuario
// (LimitadorPorUsuario). Pasado cualquiera de los dos topes: 429 DEMASIADOS_PEDIDOS, sin llamar al servicio externo.
// Solo se recuerdan las direcciones encontradas: un fallo o un "sin resultado" no se guarda, se reintenta.
@Service
public class DireccionService {

	static final int DECIMALES = 5;
	static final int CACHE_MAXIMA = 5_000;

	private final NominatimClient nominatim;
	private final LimitadorPorUsuario limitadorPorUsuario;
	private final LimitadorPorSegundo limitador;
	private final Map<String, String> cache = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> masVieja) {
			return size() > CACHE_MAXIMA;
		}
	};

	@Autowired
	public DireccionService(NominatimClient nominatim) {
		this(nominatim, System::nanoTime);
	}

	DireccionService(NominatimClient nominatim, LongSupplier relojNanos) {
		this.nominatim = nominatim;
		this.limitadorPorUsuario = new LimitadorPorUsuario(relojNanos);
		this.limitador = new LimitadorPorSegundo(relojNanos);
	}

	// Vacio si no hay direccion o el servicio externo no respondio.
	public Optional<String> direccionDe(Long usuarioId, double latitud, double longitud) {
		BigDecimal lat = redondear(latitud);
		BigDecimal lon = redondear(longitud);
		String clave = lat.toPlainString() + "," + lon.toPlainString();
		synchronized (cache) {
			String guardada = cache.get(clave);
			if (guardada != null) {
				return Optional.of(guardada);
			}
		}
		// Primero el tope del usuario y despues el global. Los aciertos de la cache no cuentan para ninguno.
		limitadorPorUsuario.registrar(usuarioId);
		limitador.tomarTurno();
		Optional<String> encontrada = nominatim.direccionDe(lat, lon);
		encontrada.ifPresent(direccion -> {
			synchronized (cache) {
				cache.put(clave, direccion);
			}
		});
		return encontrada;
	}

	private static BigDecimal redondear(double coordenada) {
		return BigDecimal.valueOf(coordenada).setScale(DECIMALES, RoundingMode.HALF_UP);
	}

	int tamanoDeLaCache() {
		synchronized (cache) {
			return cache.size();
		}
	}
}
