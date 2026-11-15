package com.micomuna.services;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.micomuna.entities.Comuna;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComunaService {

	// Minúsculas, números y guiones simples en el medio (por ejemplo "salsipuedes-cordoba").
	private static final Pattern SLUG_VALIDO = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	private static final int SLUG_MAXIMO = 120;

	private final ComunaRepository comunas;

	public Comuna buscarActivaPorSlug(String slug) {
		if (slug == null || slug.length() > SLUG_MAXIMO || !SLUG_VALIDO.matcher(slug).matches()) {
			throw noEncontrada();
		}
		return comunas.findBySlug(slug)
				.filter(Comuna::isActiva)
				.orElseThrow(ComunaService::noEncontrada);
	}

	// Mismo error para "no existe" y "está inactiva", así no se revela cuáles existen.
	private static RecursoNoEncontradoException noEncontrada() {
		return new RecursoNoEncontradoException("No encontramos esa comuna");
	}
}
