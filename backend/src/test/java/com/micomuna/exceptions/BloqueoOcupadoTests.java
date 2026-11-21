package com.micomuna.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ProblemDetail;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;

// El tiempo de espera vencido de un bloqueo (simulado acá; el real con Oracle está en BloqueosDeUsuarioTests)
// responde 409 CONFLICTO con un mensaje para reintentar, venga traducido por Spring o directo de JPA.
class BloqueoOcupadoTests {

	private final ApiExceptionHandler manejador = new ApiExceptionHandler();

	@Test
	void cualquierFormaDelTiempoDeEsperaVencidoResponde409() {
		for (Exception e : List.of(new CannotAcquireLockException("ORA-00054"),
				new PessimisticLockingFailureException("bloqueo"), new LockTimeoutException("espera"),
				new PessimisticLockException("bloqueo"))) {
			ProblemDetail problema = manejador.bloqueoOcupado(e);

			assertThat(problema.getStatus()).as(e.getClass().getSimpleName()).isEqualTo(409);
			assertThat(problema.getProperties()).containsEntry("codigo", "CONFLICTO");
			assertThat(problema.getDetail())
					.isEqualTo("Otra persona está cambiando estos datos. Probá de nuevo en unos segundos.");
		}
	}
}
