package com.utolima.vehiculosdocumentosapi.dto;

import java.math.BigDecimal;

// Una parada de la ruta, sin el login de quien la registró (dato interno).
public record ParadaPublicaDTO(
        Integer ordenParada,
        String ubicacion,
        BigDecimal latitud,
        BigDecimal longitud) {
}