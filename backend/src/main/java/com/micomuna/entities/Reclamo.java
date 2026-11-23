package com.micomuna.entities;

import java.math.BigDecimal;
import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un problema que reportó un vecino en su comuna.
// A propósito no tiene la lista de fotos: cargar un reclamo (o una lista de reclamos) nunca trae las imágenes.
// Las fotos se leen aparte, con ReclamoFoto. Lo mismo el historial de estados (ReclamoEstado).
@Entity
@Table(name = "reclamo")
@Getter
@Setter
@NoArgsConstructor
public class Reclamo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_comuna", nullable = false, updatable = false)
	private Comuna comuna;

	// Número de seguimiento, correlativo dentro de la comuna (lo asigna ReclamoService con el contador de la comuna).
	@Column(nullable = false, updatable = false)
	private Integer numero;

	// La base exige que el vecino sea de la misma comuna que el reclamo (fk_reclamo_vecino, de dos columnas).
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_vecino", nullable = false, updatable = false)
	private Usuario vecino;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_tipo", nullable = false)
	private TipoReclamo tipo;

	@Column(nullable = false)
	private String descripcion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoReclamo estado = EstadoReclamo.RECIBIDO;

	// Latitud y longitud van juntas o no va ninguna, con 6 decimales (ck_reclamo_lat_lng_juntas).
	@Column(precision = 8, scale = 6)
	private BigDecimal latitud;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitud;

	private String referencia;

	// Dirección aproximada que calcula el navegador a partir del punto. Solo informativa.
	private String direccion;

	// Datos de un reclamo DESCARTADO (V9). La base garantiza que el motivo existe solo con ese estado, que el original
	// es de la misma comuna, distinto del propio, y que solo se informa con motivo DUPLICADO.
	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_descarte")
	private MotivoDescarte motivoDescarte;

	@Column(name = "observacion_descarte")
	private String observacionDescarte;

	// Si es un duplicado, el reclamo que sigue la comuna.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_reclamo_original")
	private Reclamo reclamoOriginal;

	// Agente de campo asignado (V10). Vacío mientras el reclamo está RECIBIDO. La base exige que sea de la misma comuna;
	// que tenga rol AGENTE lo valida CambioDeEstadoService.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_agente")
	private Usuario agente;

	// Lo que hizo el agente al resolverlo. Si el reclamo se reabre queda como antecedente.
	@Column(name = "trabajo_realizado")
	private String trabajoRealizado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@PrePersist
	void alCrear() {
		if (creadoEn == null) {
			creadoEn = Instant.now();
		}
		actualizadoEn = creadoEn;
	}

	@PreUpdate
	void alActualizar() {
		actualizadoEn = Instant.now();
	}
}
