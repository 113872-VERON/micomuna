package com.micomuna.entities;

import java.time.Instant;

import com.micomuna.enums.Rol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Vacía solo para el superadmin.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_comuna")
	private Comuna comuna;

	@Column(nullable = false)
	private String correo;

	@Column(name = "clave_hash", nullable = false)
	private String claveHash;

	@Column(nullable = false)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Rol rol;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "debe_cambiar_clave", nullable = false)
	private boolean debeCambiarClave = false;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@PrePersist
	void alCrear() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
	}
}
