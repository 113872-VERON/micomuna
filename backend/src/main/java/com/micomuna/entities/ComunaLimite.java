package com.micomuna.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// El límite geográfico (texto GeoJSON, puede pesar cientos de KB) vive en la misma tabla que la comuna, pero se lee
// con esta entidad aparte, igual que el logo (ComunaLogo): cargar una Comuna nunca trae el CLOB.
// No se crea ni se borra desde acá: la fila es la de la comuna. Solo se lee y se cambia con ComunaLimiteRepository.
@Entity
@Table(name = "comuna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComunaLimite {

	@Id
	private Long id;

	@Lob
	private String limite;

	@Column(name = "limite_cargado_en")
	private Instant limiteCargadoEn;
}
