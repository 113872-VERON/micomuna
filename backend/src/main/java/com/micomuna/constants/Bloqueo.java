package com.micomuna.constants;

// Bloqueos de filas (SELECT ... FOR UPDATE) para cambios que no pueden pisarse entre sí.
// Si otra transacción tiene la fila, se espera como mucho ESPERA_MAXIMA_MS y después se responde 409
// (ver ApiExceptionHandler) en lugar de dejar la petición colgada.
public final class Bloqueo {

	// Hint de JPA: Hibernate lo traduce en Oracle a "FOR UPDATE WAIT 3".
	public static final String HINT_ESPERA = "jakarta.persistence.lock.timeout";
	public static final String ESPERA_MAXIMA_MS = "3000";

	private Bloqueo() {
	}
}
