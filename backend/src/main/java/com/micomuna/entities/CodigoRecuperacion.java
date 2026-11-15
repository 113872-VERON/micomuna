package com.micomuna.entities;

import java.time.Instant;

import com.micomuna.enums.TipoCodigo;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "codigo_recuperacion")
@Getter
@Setter
@NoArgsConstructor
public class CodigoRecuperacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario")
	private Usuario usuario;

	@Column(name = "codigo_hash", nullable = false)
	private String codigoHash;

	@Column(name = "vence_en", nullable = false)
	private Instant venceEn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoCodigo tipo = TipoCodigo.RECUPERACION;

	@Column(nullable = false)
	private boolean usado = false;
}
