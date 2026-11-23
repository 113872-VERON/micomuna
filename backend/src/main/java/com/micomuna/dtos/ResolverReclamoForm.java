package com.micomuna.dtos;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// Formulario multipart de "marcar como resuelto" (MC-20): la nota y la foto del trabajo, las dos opcionales.
// Que la foto sea válida lo revisa AgenteReclamosService con el mismo control que el alta del vecino.
public record ResolverReclamoForm(
		@Schema(description = "Opcional. Lo que se hizo; lo ve el vecino", maxLength = 1000)
		@Size(max = 1000, message = "La nota puede tener hasta 1000 caracteres. Acortala un poco") String nota,

		@Schema(description = "Opcional. Una foto del trabajo terminado (JPG o PNG)", type = "string", format = "binary") MultipartFile foto) {
}
