package com.micomuna.entities;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Una fila del historial de un reclamo: quién lo cambió, cuándo y de qué estado a cuál.
// Es el registro de lo que pasó: se agrega, no se cambia. El alta del reclamo deja la primera fila
// (RECIBIDO, sin estado anterior, con el vecino que lo reportó).
@Entity
@Table(name = "reclamo_estado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReclamoEstado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_reclamo", nullable = false, updatable = false)
	private Reclamo reclamo;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_anterior", updatable = false)
	private EstadoReclamo estadoAnterior;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private EstadoReclamo estado;

	// Vacío si el cambio lo hizo el sistema.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_usuario", updatable = false)
	private Usuario usuario;

	// Agente que quedó asignado con este cambio (V11). Solo se guarda en los cambios que asignan (ASIGNADO); en los demás
	// queda vacío. El agente anterior de una reasignación no se guarda: sale de las filas previas (ver PanelReclamosRepository).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_agente", updatable = false)
	private Usuario agente;

	@Column(updatable = false)
	private String observacion;

	@Column(name = "cambiado_en", nullable = false, updatable = false)
	private Instant cambiadoEn;

	public ReclamoEstado(Reclamo reclamo, EstadoReclamo estadoAnterior, EstadoReclamo estado, Usuario usuario) {
		this.reclamo = reclamo;
		this.estadoAnterior = estadoAnterior;
		this.estado = estado;
		this.usuario = usuario;
	}

	public ReclamoEstado(Reclamo reclamo, EstadoReclamo estadoAnterior, EstadoReclamo estado, Usuario usuario,
			String observacion) {
		this(reclamo, estadoAnterior, estado, usuario);
		this.observacion = observacion;
	}

	public ReclamoEstado(Reclamo reclamo, EstadoReclamo estadoAnterior, EstadoReclamo estado, Usuario usuario,
			String observacion, Usuario agente) {
		this(reclamo, estadoAnterior, estado, usuario, observacion);
		this.agente = agente;
	}

	@PrePersist
	void alCrear() {
		if (cambiadoEn == null) {
			cambiadoEn = Instant.now();
		}
	}
}
