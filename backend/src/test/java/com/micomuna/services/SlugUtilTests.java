package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.micomuna.enums.Provincia;

class SlugUtilTests {

	@Test
	void armaElEnlaceConNombreYProvincia() {
		assertThat(SlugUtil.generar("Salsipuedes", Provincia.CORDOBA)).isEqualTo("salsipuedes-cordoba");
		assertThat(SlugUtil.generar("Salsipuedes", Provincia.NEUQUEN)).isEqualTo("salsipuedes-neuquen");
	}

	@Test
	void quitaTildesYDieresis() {
		assertThat(SlugUtil.generar("Villa María", Provincia.CORDOBA)).isEqualTo("villa-maria-cordoba");
		assertThat(SlugUtil.generar("Río Cuarto", Provincia.CORDOBA)).isEqualTo("rio-cuarto-cordoba");
		assertThat(SlugUtil.generar("Pehuajó", Provincia.BUENOS_AIRES)).isEqualTo("pehuajo-buenos-aires");
		assertThat(SlugUtil.generar("Ñandú", Provincia.CHACO)).isEqualTo("nandu-chaco");
	}

	@Test
	void usaGuionesParaLasProvinciasConVariasPalabras() {
		assertThat(SlugUtil.generar("Paraná", Provincia.ENTRE_RIOS)).isEqualTo("parana-entre-rios");
		assertThat(SlugUtil.generar("Ushuaia", Provincia.TIERRA_DEL_FUEGO)).isEqualTo("ushuaia-tierra-del-fuego");
	}

	@Test
	void reemplazaSimbolosYEspaciosRepetidosPorUnSoloGuion() {
		assertThat(SlugUtil.generar("  San   Pedro!!  ", Provincia.SALTA)).isEqualTo("san-pedro-salta");
		assertThat(SlugUtil.generar("Villa (Nueva) / Sur", Provincia.CORDOBA)).isEqualTo("villa-nueva-sur-cordoba");
		assertThat(SlugUtil.generar("--Mina--", Provincia.JUJUY)).isEqualTo("mina-jujuy");
	}

	@Test
	void conservaLosNumeros() {
		assertThat(SlugUtil.generar("Colonia 25 de Mayo", Provincia.LA_PAMPA)).isEqualTo("colonia-25-de-mayo-la-pampa");
	}

	@Test
	void unNombreSinLetrasNiNumerosNoSirve() {
		assertThatThrownBy(() -> SlugUtil.generar("!!! ???", Provincia.CORDOBA))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SlugUtil.generar("   ", Provincia.CORDOBA))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void todoSlugGeneradoEsValidoYNoSuperaElMaximo() {
		String nombreLargo = "a".repeat(100);

		for (Provincia p : Provincia.values()) {
			String slug = SlugUtil.generar(nombreLargo, p);
			assertThat(SlugUtil.esValido(slug)).as(slug).isTrue();
		}
	}

	@Test
	void reconoceLosEnlacesValidosEInvalidos() {
		assertThat(SlugUtil.esValido("salsipuedes-cordoba")).isTrue();
		assertThat(SlugUtil.esValido("a")).isTrue();
		assertThat(SlugUtil.esValido("Salsipuedes")).isFalse();
		assertThat(SlugUtil.esValido("a_b")).isFalse();
		assertThat(SlugUtil.esValido("-a")).isFalse();
		assertThat(SlugUtil.esValido("a-")).isFalse();
		assertThat(SlugUtil.esValido("a--b")).isFalse();
		assertThat(SlugUtil.esValido("")).isFalse();
		assertThat(SlugUtil.esValido(null)).isFalse();
		assertThat(SlugUtil.esValido("a".repeat(121))).isFalse();
	}
}
