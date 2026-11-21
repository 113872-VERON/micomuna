package com.micomuna.enums;

public enum Rol {
	VECINO("Vecino"),
	ADMINISTRATIVO("Personal administrativo"),
	AGENTE("Agente"),
	ADMIN_COMUNA("Administrador"),
	SUPERADMIN("Superadministrador");

	// Cómo se nombra el rol en los textos para las personas (por ejemplo, el correo de invitación).
	private final String nombre;

	Rol(String nombre) {
		this.nombre = nombre;
	}

	public String getNombre() {
		return nombre;
	}
}
