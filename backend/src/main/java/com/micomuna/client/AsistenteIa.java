package com.micomuna.client;

import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.ResultadoIa;

// La IA detrás de una interfaz propia (MC-24): una implementación para Claude, otra para Ollama y otra que no analiza.
// La elige la propiedad micomuna.ia.proveedor. La IA solo SUGIERE: la decisión es siempre del personal municipal.
public interface AsistenteIa {

	// Quién responde (CLAUDE, OLLAMA o NINGUNO).
	ProveedorIa proveedor();

	// Modelo que se usa, o null si no hay uno (proveedor NINGUNO).
	String modelo();

	// false si no se puede analizar (proveedor apagado o sin clave): el reclamo queda con la sugerencia OMITIDA y
	// no se llama a nadie.
	boolean disponible();

	// Analiza un reclamo. Un buen implementador nunca lanza: los errores del proveedor, los tiempos agotados y las
	// respuestas inválidas se devuelven como ResultadoIa.Fallida (con un detalle corto, sin datos personales).
	ResultadoIa analizar(PedidoIa pedido);
}
