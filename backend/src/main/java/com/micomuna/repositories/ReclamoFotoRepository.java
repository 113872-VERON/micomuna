package com.micomuna.repositories;

import org.springframework.data.repository.Repository;

import com.micomuna.entities.ReclamoFoto;

// Las fotos se crean con el reclamo y no se cambian. Por ahora solo se guardan; leerlas llega con MC-17 y MC-18.
public interface ReclamoFotoRepository extends Repository<ReclamoFoto, Long> {

	ReclamoFoto save(ReclamoFoto foto);
}
