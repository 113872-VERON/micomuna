package com.micomuna.entities;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Catálogo de tipos de problema, igual para todas las comunas. Lo carga la migración V5:
// la aplicación solo lo lee (por eso es inmutable) y lo busca siempre por código, nunca por id.
@Entity
@Table(name = "tipo_reclamo")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoReclamo {

	@Id
	private Long id;

	@Column(nullable = false)
	private String codigo;

	@Column(nullable = false)
	private String nombre;

	@Column(nullable = false)
	private int orden;

	@Column(name = "consejo_foto", nullable = false)
	private String consejoFoto;
}
