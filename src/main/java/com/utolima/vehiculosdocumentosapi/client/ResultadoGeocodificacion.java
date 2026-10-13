package com.utolima.vehiculosdocumentosapi.client;

import java.math.BigDecimal;

// Lo que devuelve una consulta a Google, resumido en 3 casos que el scheduler sabe manejar.
// "record" (Java 16+) = clase inmutable: Java genera constructor, getters y toString.
public record ResultadoGeocodificacion(Estado estado, BigDecimal latitud, BigDecimal longitud, String mensaje) {

    public enum Estado {
        OK,              // se encontraron coordenadas
        SIN_RESULTADOS,  // Google funcionó, pero no encontró esa dirección (el problema es la dirección)
        ERROR            // algo falló: clave, facturación, cuota, red... (el problema NO es la dirección)
    }

    public static ResultadoGeocodificacion ok(BigDecimal lat, BigDecimal lng) {
        return new ResultadoGeocodificacion(Estado.OK, lat, lng, null);
    }

    public static ResultadoGeocodificacion sinResultados() {
        return new ResultadoGeocodificacion(Estado.SIN_RESULTADOS, null, null, null);
    }

    public static ResultadoGeocodificacion error(String mensaje) {
        return new ResultadoGeocodificacion(Estado.ERROR, null, null, mensaje);
    }
}