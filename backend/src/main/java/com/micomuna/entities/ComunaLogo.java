package com.micomuna.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// La imagen del logo vive en la misma tabla que la comuna, pero se lee con esta entidad aparte:
// así cargar una Comuna (login, cada pedido con token, consulta pública) nunca trae los bytes.
// No se crea ni se borra desde acá: la fila es la de la comuna. Solo se lee y se cambia con ComunaLogoRepository.
@Entity
@Table(name = "comuna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComunaLogo {

	@Id
	private Long id;

	@Lob
	private byte[] logo;

	@Column(name = "logo_tipo")
	private String logoTipo;
}
