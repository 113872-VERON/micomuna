package com.micomuna.client;

import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.ResultadoIa;

// Proveedor "ninguno" (el valor por defecto): no analiza nada. Cada reclamo queda con una sugerencia OMITIDA.
// También se usa cuando se pide Claude pero falta la clave.
public class AsistenteNinguno implements AsistenteIa {

	@Override
	public ProveedorIa proveedor() {
		return ProveedorIa.NINGUNO;
	}

	@Override
	public String modelo() {
		return null;
	}

	@Override
	public boolean disponible() {
		return false;
	}

	@Override
	public ResultadoIa analizar(PedidoIa pedido) {
		return new ResultadoIa.Omitida();
	}
}
