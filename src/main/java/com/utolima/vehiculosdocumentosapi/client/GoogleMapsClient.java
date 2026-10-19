package com.utolima.vehiculosdocumentosapi.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Toda la comunicación con Google vive aquí. Si cambia la API, solo se toca esta clase y application.properties.
@Component
public class GoogleMapsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper; // el mismo bean que definimos en JacksonConfig
    private final String urlTemplate;
    private final String apiKey;

    public GoogleMapsClient(ObjectMapper objectMapper,
                            @Value("${google.maps.url-template}") String urlTemplate,
                            @Value("${google.maps.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.urlTemplate = urlTemplate;
        this.apiKey = apiKey;

        // Sin timeouts, una llamada colgada bloquearía la tarea programada para siempre
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean estaConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ResultadoGeocodificacion geocodificar(String ubicacion) {
        try {
            // {direccion} y {clave} se reemplazan por estos valores; Spring codifica la dirección en UTF-8
            // (espacio -> %20, coma -> %2C, tildes -> %C3%A9...). No usar URLEncoder aquí: codificaría dos veces.
            String json = restClient.get()
                    .uri(urlTemplate, Map.of("direccion", ubicacion, "clave", apiKey))
                    .retrieve()
                    .body(String.class);
            return interpretar(json);
        } catch (RestClientResponseException e) {
            // Con la v4, Google rechaza con un HTTP 4xx y un JSON {"error": {...}} que explica el motivo.
            // Ese CUERPO se puede leer sin riesgo. Lo que NUNCA se usa es e.getMessage(): trae la URL con la clave.
            return interpretar(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResultadoGeocodificacion.error("Falló la llamada a Google Maps (" + e.getClass().getSimpleName() + ")");
        }
    }

    // Lee los dos formatos de respuesta: Geocoding v4 (el del profesor) y el clásico
    private ResultadoGeocodificacion interpretar(String json) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(json == null ? "" : json);
        } catch (JsonProcessingException e) {
            return ResultadoGeocodificacion.error("La respuesta de Google Maps no es un JSON válido");
        }
        if (raiz == null || raiz.isMissingNode()) {
            return ResultadoGeocodificacion.error("Google Maps respondió sin contenido");
        }

        // 1) Errores del formato nuevo: {"error": {"code": 403, "status": "PERMISSION_DENIED", "message": "..."}}
        if (raiz.has("error")) {
            JsonNode err = raiz.get("error");
            String estado = err.path("status").asText("ERROR");
            // Una dirección sin coincidencias no es un fallo de la clave ni de la cuenta: es SIN_RESULTADOS.
            // Si se tratara como ERROR, esa dirección frenaría la cola en cada vuelta (el scheduler corta ante un ERROR).
            if ("NOT_FOUND".equals(estado)) {
                return ResultadoGeocodificacion.sinResultados();
            }
            return ResultadoGeocodificacion.error("Google Maps respondió " + estado + ": "
                    + err.path("message").asText(""));
        }

        // 2) Formato clásico: trae "status" (OK, ZERO_RESULTS, REQUEST_DENIED...). La v4 NO lo trae.
        if (raiz.has("status")) {
            String status = raiz.get("status").asText("");
            if ("ZERO_RESULTS".equals(status)) {
                return ResultadoGeocodificacion.sinResultados();
            }
            if (!"OK".equals(status)) {
                String detalle = raiz.path("error_message").asText("");
                return ResultadoGeocodificacion.error("Google Maps respondió " + status
                        + (detalle.isBlank() ? "" : ": " + detalle));
            }
        }

        // 3) Primer resultado: "results" (Geocoding) o "candidates" (Find Place clásico)
        JsonNode primero = primerElemento(raiz.path("results"));
        if (primero == null) {
            primero = primerElemento(raiz.path("candidates"));
        }
        if (primero == null) {
            return ResultadoGeocodificacion.sinResultados(); // v4 sin coincidencias: respuesta sin "results"
        }

        // 4) Coordenadas: v4 -> location.latitude/longitude ; clásico -> geometry.location.lat/lng
        JsonNode punto = primero.path("location");
        JsonNode lat = punto.path("latitude");
        JsonNode lng = punto.path("longitude");
        if (!lat.isNumber() || !lng.isNumber()) {
            punto = primero.path("geometry").path("location");
            lat = punto.path("lat");
            lng = punto.path("lng");
        }
        if (!lat.isNumber() || !lng.isNumber()) {
            return ResultadoGeocodificacion.sinResultados();
        }

        // La tabla es DECIMAL(10,8) y DECIMAL(11,8): se ajusta a 8 decimales
        return ResultadoGeocodificacion.ok(
                lat.decimalValue().setScale(8, RoundingMode.HALF_UP),
                lng.decimalValue().setScale(8, RoundingMode.HALF_UP));
    }

    private JsonNode primerElemento(JsonNode lista) {
        return (lista.isArray() && lista.size() > 0) ? lista.get(0) : null;
    }
}