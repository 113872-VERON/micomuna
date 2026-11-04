package com.micomuna.entities;

import java.time.Instant;

import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comuna")
@Getter
@Setter
@NoArgsConstructor
public class Comuna {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provincia provincia;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(nullable = false)
	private boolean activa = true;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ColorComuna color = ColorComuna.CELESTE;

	private String departamento;

	@Lob
	private byte[] logo;

	@Column(name = "logo_tipo")
	private String logoTipo;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	@PrePersist
	void alCrear() {
		if (creadaEn == null) {
			creadaEn = Instant.now();
		}
	}
}
