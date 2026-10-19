package com.utolima.vehiculosdocumentosapi.dto;

import java.util.List;

import com.utolima.vehiculosdocumentosapi.model.enums.MotivoRestriccion;

// Una ruta restringida con todas sus paradas, agrupada bajo su código.
public record RutaRestringidaDTO(
        String codigoRuta,
        MotivoRestriccion motivo,       // VEHICULO_NO_HABILITADO, CONDUCTOR_RESTRINGIDO o AMBOS
        ConductorPublicoDTO conductor,  // solo nombres y apellidos
        String placa,                   // placa del vehículo
        List<ParadaPublicaDTO> trayectos) {
}