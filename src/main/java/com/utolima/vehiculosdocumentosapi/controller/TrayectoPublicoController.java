package com.utolima.vehiculosdocumentosapi.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utolima.vehiculosdocumentosapi.dto.RutaRestringidaDTO;
import com.utolima.vehiculosdocumentosapi.service.TrayectoService;

import lombok.RequiredArgsConstructor;

// Controlador APARTE a propósito: su ruta empieza por /api/publico/, que SecurityConfig ya deja libre
// (sin token ni APIKey). Si este método viviera en TrayectoController (/api/trayectos), quedaría protegido.
@RestController
@RequestMapping("/api/publico/trayectos")
@RequiredArgsConstructor
public class TrayectoPublicoController {

    private final TrayectoService trayectoService;

    // CONSULTA 4 (PÚBLICA): rutas cuyo vehículo no está habilitado o cuyo conductor está Restringido (RO).
    @GetMapping("/rutas-restringidas")
    public ResponseEntity<List<RutaRestringidaDTO>> consultarRutasRestringidas() {
        return ResponseEntity.ok(trayectoService.consultarRutasRestringidas());
    }
}