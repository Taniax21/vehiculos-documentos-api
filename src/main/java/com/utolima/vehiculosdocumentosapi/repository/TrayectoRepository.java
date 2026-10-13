package com.utolima.vehiculosdocumentosapi.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.utolima.vehiculosdocumentosapi.model.Trayecto;

import jakarta.transaction.Transactional;

@Repository
public interface TrayectoRepository extends JpaRepository<Trayecto, Long> {
    
    // Método de utilidad para las consultas posteriores por código de ruta
    List<Trayecto> findByCodigoRutaOrderByOrdenParadaAsc(String codigoRuta);
    boolean existsByCodigoRuta(String codigoRuta); // evita crear dos rutas con el mismo código

 // Trayectos a los que les falta latitud o longitud, los más antiguos primero, máximo 50 por consulta
    List<Trayecto> findTop50ByLatitudIsNullOrLongitudIsNullOrderByIdTrayectoAsc();

    // UPDATE directo: solo toca las dos columnas de coordenadas de UNA fila
    @Modifying       // le avisa a Spring que esta @Query escribe en la base, no lee
    @Transactional  // un UPDATE necesita transacción; la declaramos aquí porque el scheduler no abre ninguna
    @Query("UPDATE Trayecto t SET t.latitud = :lat, t.longitud = :lng WHERE t.idTrayecto = :id")
    int actualizarCoordenadas(@Param("id") Long id, @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng);
}
