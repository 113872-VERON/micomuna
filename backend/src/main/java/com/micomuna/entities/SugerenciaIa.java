package com.micomuna.entities;

import java.time.Instant;

import com.micomuna.enums.DecisionSugerenciaIa;
import com.micomuna.enums.EstadoSugerenciaIa;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.ProveedorIa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Lo que sugirió la IA sobre un reclamo (V12, MC-24). Una fila por análisis; la vigente es la más nueva. Se guarda también
// cuando falló (ERROR) o no se llamó (OMITIDA), para contar intentos y explicar por qué un reclamo no tiene sugerencia.
// La IA solo sugiere: nada de esto cambia el reclamo. La base exige la coherencia (ck_sugerencia_ia_hecha): HECHA trae tipo,
// prioridad y justificación; ERROR y OMITIDA no traen ninguno. Una fila no se modifica: se crea una nueva.
// La comuna y el reclamo van como ids sueltos (la clave foránea de dos columnas la controla la base).
// Lo único que cambia de una fila es la decisión del personal (V13, MC-25): aceptó o corrigió lo que sugirió la IA. Va todo junto
// (decisión, tipo decidido, quién y cuándo) y solo sobre una HECHA; ck_sugerencia_ia_decision_coherente lo exige en la base.
@Entity
@Table(name = "reclamo_sugerencia_ia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SugerenciaIa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "id_comuna", nullable = false, updatable = false)
	private Long idComuna;

	@Column(name = "id_reclamo", nullable = false, updatable = false)
	private Long idReclamo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private EstadoSugerenciaIa estado;

	// Código de tipo_reclamo (clave foránea a tipo_reclamo.codigo).
	@Column(name = "tipo_sugerido", updatable = false)
	private String tipoSugerido;

	@Enumerated(EnumType.STRING)
	@Column(name = "prioridad_sugerida", updatable = false)
	private PrioridadIa prioridadSugerida;

	@Column(updatable = false)
	private String justificacion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ProveedorIa proveedor;

	@Column(updatable = false)
	private String modelo;

	// Solo con ERROR; nunca con datos personales.
	@Column(name = "detalle_error", updatable = false)
	private String detalleError;

	@Column(name = "tokens_entrada", updatable = false)
	private Long tokensEntrada;

	@Column(name = "tokens_salida", updatable = false)
	private Long tokensSalida;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	// ACEPTADA: el personal dejó el tipo sugerido. CORREGIDA: dejó otro (incluido el que había elegido el vecino).
	@Enumerated(EnumType.STRING)
	private DecisionSugerenciaIa decision;

	// Código de tipo_reclamo que quedó decidido (clave foránea a tipo_reclamo.codigo).
	@Column(name = "tipo_decidido")
	private String tipoDecidido;

	// Quién decidió: id suelto (la clave foránea de dos columnas con id_comuna la controla la base).
	@Column(name = "id_usuario_decision")
	private Long idUsuarioDecision;

	@Column(name = "decidida_en")
	private Instant decididaEn;

	private SugerenciaIa(Long idComuna, Long idReclamo, EstadoSugerenciaIa estado, ProveedorIa proveedor, String modelo) {
		this.idComuna = idComuna;
		this.idReclamo = idReclamo;
		this.estado = estado;
		this.proveedor = proveedor;
		this.modelo = modelo;
	}

	public static SugerenciaIa hecha(Long idComuna, Long idReclamo, ProveedorIa proveedor, String modelo, String tipo,
			PrioridadIa prioridad, String justificacion, Long tokensEntrada, Long tokensSalida) {
		SugerenciaIa s = new SugerenciaIa(idComuna, idReclamo, EstadoSugerenciaIa.HECHA, proveedor, modelo);
		s.tipoSugerido = tipo;
		s.prioridadSugerida = prioridad;
		s.justificacion = justificacion;
		s.tokensEntrada = tokensEntrada;
		s.tokensSalida = tokensSalida;
		return s;
	}

	public static SugerenciaIa conError(Long idComuna, Long idReclamo, ProveedorIa proveedor, String modelo,
			String detalle, Long tokensEntrada, Long tokensSalida) {
		SugerenciaIa s = new SugerenciaIa(idComuna, idReclamo, EstadoSugerenciaIa.ERROR, proveedor, modelo);
		s.detalleError = detalle;
		s.tokensEntrada = tokensEntrada;
		s.tokensSalida = tokensSalida;
		return s;
	}

	// No se llamó a nadie: proveedor NINGUNO y sin modelo.
	public static SugerenciaIa omitida(Long idComuna, Long idReclamo) {
		return new SugerenciaIa(idComuna, idReclamo, EstadoSugerenciaIa.OMITIDA, ProveedorIa.NINGUNO, null);
	}

	// Registra (o reemplaza) la decisión del personal. Solo una HECHA se decide. ACEPTADA si el tipo decidido es el sugerido;
	// si no, CORREGIDA.
	public void decidir(String tipoDecidido, Long idUsuario, Instant cuando) {
		if (estado != EstadoSugerenciaIa.HECHA) {
			throw new IllegalStateException("Solo se decide sobre una sugerencia HECHA.");
		}
		this.decision = tipoDecidido.equals(tipoSugerido) ? DecisionSugerenciaIa.ACEPTADA : DecisionSugerenciaIa.CORREGIDA;
		this.tipoDecidido = tipoDecidido;
		this.idUsuarioDecision = idUsuario;
		this.decididaEn = cuando;
	}

	@PrePersist
	void alCrear() {
		if (creadaEn == null) {
			creadaEn = Instant.now();
		}
	}
}
