package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.dtos.FotoReclamoDto;
import com.micomuna.entities.ReclamoFoto;

// Las fotos se crean junto con el reclamo (o al resolverlo) y no se cambian.
public interface ReclamoFotoRepository extends Repository<ReclamoFoto, Long> {

	ReclamoFoto save(ReclamoFoto foto);

	// Solo los datos de cada foto (id, origen, orden): la consulta no pide la imagen, así que los bytes no se leen.
	@Query("select new com.micomuna.dtos.FotoReclamoDto(f.id, f.origen, f.orden) from ReclamoFoto f "
			+ "where f.reclamo.id = :idReclamo order by f.orden")
	List<FotoReclamoDto> datosDe(@Param("idReclamo") Long idReclamo);

	// Las fotos del TRABAJO solo se entregan si el reclamo está RESUELTO.
	// La imagen, solo si la foto pertenece a ese reclamo Y el reclamo es de ese vecino en esa comuna: una sola consulta
	// con las cuatro condiciones, así que una foto ajena, de otra comuna o inexistente dan el mismo resultado (vacío).
	@Query("""
			select f from ReclamoFoto f join f.reclamo r
			where f.id = :idFoto and r.numero = :numero and r.comuna.id = :idComuna and r.vecino.id = :idVecino
				and (f.origen = com.micomuna.enums.OrigenFoto.VECINO or r.estado = com.micomuna.enums.EstadoReclamo.RESUELTO)
			""")
	Optional<ReclamoFoto> buscarDelVecino(@Param("idFoto") Long idFoto, @Param("numero") Integer numero,
			@Param("idComuna") Long idComuna, @Param("idVecino") Long idVecino);
}
