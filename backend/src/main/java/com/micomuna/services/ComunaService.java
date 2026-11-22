package com.micomuna.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.ActualizarComunaRequest;
import com.micomuna.dtos.ComunaAdminDto;
import com.micomuna.dtos.ComunaCreadaDto;
import com.micomuna.dtos.CrearComunaRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.ComunaLogo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaLimiteRepository;
import com.micomuna.repositories.ComunaLogoRepository;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComunaService {

	private static final String ENLACE_REPETIDO = "Ya existe una comuna con ese enlace. "
			+ "Agregale el departamento al enlace para distinguirla (por ejemplo salsipuedes-colon-cordoba)";

	public static final int LOGO_MAXIMO_BYTES = 512 * 1024;
	public static final int LOGO_LADO_MAXIMO_PX = 2000;
	public static final int LIMITE_MAXIMO_BYTES = 1024 * 1024;

	private final ComunaRepository comunas;
	private final ComunaLogoRepository logos;
	private final ComunaLimiteRepository limites;
	private final LimiteDeComuna limiteDeComuna;
	private final ComunaActual comunaActual;
	private final UsuarioRepository usuarios;
	private final InvitacionService invitaciones;
	private final TransactionTemplate transaccion;

	public Comuna buscarActivaPorSlug(String slug) {
		if (!SlugUtil.esValido(slug)) {
			throw noEncontrada();
		}
		return comunas.findBySlug(slug)
				.filter(Comuna::isActiva)
				.orElseThrow(ComunaService::noEncontrada);
	}

	@Transactional(readOnly = true)
	public List<ComunaAdminDto> listar() {
		return comunas.listarParaAdmin();
	}

	// Crea la comuna, su administrador y el código de invitación en una sola transacción: si algo falla, no queda nada.
	// El administrador queda sin contraseña (pendiente). El correo sale después de guardar: si falla,
	// la comuna queda creada y la respuesta avisa (invitacionEnviada = false) para reenviarlo.
	public ComunaCreadaDto crear(CrearComunaRequest pedido) {
		Creada creada = transaccion.execute(estado -> crearEnTransaccion(pedido));
		return new ComunaCreadaDto(creada.comuna(), creada.adminCorreo(), invitaciones.enviar(creada.invitacion()));
	}

	private record Creada(ComunaAdminDto comuna, String adminCorreo, InvitacionService.Invitacion invitacion) {
	}

	private Creada crearEnTransaccion(CrearComunaRequest pedido) {
		String slug = pedido.slug() != null ? pedido.slug() : generarEnlace(pedido);
		if (comunas.existsBySlug(slug)) {
			throw new ConflictoException(ENLACE_REPETIDO);
		}

		Comuna comuna = new Comuna();
		comuna.setNombre(pedido.nombre());
		comuna.setProvincia(pedido.provincia());
		comuna.setSlug(slug);
		comuna.setColor(pedido.color() != null ? pedido.color() : ColorComuna.CELESTE);
		comuna.setDepartamento(pedido.departamento());
		comuna.setTelefono(pedido.telefono());
		comuna.setWhatsapp(pedido.whatsapp());
		comuna.setActiva(true);

		Usuario admin = new Usuario();
		admin.setComuna(comuna);
		admin.setCorreo(pedido.adminCorreo());
		admin.setNombre(pedido.adminNombre());
		admin.setRol(Rol.ADMIN_COMUNA);
		admin.setClaveHash(null);
		admin.setActivo(true);

		try {
			comunas.saveAndFlush(comuna);
			usuarios.saveAndFlush(admin);
		} catch (DataIntegrityViolationException e) {
			// Otro alta con el mismo enlace entró entre la consulta y el guardado.
			throw new ConflictoException(ENLACE_REPETIDO);
		}

		return new Creada(ComunaAdminDto.desde(comuna), admin.getCorreo(), invitaciones.preparar(admin, comuna));
	}

	// Solo cambia lo que viene en el pedido. El enlace no se toca: ya está repartido entre los vecinos.
	@Transactional
	public ComunaAdminDto actualizar(Long id, ActualizarComunaRequest pedido) {
		Comuna comuna = comunas.findById(id).orElseThrow(ComunaService::noEncontrada);

		if (pedido.nombre() != null) {
			comuna.setNombre(pedido.nombre());
		}
		if (pedido.activa() != null) {
			comuna.setActiva(pedido.activa());
		}
		if (pedido.color() != null) {
			comuna.setColor(pedido.color());
		}
		if (pedido.departamento() != null) {
			comuna.setDepartamento(pedido.departamento().isEmpty() ? null : pedido.departamento());
		}
		if (pedido.telefono() != null) {
			comuna.setTelefono(pedido.telefono().isEmpty() ? null : pedido.telefono());
		}
		if (pedido.whatsapp() != null) {
			comuna.setWhatsapp(pedido.whatsapp().isEmpty() ? null : pedido.whatsapp());
		}
		// Si no cambió nada de verdad, Hibernate no manda el update y actualizadaEn queda como estaba.
		return ComunaAdminDto.desde(comunas.saveAndFlush(comuna));
	}

	// El tipo se decide por el contenido del archivo, nunca por lo que declara el cliente. Solo PNG y JPEG:
	// un SVG puede llevar scripts, así que no se acepta. Además de la firma, la imagen se abre de verdad
	// (ImagenUtil): un archivo con la firma correcta y basura adentro se rechaza.
	@Transactional
	public ComunaAdminDto cambiarLogo(Long id, byte[] imagen) {
		if (!comunas.existsById(id)) {
			throw noEncontrada();
		}
		if (imagen == null || imagen.length == 0 || imagen.length > LOGO_MAXIMO_BYTES) {
			throw logoInvalido();
		}
		String tipo = ImagenUtil.tipoSiEsValida(imagen, LOGO_LADO_MAXIMO_PX);
		if (tipo == null) {
			throw logoInvalido();
		}
		if (logos.guardar(id, imagen, tipo) == 0) {
			throw noEncontrada();
		}
		comunas.tocar(id, Instant.now());
		return ComunaAdminDto.desde(comunas.findById(id).orElseThrow(ComunaService::noEncontrada));
	}

	// Devuelve el logo solo si la comuna está activa y tiene uno; si no, el mismo 404 que el resto de la consulta
	// pública. Es el único lugar donde se leen los bytes de la imagen.
	@Transactional(readOnly = true)
	public ComunaLogo buscarLogoDeComunaActiva(String slug) {
		Comuna comuna = buscarActivaPorSlug(slug);
		return logos.findById(comuna.getId())
				.filter(logo -> logo.getLogo() != null)
				.orElseThrow(ComunaService::noEncontrada);
	}

	// Carga o reemplaza el límite geográfico (MC-16). Se valida el contenido de verdad (LimiteGeoJson) y se guarda solo
	// la figura normalizada, sin las propiedades del archivo. La comuna se busca primero: un id que no existe es 404
	// aunque el archivo sea malo. Después se descarta la figura que el servidor tenía en memoria.
	@Transactional
	public ComunaAdminDto cambiarLimite(Long id, byte[] contenido) {
		if (!comunas.existsById(id)) {
			throw noEncontrada();
		}
		if (contenido == null || contenido.length > LIMITE_MAXIMO_BYTES) {
			throw limiteMuyGrande();
		}
		String figura = LimiteGeoJson.normalizar(contenido);
		Instant ahora = Instant.now();
		if (limites.guardar(id, figura, ahora) == 0) {
			throw noEncontrada();
		}
		comunas.tocar(id, ahora);
		limiteDeComuna.invalidar(id);
		return ComunaAdminDto.desde(comunas.findById(id).orElseThrow(ComunaService::noEncontrada));
	}

	// El límite de la comuna del usuario con sesión (la comuna sale del token). Vacío si no tiene límite cargado.
	@Transactional(readOnly = true)
	public Optional<String> limiteDeMiComuna() {
		return limites.findById(comunaActual.id())
				.map(com.micomuna.entities.ComunaLimite::getLimite)
				.filter(figura -> !figura.isBlank());
	}

	public static PeticionInvalidaException limiteMuyGrande() {
		return new PeticionInvalidaException(LimiteGeoJson.CODIGO, "El archivo pesa más de 1 MB. Simplificá el límite y volvé a subirlo.");
	}

	private static PeticionInvalidaException logoInvalido() {
		return new PeticionInvalidaException("LOGO_INVALIDO",
				"El logo tiene que ser una imagen PNG o JPG de hasta 512 KB y 2000 x 2000 píxeles");
	}

	private String generarEnlace(CrearComunaRequest pedido) {
		String slug;
		try {
			slug = SlugUtil.generar(pedido.nombre(), pedido.provincia());
		} catch (IllegalArgumentException e) {
			throw new PeticionInvalidaException("NOMBRE_INVALIDO", "El nombre tiene que tener letras o números");
		}
		if (!SlugUtil.esValido(slug)) {
			throw new PeticionInvalidaException("ENLACE_INVALIDO", "El enlace armado con ese nombre es demasiado largo");
		}
		return slug;
	}

	// Mismo error para "no existe" y "está inactiva", así no se revela cuáles existen.
	private static RecursoNoEncontradoException noEncontrada() {
		return new RecursoNoEncontradoException("No encontramos esa comuna");
	}
}
