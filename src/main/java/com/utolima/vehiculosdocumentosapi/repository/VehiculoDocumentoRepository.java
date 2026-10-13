package com.utolima.vehiculosdocumentosapi.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.utolima.vehiculosdocumentosapi.model.VehiculoDocumento;
import com.utolima.vehiculosdocumentosapi.model.VehiculoDocumentoId;
import com.utolima.vehiculosdocumentosapi.model.enums.EstadoDocumento;

// La llave primaria de VehiculoDocumento es compuesta (VehiculoDocumentoId), por eso va como segundo parametro
public interface VehiculoDocumentoRepository extends JpaRepository<VehiculoDocumento, VehiculoDocumentoId> {

    // Todos los registros de la tabla puente para un vehiculo dado
    // "Vehiculo_Id" con guion bajo: significa "el campo 'id' dentro de la relacion 'vehiculo'"
    List<VehiculoDocumento> findByVehiculo_Id(Long idVehiculo);

    // Todos los registros de la tabla puente para un documento dado
    List<VehiculoDocumento> findByDocumento_Id(Long idDocumento);

    @Query("SELECT vd FROM VehiculoDocumento vd WHERE vd.vehiculo.id = :idVehiculo")
    List<VehiculoDocumento> findByVehiculoId(@Param("idVehiculo") Long idVehiculo);

 // Documentos cuya fecha de vencimiento ya pasó y que todavía NO están marcados como VENCIDO.
 // Spring Data lo traduce solo: fechaVencimiento < ?1 AND estadoDocumento <> ?2
 List<VehiculoDocumento> findByFechaVencimientoBeforeAndEstadoDocumentoNot(LocalDate fecha, EstadoDocumento estado);//consulta que le dice a la tarea que documento hay que marcar como vencido
}