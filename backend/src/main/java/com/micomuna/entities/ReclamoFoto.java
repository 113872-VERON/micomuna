package com.micomuna.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import com.micomuna.enums.OrigenFoto;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Una foto de un reclamo (hasta 3, con orden 1 a 3). Es una entidad aparte justamente para que la imagen
// no viaje con el reclamo: Reclamo no la referencia, así que los bytes solo se leen cuando se pide la foto.
// La imagen que se guarda ya pasó por ImagenUtil.limpiar: no conserva los datos ocultos del archivo original.
// Una foto no se cambia: se crea junto con el reclamo.
@Entity
@Table(name = "reclamo_foto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReclamoFoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_reclamo", nullable = false, updatable = false)
	private Reclamo reclamo;

	@Column(nullable = false, updatable = false)
	private int orden;

	// VECINO (al reportar) o TRABAJO (las del agente al resolver). El orden 1 a 3 no se repite dentro de cada origen.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private OrigenFoto origen;

	@Lob
	@Column(nullable = false, updatable = false)
	private byte[] imagen;

	@Column(name = "imagen_tipo", nullable = false, updatable = false)
	private String imagenTipo;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	public ReclamoFoto(Reclamo reclamo, OrigenFoto origen, int orden, byte[] imagen, String imagenTipo) {
		this.reclamo = reclamo;
		this.origen = origen;
		this.orden = orden;
		this.imagen = imagen;
		this.imagenTipo = imagenTipo;
	}

	@PrePersist
	void alCrear() {
		if (creadaEn == null) {
			creadaEn = Instant.now();
		}
	}
}
