package com.utolima.vehiculosdocumentosapi.dto;

// Una fila por ruta: el código, quién la conduce y cuántas paradas tiene.
// Es el "agrupado" que pide el enunciado (no repetimos una fila por cada parada).
public record RutaConConductorDTO(
        String codigoRuta,
        ConductorResumenDTO conductor, // reutilizamos el DTO de conductor de la consulta 1
        int totalParadas) {
}
