package com.utolima.vehiculosdocumentosapi.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.utolima.vehiculosdocumentosapi.model.VehiculoPersona;
import com.utolima.vehiculosdocumentosapi.model.enums.EstadoConductor;
import com.utolima.vehiculosdocumentosapi.repository.VehiculoPersonaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class LicenciasVencidasScheduler {

    private final VehiculoPersonaRepository vehiculoPersonaRepository;

    // Cada 2 minutos, como pide el PDF. initialDelay distinto (15 s) al de la tarea de documentos (10 s)
    // para que las dos no arranquen exactamente al mismo tiempo y compitan por la base de datos.
    @Scheduled(fixedRate = 120_000, initialDelay = 15_000)
    @Transactional // el lote se guarda completo o no se guarda; además mantiene abierta la sesión de Hibernate
    public void restringirConductoresConLicenciaVencida() {
        LocalDate hoy = LocalDate.now();

        // licencia vencida = su fecha de vigencia es ANTERIOR a hoy (si vence hoy, todavía sirve durante hoy)
        List<VehiculoPersona> afectados = vehiculoPersonaRepository
                .findConLicenciaVencidaNoRestringidos(hoy, EstadoConductor.RESTRINGIDO);

        if (afectados.isEmpty()) {
            return; // nada que cambiar: no llenamos el log cada 2 minutos
        }

        for (VehiculoPersona vp : afectados) {
            vp.setEstadoConductor(EstadoConductor.RESTRINGIDO); // RO: Restringido para Operar
            // vp.getId() es la llave compuesta (ya está cargada): evitamos tocar la relación perezosa "persona"
            log.info("Licencia vencida: el conductor {} queda RO en el vehículo {}",
                    vp.getId().getIdPersona(), vp.getId().getIdVehiculo());
        }
        vehiculoPersonaRepository.saveAll(afectados);
    }
}