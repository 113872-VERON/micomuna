package com.micomuna.enums;

// Por qué un reclamo de un lote no se cambió. Los mismos valores del contrato (OmitidoDto).
public enum CodigoOmitido {
	// No existe o es de otra comuna: no se distingue.
	NO_ENCONTRADO,
	YA_ESTABA_EN_ESE_ESTADO,
	TRANSICION_INVALIDA,
	// Se quiso descartar uno que ya está RESUELTO o DESCARTADO.
	YA_CERRADO,
	// Otra persona lo estaba cambiando y venció la espera.
	CONFLICTO,
	// El agente elegido dejó de ser válido durante el lote (por ejemplo, lo desactivaron).
	AGENTE_INVALIDO,
	// El reclamo original dejó de ser válido durante el lote (por ejemplo, lo descartaron).
	ORIGINAL_INVALIDO,
	// Falló algo inesperado con ese reclamo; los demás del lote no se ven afectados.
	ERROR
}
