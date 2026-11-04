package com.micomuna.enums;

public enum ColorComuna {
	CELESTE("#1D6AA8"),
	MARINO("#1E3A8A"),
	VERDE_AZULADO("#0B6E6E"),
	BOSQUE("#2F6B3F"),
	CIRUELA("#6B2D7B"),
	OCRE("#9A4A0B"),
	PIZARRA("#3F4F5F");

	private final String hexa;

	ColorComuna(String hexa) {
		this.hexa = hexa;
	}

	public String getHexa() {
		return hexa;
	}
}
