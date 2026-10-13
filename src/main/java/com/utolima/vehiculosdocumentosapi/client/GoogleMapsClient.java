package com.utolima.vehiculosdocumentosapi.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Toda la comunicación con Google vive aquí. Si cambia la API (Geocoding, Find Place...), solo se toca esta clase.
@Component
public class GoogleMapsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper; // el mismo bean que definimos en JacksonConfig
    private final String urlTemplate;
    private final String apiKey;

    // Constructor manual (y no @RequiredArgsConstructor) porque necesitamos @Value en los parámetros
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
            // {direccion} y {clave} se reemplazan por estos valores y RestClient los codifica correctamente
            String json = restClient.get()
                    .uri(urlTemplate, Map.of("direccion", ubicacion, "clave", apiKey))
                    .retrieve()
                    .body(String.class); // pedimos el texto crudo y lo leemos nosotros: no dependemos de convertidores
            return interpretar(json);
        } catch (Exception e) {
            // IMPORTANTE: nunca e.getMessage(). Los mensajes de Spring para errores de red o HTTP
            // incluyen la URL COMPLETA, y esa URL lleva la clave: terminaría escrita en el log.
            return ResultadoGeocodificacion.error("Falló la llamada a Google Maps (" + e.getClass().getSimpleName() + ")");
        }
    }

    private ResultadoGeocodificacion interpretar(String json) throws JsonProcessingException {
        JsonNode raiz = objectMapper.readTree(json);
        String status = raiz.path("status").asText(""); // Google responde 200 incluso cuando rechaza: hay que leer "status"

        if ("ZERO_RESULTS".equals(status)) {
            return ResultadoGeocodificacion.sinResultados();
        }
        if (!"OK".equals(status)) { // REQUEST_DENIED, OVER_QUERY_LIMIT, INVALID_REQUEST, UNKNOWN_ERROR...
            String detalle = raiz.path("error_message").asText("");
            return ResultadoGeocodificacion.error("Google Maps respondió " + status
                    + (detalle.isBlank() ? "" : ": " + detalle));
        }

        // Geocoding trae la lista en "results"; Find Place la trae en "candidates". Aceptamos las dos.
        JsonNode primero = primerElemento(raiz.path("results"));
        if (primero == null) {
            primero = primerElemento(raiz.path("candidates"));
        }
        if (primero == null) {
            return ResultadoGeocodificacion.sinResultados();
        }

        JsonNode punto = primero.path("geometry").path("location");
        if (!punto.has("lat") || !punto.has("lng")) {
            return ResultadoGeocodificacion.sinResultados();
        }

        // La tabla es DECIMAL(10,8) y DECIMAL(11,8): se ajusta a 8 decimales
        BigDecimal lat = punto.get("lat").decimalValue().setScale(8, RoundingMode.HALF_UP);
        BigDecimal lng = punto.get("lng").decimalValue().setScale(8, RoundingMode.HALF_UP);
        return ResultadoGeocodificacion.ok(lat, lng);
    }

    private JsonNode primerElemento(JsonNode lista) {
        return (lista.isArray() && lista.size() > 0) ? lista.get(0) : null;
    }
}