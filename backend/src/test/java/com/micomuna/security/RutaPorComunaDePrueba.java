package com.micomuna.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.entities.Usuario;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.UsuarioRepository;

import lombok.RequiredArgsConstructor;

// Endpoints solo para las pruebas: muestran el patrón que van a seguir los endpoints de negocio por comuna.
// La comuna sale siempre de ComunaActual (el token); el slug, el id de comuna del pedido y el cuerpo se ignoran.
@RestController
@RequiredArgsConstructor
class RutaPorComunaDePrueba {

	record CambioNombre(String nombre, Long idComuna) {
	}

	private final ComunaActual comunaActual;
	private final UsuarioRepository usuarios;

	@GetMapping("/api/prueba/{slug}/comuna-actual")
	String comuna(@PathVariable String slug, @RequestParam(required = false) Long idComuna) {
		return String.valueOf(comunaActual.id());
	}

	@GetMapping("/api/prueba/usuarios/{id}")
	String leer(@PathVariable Long id) {
		return buscar(id).getCorreo();
	}

	@PatchMapping("/api/prueba/usuarios/{id}")
	String cambiarNombre(@PathVariable Long id, @RequestBody CambioNombre cambio) {
		Usuario usuario = buscar(id);
		usuario.setNombre(cambio.nombre());
		return usuarios.save(usuario).getNombre();
	}

	private Usuario buscar(Long id) {
		// Un id de otra comuna responde igual que uno inexistente: no se revela que existe.
		return usuarios.findByIdAndComunaId(id, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos lo que buscás"));
	}
}
