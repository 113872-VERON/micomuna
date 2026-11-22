package com.micomuna.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Sin internet: el servidor de Nominatim es un MockRestServiceServer.
@ExtendWith(OutputCaptureExtension.class)
class NominatimClientTests {

	private static final BigDecimal LAT = new BigDecimal("-31.42008");
	private static final BigDecimal LON = new BigDecimal("-64.18878");

	private MockRestServiceServer servidor;
	private NominatimClient cliente;

	@BeforeEach
	void preparar() {
		cliente = clienteCon("");
	}

	private NominatimClient clienteCon(String contacto) {
		RestClient.Builder constructor = RestClient.builder().baseUrl("http://nominatim.prueba");
		servidor = MockRestServiceServer.bindTo(constructor).build();
		return new NominatimClient(constructor.build(), contacto);
	}

	private void responder(String json) {
		servidor.expect(method(HttpMethod.GET)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	@Test
	void armaLaDireccionConCalleNumeroBarrioYLocalidad() {
		servidor.expect(requestTo(startsWith("http://nominatim.prueba/reverse?")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(queryParam("format", "jsonv2"))
				.andExpect(queryParam("lat", "-31.42008"))
				.andExpect(queryParam("lon", "-64.18878"))
				.andExpect(queryParam("accept-language", "es"))
				.andExpect(queryParam("zoom", "18"))
				.andRespond(withSuccess("""
						{"display_name":"120, Avenida San Martín, Barrio Centro, Agua de Oro, Departamento Colón, Córdoba, X5000, Argentina",
						 "address":{"house_number":"120","road":"Avenida San Martín","suburb":"Barrio Centro",
						            "village":"Agua de Oro","county":"Departamento Colón","state":"Córdoba","country":"Argentina"}}
						""", MediaType.APPLICATION_JSON));

		assertThat(cliente.direccionDe(LAT, LON)).contains("Avenida San Martín 120, Barrio Centro, Agua de Oro");
		servidor.verify();
	}

	@Test
	void sinNumeroNiBarrioUsaCalleYLocalidad() {
		responder("{\"address\":{\"road\":\"Calle Los Aromos\",\"town\":\"Salsipuedes\"}}");
		assertThat(cliente.direccionDe(LAT, LON)).contains("Calle Los Aromos, Salsipuedes");
	}

	@Test
	void seIdentificaConElNombreDeLaAppSinDatosPersonales() {
		servidor.expect(header("User-Agent", "MiComuna/0.1 (trabajo final, UTN)"))
				.andRespond(withSuccess("{\"address\":{\"road\":\"Calle 1\"}}", MediaType.APPLICATION_JSON));
		assertThat(cliente.direccionDe(LAT, LON)).isPresent();
		servidor.verify();
	}

	@Test
	void conContactoConfiguradoLoAgregaAlUserAgentYNoALaUrl() {
		cliente = clienteCon("alguien@ejemplo.test");
		servidor.expect(header("User-Agent", "MiComuna/0.1 (trabajo final, UTN; alguien@ejemplo.test)"))
				.andExpect(request -> assertThat(request.getURI().toString()).doesNotContain("alguien").doesNotContain("email"))
				.andRespond(withSuccess("{\"address\":{\"road\":\"Calle 1\"}}", MediaType.APPLICATION_JSON));
		assertThat(cliente.direccionDe(LAT, LON)).isPresent();
		servidor.verify();
	}

	@Test
	void sinAddressUsaElDisplayNameRecortadoA300Caracteres() {
		responder("{\"display_name\":\"" + "x".repeat(500) + "\"}");
		Optional<String> direccion = cliente.direccionDe(LAT, LON);
		assertThat(direccion).isPresent();
		assertThat(direccion.get()).hasSize(300);
	}

	@Test
	void unAddressSinCamposUtilesCaeAlDisplayName() {
		responder("{\"display_name\":\"Ruta 20, Córdoba\",\"address\":{\"country\":\"Argentina\"}}");
		assertThat(cliente.direccionDe(LAT, LON)).contains("Ruta 20, Córdoba");
	}

	@Test
	void laDireccionArmadaTambienSeRecortaA300() {
		responder("{\"address\":{\"road\":\"" + "c".repeat(400) + "\",\"house_number\":\"1\"}}");
		assertThat(cliente.direccionDe(LAT, LON).orElseThrow()).hasSize(300);
	}

	@Test
	void tratalaRespuestaComoTextoLimpiaControlesEIgnoraLoQueNoEsTexto() {
		responder("{\"address\":{\"road\":\"Calle\\u0000 \\n Falsa\\t<script>alert(1)</script>\",\"house_number\":123,"
				+ "\"village\":{\"x\":1}},\"extra\":{\"anidado\":[1,2,3]}}");
		// El número y la localidad no son texto: se ignoran. Los controles se vuelven un espacio. Nada se interpreta.
		assertThat(cliente.direccionDe(LAT, LON)).contains("Calle Falsa <script>alert(1)</script>");
	}

	@Test
	void sinResultadoDevuelveVacio() {
		responder("{\"error\":\"Unable to geocode\"}");
		assertThat(cliente.direccionDe(LAT, LON)).isEmpty();
	}

	@Test
	void unaRespuestaQueNoEsJsonODeOtraFormaDevuelveVacio() {
		for (String cuerpo : new String[] { "<html>nada</html>", "[]", "" }) {
			cliente = clienteCon("");
			responder(cuerpo);
			assertThat(cliente.direccionDe(LAT, LON)).as(cuerpo).isEmpty();
		}
	}

	@Test
	void unErrorDelServidorDevuelveVacio() {
		servidor.expect(method(HttpMethod.GET)).andRespond(withServerError());
		assertThat(cliente.direccionDe(LAT, LON)).isEmpty();
	}

	@Test
	void unTiempoVencidoDevuelveVacioYSeAvisaSinLasCoordenadasExactas(CapturedOutput salida) {
		cliente = clienteCon("alguien@ejemplo.test");
		servidor.expect(method(HttpMethod.GET)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThat(cliente.direccionDe(LAT, LON)).isEmpty();

		assertThat(salida.getAll()).contains("WARN").contains("(-31.42, -64.19)")
				.doesNotContain("-31.42008").doesNotContain("-64.18878").doesNotContain("alguien@ejemplo.test");
	}
}
