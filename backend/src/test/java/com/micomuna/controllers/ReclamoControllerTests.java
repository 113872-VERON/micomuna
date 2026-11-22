package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.services.ReclamoService;
import com.micomuna.services.TurnosDeFotos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;

// MC-15 (subtarea MC-57): el vecino reporta un problema con descripción, ubicación y hasta 3 fotos.
// Contra la base de desarrollo, dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReclamoControllerTests {

	private static final String RUTA = "/api/reclamos";
	private static final String RUTA_TIPOS = "/api/tipos-reclamo";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	@Autowired
	private EntityManager em;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private TurnosDeFotos turnos;

	@Autowired
	private ReclamoService reclamoService;

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario vecinoA;
	private Usuario vecinoB;
	private String tokenVecinoA;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comunaA = comuna("t15-reclamos-a");
		comunaB = comuna("t15-reclamos-b");
		vecinoA = usuario(comunaA, "vecino-a@mail.com", Rol.VECINO);
		vecinoB = usuario(comunaB, "vecino-b@mail.com", Rol.VECINO);
		tokenVecinoA = jwt.generar(vecinoA);
	}

	// ---- Ayudas

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private String token(Comuna comuna, String correo, Rol rol) {
		return jwt.generar(usuario(comuna, correo, rol));
	}

	private static Map<String, String> validos() {
		Map<String, String> campos = new LinkedHashMap<>();
		campos.put("tipo", "BACHE");
		campos.put("descripcion", "Hay un pozo enorme en la esquina");
		campos.put("latitud", "-31.4200833");
		campos.put("longitud", "-64.1887761");
		return campos;
	}

	private static Map<String, String> con(Map<String, String> campos, String... claveValor) {
		Map<String, String> copia = new LinkedHashMap<>(campos);
		for (int i = 0; i < claveValor.length; i += 2) {
			if (claveValor[i + 1] == null) {
				copia.remove(claveValor[i]);
			} else {
				copia.put(claveValor[i], claveValor[i + 1]);
			}
		}
		return copia;
	}

	private static MockMultipartFile foto(byte[] bytes) {
		return new MockMultipartFile("fotos", "foto.jpg", "image/jpeg", bytes);
	}

	private ResultActions reportar(String token, Map<String, String> campos, MockMultipartFile... fotos) throws Exception {
		MockMultipartHttpServletRequestBuilder pedido = multipart(RUTA);
		campos.forEach(pedido::param);
		for (MockMultipartFile f : fotos) {
			pedido.file(f);
		}
		if (token != null) {
			pedido.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(pedido);
	}

	private JsonNode reportarBien(Map<String, String> campos, MockMultipartFile... fotos) throws Exception {
		String cuerpo = reportar(tokenVecinoA, campos, fotos)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(cuerpo);
	}

	private void esperarRechazo(Map<String, String> campos, String codigo, MockMultipartFile... fotos) throws Exception {
		reportar(tokenVecinoA, campos, fotos)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value(codigo));
		assertThat(reclamosDe(comunaA)).as("no se guardó ningún reclamo").isEmpty();
	}

	private void esperarCampoInvalido(Map<String, String> campos, String campo) throws Exception {
		reportar(tokenVecinoA, campos)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores." + campo).exists());
		assertThat(reclamosDe(comunaA)).isEmpty();
	}

	// Lo que quedó en la base de verdad, no lo que hay en memoria en la sesión de Hibernate.
	private void releer() {
		em.flush();
		em.clear();
	}

	private List<Reclamo> reclamosDe(Comuna comuna) {
		releer();
		return em.createQuery("select r from Reclamo r where r.comuna.id = :id order by r.numero", Reclamo.class)
				.setParameter("id", comuna.getId()).getResultList();
	}

	private Reclamo reclamo(JsonNode respuesta) {
		releer();
		return em.find(Reclamo.class, respuesta.get("id").asLong());
	}

	private List<ReclamoFoto> fotosDe(long idReclamo) {
		releer();
		return em.createQuery("select f from ReclamoFoto f where f.reclamo.id = :id order by f.orden", ReclamoFoto.class)
				.setParameter("id", idReclamo).getResultList();
	}

	private int contadorDe(Comuna comuna) {
		releer();
		Number valor = (Number) em.createNativeQuery("select ultimo_numero_reclamo from comuna where id = :id")
				.setParameter("id", comuna.getId()).getSingleResult();
		return valor.intValue();
	}

	// ---- Tipos de problema

	@Test
	void elVecinoVeLosSeisTiposEnOrdenConSuConsejo() throws Exception {
		mockMvc.perform(get(RUTA_TIPOS).header("Authorization", "Bearer " + tokenVecinoA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(6))
				.andExpect(jsonPath("$[*].codigo").value(contains("BACHE", "LUZ", "BASURA", "ARBOL", "AGUA", "OTRO")))
				.andExpect(jsonPath("$[*].nombre").value(contains("Bache o calle rota", "Luz apagada", "Basura",
						"Árbol o ramas", "Pérdida de agua o desagüe", "Otro problema")))
				.andExpect(jsonPath("$[0].consejoFoto").value(containsString("de cerca")))
				.andExpect(jsonPath("$[0].id").doesNotExist())
				.andExpect(jsonPath("$[0].orden").doesNotExist());
	}

	@Test
	void elPersonalDeLaComunaTambienVeLosTipos() throws Exception {
		for (Rol rol : List.of(Rol.AGENTE, Rol.ADMINISTRATIVO, Rol.ADMIN_COMUNA)) {
			mockMvc.perform(get(RUTA_TIPOS).header("Authorization", "Bearer " + token(comunaA, rol + "@mail.com", rol)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(6));
		}
	}

	@Test
	void elSuperadminNoVeLosTiposYSinSesionDa401() throws Exception {
		mockMvc.perform(get(RUTA_TIPOS).header("Authorization", "Bearer " + token(null, "root@mail.com", Rol.SUPERADMIN)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		mockMvc.perform(get(RUTA_TIPOS)).andExpect(status().isUnauthorized());
	}

	// ---- Alta

	@Test
	void reportaSinFotosConLaUbicacionDelGps() throws Exception {
		JsonNode creado = reportarBien(validos());

		assertThat(creado.get("numero").asInt()).isEqualTo(1);
		assertThat(creado.get("tipo").asText()).isEqualTo("BACHE");
		assertThat(creado.get("estado").asText()).isEqualTo("RECIBIDO");
		assertThat(creado.get("cantidadFotos").asInt()).isZero();
		assertThat(creado.get("creadoEn").asText()).isNotBlank();

		Reclamo guardado = reclamo(creado);
		assertThat(guardado.getComuna().getId()).isEqualTo(comunaA.getId());
		assertThat(guardado.getVecino().getId()).isEqualTo(vecinoA.getId());
		assertThat(guardado.getTipo().getCodigo()).isEqualTo("BACHE");
		assertThat(guardado.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
		assertThat(guardado.getDescripcion()).isEqualTo("Hay un pozo enorme en la esquina");
		// Se guardan 6 decimales.
		assertThat(guardado.getLatitud()).isEqualByComparingTo(new BigDecimal("-31.420083"));
		assertThat(guardado.getLongitud()).isEqualByComparingTo(new BigDecimal("-64.188776"));
		assertThat(guardado.getReferencia()).isNull();
		assertThat(guardado.getCreadoEn()).isNotNull();
		assertThat(guardado.getActualizadoEn()).isEqualTo(guardado.getCreadoEn());
		assertThat(fotosDe(guardado.getId())).isEmpty();
	}

	@Test
	void unaReferenciaSolaNoAlcanzaSinCoordenadas() throws Exception {
		// Desde MC-16 la ubicación es el punto del mapa; la referencia escrita es opcional y no la reemplaza.
		esperarRechazo(con(validos(), "latitud", null, "longitud", null, "referencia", "Frente a la panadería"),
				"UBICACION_REQUERIDA");
	}

	@Test
	void reportaConGpsYReferenciaALaVez() throws Exception {
		Reclamo guardado = reclamo(reportarBien(con(validos(), "referencia", "Al lado del kiosco")));

		assertThat(guardado.getLatitud()).isNotNull();
		assertThat(guardado.getReferencia()).isEqualTo("Al lado del kiosco");
	}

	@Test
	void reportaConTresFotosQueSeGuardanEnOrden() throws Exception {
		JsonNode creado = reportarBien(validos(),
				foto(ImagenesDePrueba.jpeg(64, 48)),
				new MockMultipartFile("fotos", "captura.png", "image/png", ImagenesDePrueba.pngConTransparencia(30, 20)),
				foto(ImagenesDePrueba.jpeg(20, 30)));

		assertThat(creado.get("cantidadFotos").asInt()).isEqualTo(3);
		List<ReclamoFoto> fotos = fotosDe(creado.get("id").asLong());
		assertThat(fotos).extracting(ReclamoFoto::getOrden).containsExactly(1, 2, 3);
		assertThat(fotos).extracting(ReclamoFoto::getImagenTipo).containsExactly("image/jpeg", "image/png", "image/jpeg");
		assertThat(fotos).allSatisfy(f -> assertThat(f.getCreadaEn()).isNotNull());
		BufferedImage primera = ImagenesDePrueba.leer(fotos.get(0).getImagen());
		assertThat(primera.getWidth()).isEqualTo(64);
		assertThat(primera.getHeight()).isEqualTo(48);
		BufferedImage segunda = ImagenesDePrueba.leer(fotos.get(1).getImagen());
		assertThat(segunda.getWidth()).isEqualTo(30);
		assertThat(segunda.getColorModel().hasAlpha()).as("el PNG conserva la transparencia").isTrue();
	}

	// El tipo de la foto se decide por el contenido: un PNG que el cliente declara como JPEG se guarda como PNG.
	@Test
	void elTipoDeLaFotoSeDecidePorElContenido() throws Exception {
		JsonNode creado = reportarBien(validos(),
				new MockMultipartFile("fotos", "foto.jpg", "image/jpeg", ImagenesDePrueba.png(10, 10)));

		assertThat(fotosDe(creado.get("id").asLong())).extracting(ReclamoFoto::getImagenTipo).containsExactly("image/png");
	}

	@Test
	void elNumeroEsCorrelativoDentroDeCadaComuna() throws Exception {
		assertThat(reportarBien(validos()).get("numero").asInt()).isEqualTo(1);
		assertThat(reportarBien(validos()).get("numero").asInt()).isEqualTo(2);
		assertThat(reportarBien(validos()).get("numero").asInt()).isEqualTo(3);

		String cuerpo = reportar(jwt.generar(vecinoB), validos())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.numero").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(reclamosDe(comunaA)).extracting(Reclamo::getNumero).containsExactly(1, 2, 3);
		assertThat(reclamo(json.readTree(cuerpo)).getComuna().getId()).isEqualTo(comunaB.getId());
		assertThat(contadorDe(comunaA)).isEqualTo(3);
		assertThat(contadorDe(comunaB)).isEqualTo(1);
	}

	// Cambiar otros datos de la comuna no pisa el contador, aunque la entidad Comuna no lo tenga mapeado.
	@Test
	void guardarLaComunaNoPisaElContador() throws Exception {
		reportarBien(validos());
		reportarBien(validos());

		Comuna c = comunas.findById(comunaA.getId()).orElseThrow();
		c.setNombre("Otro nombre");
		comunas.saveAndFlush(c);

		assertThat(contadorDe(comunaA)).isEqualTo(2);
		assertThat(reportarBien(validos()).get("numero").asInt()).isEqualTo(3);
	}

	@Test
	void elAltaDejaLaPrimeraFilaDelHistorial() throws Exception {
		long id = reportarBien(validos()).get("id").asLong();

		releer();
		List<ReclamoEstado> historial = em
				.createQuery("select h from ReclamoEstado h where h.reclamo.id = :id", ReclamoEstado.class)
				.setParameter("id", id).getResultList();
		assertThat(historial).hasSize(1);
		ReclamoEstado primera = historial.get(0);
		assertThat(primera.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
		assertThat(primera.getEstadoAnterior()).isNull();
		assertThat(primera.getUsuario().getId()).isEqualTo(vecinoA.getId());
		assertThat(primera.getObservacion()).isNull();
		assertThat(primera.getCambiadoEn()).isNotNull();
	}

	@Test
	void laComunaYElVecinoSalenDelTokenAunqueElPedidoLosTraiga() throws Exception {
		String otra = String.valueOf(comunaB.getId());
		String otroVecino = String.valueOf(vecinoB.getId());
		JsonNode creado = reportarBien(con(validos(), "comuna", otra, "idComuna", otra, "comunaId", otra,
				"vecino", otroVecino, "idVecino", otroVecino, "numero", "99", "estado", "RESUELTO"));

		assertThat(creado.get("numero").asInt()).isEqualTo(1);
		assertThat(creado.get("estado").asText()).isEqualTo("RECIBIDO");
		Reclamo guardado = reclamo(creado);
		assertThat(guardado.getComuna().getId()).isEqualTo(comunaA.getId());
		assertThat(guardado.getVecino().getId()).isEqualTo(vecinoA.getId());
		assertThat(reclamosDe(comunaB)).isEmpty();
	}

	@Test
	void unaDescripcionDe500CaracteresConTildesEntra() throws Exception {
		String larga = "ñ".repeat(500);
		assertThat(reclamo(reportarBien(con(validos(), "descripcion", larga))).getDescripcion()).isEqualTo(larga);
	}

	@Test
	void aceptaFotosDeHasta2MBYHasta2560PixelesDeLado() throws Exception {
		// Los bytes de relleno después del final de la imagen no impiden abrirla: sirven para llegar al peso exacto.
		JsonNode creado = reportarBien(validos(),
				foto(Arrays.copyOf(ImagenesDePrueba.jpeg(16, 16), 2 * 1024 * 1024)),
				foto(ImagenesDePrueba.png(2560, 8)),
				foto(ImagenesDePrueba.jpeg(8, 2560)));

		assertThat(creado.get("cantidadFotos").asInt()).isEqualTo(3);
	}

	// ---- Las fotos no conservan sus datos ocultos

	@Test
	void laFotoGuardadaNoConservaElExif() throws Exception {
		byte[] original = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(40, 30), 1, "GPS -31.42 -64.18 SECRETO");
		assertThat(ImagenesDePrueba.contiene(original, "Exif")).as("la foto de prueba trae EXIF").isTrue();
		assertThat(ImagenesDePrueba.contiene(original, "SECRETO")).isTrue();
		assertThat(ImagenesDePrueba.leer(original)).as("la foto de prueba se puede abrir").isNotNull();

		JsonNode creado = reportarBien(validos(), foto(original));

		byte[] guardada = fotosDe(creado.get("id").asLong()).get(0).getImagen();
		assertThat(ImagenesDePrueba.contiene(guardada, "Exif")).isFalse();
		assertThat(ImagenesDePrueba.contiene(guardada, "SECRETO")).isFalse();
		assertThat(guardada[0] & 0xFF).isEqualTo(0xFF);
		assertThat(guardada[1] & 0xFF).isEqualTo(0xD8);
		BufferedImage imagen = ImagenesDePrueba.leer(guardada);
		assertThat(imagen.getWidth()).isEqualTo(40);
		assertThat(imagen.getHeight()).isEqualTo(30);
	}

	// La orientación de la cámara viaja en el EXIF: se aplica antes de descartarlo para que la foto no quede acostada.
	// Orientación 6 = hay que girarla 90° a la derecha: lo que estaba a la izquierda (rojo) queda arriba.
	@Test
	void laFotoQuedaDerechaAunqueSeDescarteLaOrientacionDelExif() throws Exception {
		byte[] original = ImagenesDePrueba.conExif(ImagenesDePrueba.jpegDosColores(40, 20), 6, "camara");

		JsonNode creado = reportarBien(validos(), foto(original));

		byte[] guardada = fotosDe(creado.get("id").asLong()).get(0).getImagen();
		assertThat(ImagenesDePrueba.contiene(guardada, "Exif")).isFalse();
		BufferedImage imagen = ImagenesDePrueba.leer(guardada);
		assertThat(imagen.getWidth()).isEqualTo(20);
		assertThat(imagen.getHeight()).isEqualTo(40);
		assertThat(predomina(imagen.getRGB(10, 5))).isEqualTo("rojo");
		assertThat(predomina(imagen.getRGB(10, 35))).isEqualTo("azul");
	}

	@Test
	void elPngGuardadoNoConservaSusBloquesDeTexto() throws Exception {
		byte[] original = ImagenesDePrueba.conTextoPng(ImagenesDePrueba.png(12, 12), "Comment", "DATO-OCULTO");
		assertThat(ImagenesDePrueba.leer(original)).as("el PNG de prueba se puede abrir").isNotNull();

		JsonNode creado = reportarBien(validos(), foto(original));

		byte[] guardada = fotosDe(creado.get("id").asLong()).get(0).getImagen();
		assertThat(ImagenesDePrueba.contiene(original, "DATO-OCULTO")).isTrue();
		assertThat(ImagenesDePrueba.contiene(guardada, "DATO-OCULTO")).isFalse();
		assertThat(ImagenesDePrueba.leer(guardada).getWidth()).isEqualTo(12);
	}

	private static String predomina(int rgb) {
		Color c = new Color(rgb);
		return c.getRed() > c.getBlue() ? "rojo" : "azul";
	}

	// ---- Rechazos: datos del formulario (VALIDACION)

	@Test
	void laDescripcionEsObligatoriaYNoPuedeSerSoloEspacios() throws Exception {
		esperarCampoInvalido(con(validos(), "descripcion", null), "descripcion");
		esperarCampoInvalido(con(validos(), "descripcion", ""), "descripcion");
		esperarCampoInvalido(con(validos(), "descripcion", "   \t  "), "descripcion");
	}

	@Test
	void laDescripcionNoPuedeSuperarLos500Caracteres() throws Exception {
		reportar(tokenVecinoA, con(validos(), "descripcion", "a".repeat(501)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.descripcion").value("La descripción puede tener hasta 500 caracteres. Acortala un poco"));
	}

	@Test
	void laReferenciaNoPuedeSuperarLos200Caracteres() throws Exception {
		esperarCampoInvalido(con(validos(), "referencia", "r".repeat(201)), "referencia");
	}

	@Test
	void elTipoEsObligatorio() throws Exception {
		esperarCampoInvalido(con(validos(), "tipo", null), "tipo");
	}

	@Test
	void laLatitudYLaLongitudVanJuntas() throws Exception {
		reportar(tokenVecinoA, con(validos(), "longitud", null, "referencia", "Frente a la plaza"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.longitud").value(containsString("tocá «Usar mi ubicación» de nuevo")));
		esperarCampoInvalido(con(validos(), "latitud", null), "latitud");
	}

	@Test
	void laLatitudYLaLongitudTienenQueEstarEnRango() throws Exception {
		esperarCampoInvalido(con(validos(), "latitud", "90.000001"), "latitud");
		esperarCampoInvalido(con(validos(), "latitud", "-90.5"), "latitud");
		esperarCampoInvalido(con(validos(), "longitud", "180.000001"), "longitud");
		esperarCampoInvalido(con(validos(), "longitud", "-181"), "longitud");
		esperarCampoInvalido(con(validos(), "latitud", "NaN"), "latitud");
		esperarCampoInvalido(con(validos(), "longitud", "Infinity"), "longitud");
	}

	@Test
	void unaCoordenadaQueNoEsUnNumeroDaUnMensajeEnCastellano() throws Exception {
		reportar(tokenVecinoA, con(validos(), "latitud", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.latitud").value("El valor no es válido"));
	}

	@Test
	void losExtremosDelRangoSonValidos() throws Exception {
		reportarBien(con(validos(), "latitud", "-90", "longitud", "180"));
		reportarBien(con(validos(), "latitud", "90", "longitud", "-180"));
	}

	// ---- Rechazos: reglas del reclamo

	@Test
	void sinLatitudNiLongitudDa400UbicacionRequerida() throws Exception {
		esperarRechazo(con(validos(), "latitud", null, "longitud", null), "UBICACION_REQUERIDA");
		esperarRechazo(con(validos(), "latitud", null, "longitud", null, "referencia", "    "), "UBICACION_REQUERIDA");
		esperarRechazo(con(validos(), "latitud", null, "longitud", null, "referencia", "Frente a la plaza"), "UBICACION_REQUERIDA");
	}

	@Test
	void unTipoQueNoExisteDa400TipoInvalido() throws Exception {
		esperarRechazo(con(validos(), "tipo", "VOLCAN"), "TIPO_INVALIDO");
		esperarRechazo(con(validos(), "tipo", "X".repeat(20)), "TIPO_INVALIDO");
		reportar(tokenVecinoA, con(validos(), "tipo", "VOLCAN"))
				.andExpect(jsonPath("$.detail").value("Elegí uno de los tipos de problema de la lista."));
	}

	// ---- Rechazos: fotos (FOTO_INVALIDA)

	@Test
	void unaCuartaFotoDa400() throws Exception {
		byte[] jpeg = ImagenesDePrueba.jpeg(8, 8);
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(jpeg), foto(jpeg), foto(jpeg), foto(jpeg));
		reportar(tokenVecinoA, validos(), foto(jpeg), foto(jpeg), foto(jpeg), foto(jpeg))
				.andExpect(jsonPath("$.detail").value("Podés mandar hasta 3 fotos. Sacá alguna y probá de nuevo."));
	}

	@Test
	void unGifDa400AunqueSeDeclareComoJpeg() throws Exception {
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(ImagenesDePrueba.gif(8, 8)));
	}

	@Test
	void unSvgDa400() throws Exception {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
		esperarRechazo(validos(), "FOTO_INVALIDA", new MockMultipartFile("fotos", "foto.png", "image/png", svg));
	}

	@Test
	void unPngConBasuraAdentroDa400() throws Exception {
		byte[] firma = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
		byte[] falso = Arrays.copyOf(firma, 300);
		Arrays.fill(falso, firma.length, falso.length, (byte) 0x41);
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(falso));
	}

	@Test
	void unaFotoCortadaPorLaMitadDa400() throws Exception {
		byte[] entera = ImagenesDePrueba.jpeg(300, 300);
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(Arrays.copyOf(entera, entera.length / 2)));
	}

	@Test
	void unaFotoDeMasDe2560PixelesDeLadoDa400() throws Exception {
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(ImagenesDePrueba.png(2561, 8)));
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(ImagenesDePrueba.jpeg(8, 2561)));
	}

	@Test
	void unaFotoVaciaDa400() throws Exception {
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(new byte[0]));
	}

	@Test
	void unaFotoDeMasDe2MBDa400() throws Exception {
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(Arrays.copyOf(ImagenesDePrueba.jpeg(16, 16), 2 * 1024 * 1024 + 1)));
	}

	// Si una de las fotos está mal no se guarda nada, ni siquiera las otras ni el número.
	@Test
	void unaFotoMalaEntreBuenasNoGuardaNadaNiGastaElNumero() throws Exception {
		reportar(tokenVecinoA, validos(), foto(ImagenesDePrueba.jpeg(8, 8)), foto(ImagenesDePrueba.gif(8, 8)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"))
				.andExpect(jsonPath("$.detail").value("No pudimos usar la foto 2. Sacá otra o elegí una distinta."));

		assertThat(reclamosDe(comunaA)).isEmpty();
		assertThat(contadorDe(comunaA)).isZero();
		assertThat(reportarBien(validos()).get("numero").asInt()).isEqualTo(1);
	}

	// ---- Quién puede reportar

	@Test
	void soloElVecinoPuedeReportar() throws Exception {
		List<String> otros = List.of(
				token(comunaA, "agente@mail.com", Rol.AGENTE),
				token(comunaA, "administrativo@mail.com", Rol.ADMINISTRATIVO),
				token(comunaA, "admin@mail.com", Rol.ADMIN_COMUNA),
				token(null, "root@mail.com", Rol.SUPERADMIN));
		for (String otro : otros) {
			reportar(otro, validos())
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			// Con un formulario inválido también es 403: el permiso se revisa antes que los datos.
			reportar(otro, Map.of())
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		}
		reportar(null, validos()).andExpect(status().isUnauthorized());
		reportar(null, Map.of()).andExpect(status().isUnauthorized());

		assertThat(reclamosDe(comunaA)).isEmpty();
		assertThat(contadorDe(comunaA)).isZero();
	}

	@Test
	void unPedidoQueNoEsMultipartDa415() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(RUTA)
				.header("Authorization", "Bearer " + tokenVecinoA)
				.contentType("application/json").content("{\"tipo\":\"BACHE\"}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.codigo").value(not("ERROR_INTERNO")));
	}

	// ---- Las fotos no viajan con el reclamo

	// Como con el logo de la comuna: la entidad Reclamo no tiene ningún campo con bytes ni una lista de fotos.
	// Si alguien la agrega, esta prueba falla.
	@Test
	void laEntidadReclamoNoTieneLasFotos() {
		assertThat(em.getMetamodel().entity(Reclamo.class).getAttributes())
				.noneMatch(atributo -> atributo.getJavaType() == byte[].class)
				.noneMatch(Attribute::isCollection)
				.noneMatch(atributo -> atributo.getJavaType() == ReclamoFoto.class);
	}

	// Y en la práctica: cargar reclamos (uno solo o una lista, recorriendo sus relaciones) no trae ninguna foto
	// a la sesión de Hibernate, así que tampoco sus bytes.
	@Test
	void cargarReclamosNoTraeLasFotos() throws Exception {
		byte[] jpeg = ImagenesDePrueba.jpeg(32, 32);
		long id = reportarBien(validos(), foto(jpeg), foto(jpeg)).get("id").asLong();
		reportarBien(validos(), foto(jpeg));

		releer();
		Reclamo uno = em.find(Reclamo.class, id);
		assertThat(uno.getTipo().getCodigo()).isEqualTo("BACHE");
		assertThat(uno.getComuna().getSlug()).isEqualTo("t15-reclamos-a");
		assertThat(uno.getVecino().getCorreo()).isEqualTo("vecino-a@mail.com");
		List<Reclamo> lista = em.createQuery("select r from Reclamo r where r.comuna.id = :id", Reclamo.class)
				.setParameter("id", comunaA.getId()).getResultList();
		assertThat(lista).hasSize(2);

		assertThat(entidadesCargadas()).contains(Reclamo.class).doesNotContain(ReclamoFoto.class);
		// Control: leer las fotos sí las trae.
		assertThat(fotosDe(id)).hasSize(2);
		assertThat(entidadesCargadas()).contains(ReclamoFoto.class);
	}

	// ---- Segunda tanda (revisiones de seguridad y funcional)

	@Test
	void latitudNaNYLongitudInfinityDan400Validacion() throws Exception {
		esperarCampoInvalido(con(validos(), "latitud", "NaN"), "latitud");
		esperarCampoInvalido(con(validos(), "longitud", "Infinity"), "longitud");
		esperarCampoInvalido(con(validos(), "longitud", "-Infinity"), "longitud");
	}

	@Test
	void unTipoDe21CaracteresDa400Validacion() throws Exception {
		esperarCampoInvalido(con(validos(), "tipo", "X".repeat(21)), "tipo");
	}

	@Test
	void losMensajesDeUbicacionYDeFotosDicenQueHacerSinPalabrasTecnicas() throws Exception {
		reportar(tokenVecinoA, con(validos(), "latitud", null, "longitud", null))
				.andExpect(jsonPath("$.detail").value("Marcá en el mapa dónde está el problema o tocá «Usar mi ubicación»."));
		String detalle = json.readTree(reportar(tokenVecinoA, validos(), foto(ImagenesDePrueba.gif(8, 8)))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString()).get("detail").asText();
		assertThat(detalle).isEqualTo("No pudimos usar la foto 1. Sacá otra o elegí una distinta.");
		assertThat(detalle.toLowerCase()).doesNotContain("jpg", "png", "píxel", "mapa");
		reportar(tokenVecinoA, validos(), foto(Arrays.copyOf(ImagenesDePrueba.jpeg(8, 8), 2 * 1024 * 1024 + 1)))
				.andExpect(jsonPath("$.detail").value("La foto 1 es muy pesada. Sacá otra o elegí una distinta."));
	}

	// ---- Memoria al abrir una foto (tope de 40 MiB por foto, contando la copia para girarla)

	// 2560 x 2560 x 8 bytes (16 bits por canal, con transparencia) = 50 MiB: se rechaza sin llegar a abrirla.
	@Test
	void unPng16BitsConTransparenciaQueOcuparaDemasiadaMemoriaSeRechaza() throws Exception {
		byte[] png = ImagenesDePrueba.png16ConTransparencia(2560, 2560);
		assertThat(png.length).as("el archivo es liviano").isLessThan(2 * 1024 * 1024);
		esperarRechazo(validos(), "FOTO_INVALIDA", foto(png));
	}

	// 2560 x 1920 x 8 bytes = 37,5 MiB: entra en el tope, y el PNG de 16 bits se vuelve a armar bien.
	@Test
	void unPng16BitsDentroDelTopeDeMemoriaSeAcepta() throws Exception {
		JsonNode creado = reportarBien(validos(), foto(ImagenesDePrueba.png16ConTransparencia(2560, 1920)));

		BufferedImage guardada = ImagenesDePrueba.leer(fotosDe(creado.get("id").asLong()).get(0).getImagen());
		assertThat(guardada.getWidth()).isEqualTo(2560);
		assertThat(guardada.getColorModel().hasAlpha()).isTrue();
	}

	// El caso legítimo más grande: un JPEG de 2560 x 2560 que hay que girar (18,75 MiB + 18,75 MiB de la copia).
	@Test
	void elJpegMasGrandeQueHayQueGirarEntraEnElTopeDeMemoria() throws Exception {
		byte[] original = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(2560, 2560), 6, "camara");
		assertThat(original.length).isLessThan(2 * 1024 * 1024);

		assertThat(reportarBien(validos(), foto(original)).get("cantidadFotos").asInt()).isEqualTo(1);
	}

	// ---- JPEG progresivo con demasiadas pasadas

	@Test
	void unJpegProgresivoComunSeAcepta() throws Exception {
		byte[] progresivo = ImagenesDePrueba.jpegProgresivo(64, 64);
		assertThat(ImagenesDePrueba.pasadas(progresivo)).as("es progresivo").isBetween(2, 30);

		reportarBien(validos(), foto(progresivo));
	}

	@Test
	void unJpegConMasDe30PasadasSeRechaza() throws Exception {
		byte[] progresivo = ImagenesDePrueba.jpegProgresivo(64, 64);
		byte[] conMuchas = ImagenesDePrueba.conPasadasRepetidas(progresivo, 31 - ImagenesDePrueba.pasadas(progresivo));
		assertThat(ImagenesDePrueba.pasadas(conMuchas)).isEqualTo(31);
		// Control: con exactamente 30 pasadas el archivo se abre y esta regla no lo rechaza.
		byte[] justo = ImagenesDePrueba.conPasadasRepetidas(progresivo, 30 - ImagenesDePrueba.pasadas(progresivo));
		assertThat(ImagenesDePrueba.leer(justo)).as("repetir la última pasada no rompe el archivo").isNotNull();
		reportarBien(validos(), foto(justo));

		reportar(tokenVecinoA, validos(), foto(conMuchas))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"));
	}

	// ---- Peso de la foto limpia

	// Ruido guardado con calidad 0,3 pesa menos de 2 MB; vuelto a armar con calidad 0,9 pasa los 3 MB.
	@Test
	void unaFotoQueLimpiaPesariaMasDe3MBSeRechaza() throws Exception {
		byte[] ruido = ImagenesDePrueba.jpegRuidoConPocaCalidad(2560, 1920, 0.3f);
		assertThat(ruido.length).isLessThan(2 * 1024 * 1024);

		reportar(tokenVecinoA, validos(), foto(ruido))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"))
				.andExpect(jsonPath("$.detail").value("La foto 1 es muy pesada. Sacá otra o elegí una distinta."));
		assertThat(reclamosDe(comunaA)).isEmpty();
	}

	// ---- Turnos para procesar fotos

	@Test
	void conTodosLosTurnosOcupadosResponde503EnUnosCincoSegundos() throws Exception {
		Semaphore semaforo = (Semaphore) ReflectionTestUtils.getField(turnos, "turnos");
		semaforo.acquire(3);
		try {
			long inicio = System.nanoTime();
			reportar(tokenVecinoA, validos(), foto(ImagenesDePrueba.jpeg(8, 8)))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.codigo").value("OCUPADO"))
					.andExpect(jsonPath("$.detail").value(
							"Estamos procesando muchas fotos en este momento. Probá de nuevo en unos segundos."));
			assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isBetween(Duration.ofMillis(4500), Duration.ofSeconds(10));
			// Un reclamo sin fotos no necesita turno.
			reportarBien(validos());
		} finally {
			semaforo.release(3);
		}
		assertThat(reclamosDe(comunaA)).hasSize(1);
	}

	@Test
	void elTurnoSeDevuelveSiempreAunqueLaFotoSeaRechazada() throws Exception {
		Semaphore semaforo = (Semaphore) ReflectionTestUtils.getField(turnos, "turnos");
		reportar(tokenVecinoA, validos(), foto(ImagenesDePrueba.gif(8, 8))).andExpect(status().isBadRequest());
		reportarBien(validos(), foto(ImagenesDePrueba.jpeg(8, 8)));

		assertThat(semaforo.availablePermits()).isEqualTo(3);
	}

	// ---- Límite de reclamos por vecino

	private void reclamoGuardadoHace(Duration antiguedad, int numero) {
		TipoReclamo bache = em.createQuery("select t from TipoReclamo t where t.codigo = :codigo", TipoReclamo.class)
				.setParameter("codigo", "BACHE").getSingleResult();
		Reclamo r = new Reclamo();
		r.setComuna(em.find(Comuna.class, comunaA.getId()));
		r.setNumero(numero);
		r.setVecino(em.find(Usuario.class, vecinoA.getId()));
		r.setTipo(bache);
		r.setDescripcion("Reclamo anterior");
		r.setReferencia("En la esquina");
		r.setCreadoEn(Instant.now().minus(antiguedad));
		em.persist(r);
		em.flush();
	}

	@Test
	void elUndecimoReclamoEnUnaHoraDa429YUnoDeHaceMasDeUnaHoraNoCuenta() throws Exception {
		for (int i = 0; i < 9; i++) {
			reclamoGuardadoHace(Duration.ofMinutes(5 + i), 1000 + i);
		}
		reclamoGuardadoHace(Duration.ofMinutes(61), 1100);

		// 9 en la última hora (el de hace 61 minutos no cuenta): el décimo entra.
		reportarBien(validos());
		// El undécimo no, y se rechaza antes de mirar las fotos (si no, sería FOTO_INVALIDA).
		reportar(tokenVecinoA, validos(), foto(ImagenesDePrueba.gif(8, 8)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_RECLAMOS"))
				.andExpect(jsonPath("$.detail").value("Ya mandaste muchos reclamos en la última hora. Probá de nuevo más tarde."));
		// El límite es por vecino: otro vecino de la misma comuna sigue pudiendo reportar.
		reportar(token(comunaA, "otro-vecino@mail.com", Rol.VECINO), validos()).andExpect(status().isCreated());
		assertThat(reclamosDe(comunaA)).hasSize(12);
	}

	// ---- Tiempo máximo de la transacción del alta

	@Test
	void laTransaccionDelAltaTieneUnTiempoMaximoDe10Segundos() {
		TransactionTemplate propia = (TransactionTemplate) ReflectionTestUtils.getField(reclamoService, "transaccion");
		assertThat(propia.getTimeout()).isEqualTo(10);
	}

	private List<Class<?>> entidadesCargadas() {
		return em.unwrap(SessionImplementor.class).getPersistenceContext().getEntitiesByKey().values().stream()
				.<Class<?>>map(Object::getClass).distinct().toList();
	}
}
