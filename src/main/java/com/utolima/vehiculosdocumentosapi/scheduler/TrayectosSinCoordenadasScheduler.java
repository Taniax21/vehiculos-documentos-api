package com.utolima.vehiculosdocumentosapi.scheduler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.utolima.vehiculosdocumentosapi.client.GoogleMapsClient;
import com.utolima.vehiculosdocumentosapi.client.ResultadoGeocodificacion;
import com.utolima.vehiculosdocumentosapi.model.Trayecto;
import com.utolima.vehiculosdocumentosapi.repository.TrayectoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrayectosSinCoordenadasScheduler {

    private static final int MAX_POR_EJECUCION = 20; // tope de llamadas a Google por vuelta: cuida cuota y costos

    private final TrayectoRepository trayectoRepository;
    private final GoogleMapsClient googleMapsClient;

    // Trayectos que Google no pudo ubicar: se saltan para no reintentarlos cada 90 s. Se vacía al reiniciar la app.
    private final Set<Long> sinResultado = ConcurrentHashMap.newKeySet();

    // Último error escrito en el log, para no repetir el mismo mensaje cada 90 segundos
    private String ultimoErrorRegistrado;

    // fixedDelay: espera 90 s DESPUÉS de terminar la vuelta anterior. Aquí conviene (a diferencia de las otras dos tareas)
    // porque hay llamadas de red que pueden tardar: así dos vueltas nunca se pisan.
    @Scheduled(fixedDelay = 90_000, initialDelay = 20_000)
    // Sin @Transactional a propósito: no queremos una conexión a la base abierta mientras esperamos a Google.
    // Cada UPDATE abre y cierra su propia transacción (ver TrayectoRepository.actualizarCoordenadas).
    public void completarCoordenadas() {

        if (!googleMapsClient.estaConfigurado()) {
            registrarErrorUnaVez("No hay clave de Google Maps (variable de entorno GOOGLE_MAPS_API_KEY): se omite la geocodificación.");
            return;
        }

        List<Trayecto> pendientes = trayectoRepository
                .findTop50ByLatitudIsNullOrLongitudIsNullOrderByIdTrayectoAsc().stream()
                .filter(t -> !sinResultado.contains(t.getIdTrayecto()))
                .limit(MAX_POR_EJECUCION)
                .toList();

        if (pendientes.isEmpty()) {
            return; // nada que hacer: no llenamos el log
        }

        int completados = 0;
        for (Trayecto t : pendientes) {
            ResultadoGeocodificacion r = googleMapsClient.geocodificar(t.getUbicacion());

            if (r.estado() == ResultadoGeocodificacion.Estado.OK) {
                trayectoRepository.actualizarCoordenadas(t.getIdTrayecto(), r.latitud(), r.longitud());
                completados++;
                ultimoErrorRegistrado = null; // si el error vuelve a aparecer más adelante, se registrará de nuevo

            } else if (r.estado() == ResultadoGeocodificacion.Estado.SIN_RESULTADOS) {
                sinResultado.add(t.getIdTrayecto());
                log.warn("Geocodificación: Google no encontró la ubicación del trayecto {} ('{}')",
                        t.getIdTrayecto(), t.getUbicacion());

            } else { // ERROR: clave, facturación, cuota o red -> insistir con los demás trayectos solo gastaría llamadas
                registrarErrorUnaVez(r.mensaje());
                break; // se reintenta en la próxima vuelta (90 s)
            }
        }

        if (completados > 0) {
            log.info("Geocodificación: {} trayecto(s) completaron su latitud y longitud", completados);
        }
    }

    private void registrarErrorUnaVez(String mensaje) {
        if (!mensaje.equals(ultimoErrorRegistrado)) {
            log.warn("Geocodificación: {}", mensaje);
            ultimoErrorRegistrado = mensaje;
        }
    }
}