package com.micomuna.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

// Un servidor HTTP local y falso para probar a los proveedores de IA sin llamar a nadie de verdad: anota lo que le llega
// y responde lo que se le configure (estado, cuerpo y demora). Solo escucha en 127.0.0.1 y en un puerto libre.
final class ServidorSimulado implements AutoCloseable {

	record Pedido(String metodo, String ruta, String cuerpo, com.sun.net.httpserver.Headers cabeceras) {
	}

	private final HttpServer servidor;
	final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
	volatile int estado = 200;
	volatile String respuesta = "{}";
	volatile long demoraMs = 0;
	// Si es mayor que 0, las primeras N respuestas son error 500 (para probar los reintentos).
	volatile int fallosAntesDeResponder = 0;
	// Si es mayor que 0, responde 200 y gotea la respuesta de a un byte cada tantos milisegundos (hasta unos 8 segundos).
	volatile long goteoMs = 0;
	// Si es mayor que 0, la respuesta es un cuerpo de esta cantidad de bytes.
	volatile int bytesDeLaRespuesta = 0;

	ServidorSimulado() {
		try {
			servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		servidor.createContext("/", intercambio -> {
			String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			pedidos.add(new Pedido(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(), cuerpo,
					intercambio.getRequestHeaders()));
			if (demoraMs > 0) {
				try {
					Thread.sleep(demoraMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			if (goteoMs > 0) {
				try (OutputStream salida = intercambio.getResponseBody()) {
					intercambio.sendResponseHeaders(200, 0);
					for (int i = 0; i < 8000 / goteoMs; i++) {
						salida.write('x');
						salida.flush();
						Thread.sleep(goteoMs);
					}
				} catch (IOException | InterruptedException e) {
					// El cliente cortó: no importa.
				}
				return;
			}
			int codigo = estado;
			String texto = respuesta;
			if (fallosAntesDeResponder > 0 && pedidos.size() <= fallosAntesDeResponder) {
				codigo = 500;
				texto = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"falla simulada\"}}";
			}
			byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
			if (bytesDeLaRespuesta > 0) {
				bytes = new byte[bytesDeLaRespuesta];
				java.util.Arrays.fill(bytes, (byte) 'x');
			}
			intercambio.getResponseHeaders().add("Content-Type", "application/json");
			try {
				intercambio.sendResponseHeaders(codigo, bytes.length);
				try (OutputStream salida = intercambio.getResponseBody()) {
					salida.write(bytes);
				}
			} catch (IOException e) {
				// El cliente ya cortó (por ejemplo, por tiempo de espera): no importa.
			}
		});
		servidor.start();
	}

	String url() {
		return "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

	@Override
	public void close() {
		servidor.stop(0);
	}
}
