package com.utolima.vehiculosdocumentosapi.service;

import java.util.List;

import com.utolima.vehiculosdocumentosapi.dto.RutaConConductorDTO;
import com.utolima.vehiculosdocumentosapi.dto.RutaRestringidaDTO;
import com.utolima.vehiculosdocumentosapi.dto.TrayectoRequestDTO;
import com.utolima.vehiculosdocumentosapi.dto.TrayectoResponseDTO;

public interface TrayectoService {

    /**
     * Valida y registra la lista de paradas que componen una ruta (entre 2 y 7 paradas).
     * Requiere que el vehículo tenga documentos en estado Habilitado y que el conductor esté en estado PO.
     */
    void crearRuta(List<TrayectoRequestDTO> paradas);

    /** Consulta 1: paradas de una ruta ordenadas por ordenParada. Lanza 404 si el código no existe. */
    List<TrayectoResponseDTO> consultarRutaPorCodigo(String codigoRuta);

    /** Consulta 2: códigos de ruta (sin repetir) de un conductor, buscado por su número de identificación. */
    List<String> consultarRutasPorConductor(String identificacion);

    /** Consulta 3: rutas de un vehículo (por placa), agrupadas por código con su conductor. Lanza 404 si la placa no existe. */
    List<RutaConConductorDTO> consultarRutasPorVehiculo(String placa);

    /** Consulta 4 (pública): rutas cuyo vehículo NO está habilitado o cuyo conductor está Restringido (RO). */
    List<RutaRestringidaDTO> consultarRutasRestringidas();
}