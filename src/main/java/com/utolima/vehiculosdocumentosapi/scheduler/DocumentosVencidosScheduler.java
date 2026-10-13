package com.utolima.vehiculosdocumentosapi.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.utolima.vehiculosdocumentosapi.model.VehiculoDocumento;
import com.utolima.vehiculosdocumentosapi.model.enums.EstadoDocumento;
import com.utolima.vehiculosdocumentosapi.repository.VehiculoDocumentoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component // bean de Spring: sin esto el scheduler no descubriría esta clase
@RequiredArgsConstructor
@Slf4j // Lombok genera el objeto "log" para escribir en la consola
public class DocumentosVencidosScheduler {

    private final VehiculoDocumentoRepository vehiculoDocumentoRepository;

    // fixedRate = 120_000 ms = cada 2 minutos (lo que pide el PDF).
    // initialDelay = 10_000: la primera ejecución espera 10 s después de arrancar, para que la app termine de levantar.
    @Scheduled(fixedRate = 120_000, initialDelay = 10_000)
    @Transactional // todo el lote se guarda junto, o no se guarda nada; además mantiene abierta la sesión de Hibernate
    public void marcarDocumentosVencidos() {
        LocalDate hoy = LocalDate.now();

        // "Vencido" = su fecha de vencimiento es ANTERIOR a hoy. Un documento que vence hoy sigue vigente durante hoy.
        List<VehiculoDocumento> vencidos = vehiculoDocumentoRepository
                .findByFechaVencimientoBeforeAndEstadoDocumentoNot(hoy, EstadoDocumento.VENCIDO);

        if (vencidos.isEmpty()) {
            return; // nada que cambiar: no llenamos el log con un mensaje cada 2 minutos
        }

        for (VehiculoDocumento vd : vencidos) {
            vd.setEstadoDocumento(EstadoDocumento.VENCIDO);
        }
        vehiculoDocumentoRepository.saveAll(vencidos);

        log.info("Tarea programada: {} documento(s) de vehículo pasaron a estado VENCIDO", vencidos.size());
    }
}