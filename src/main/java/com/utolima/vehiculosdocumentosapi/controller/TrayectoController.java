package com.utolima.vehiculosdocumentosapi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.utolima.vehiculosdocumentosapi.dto.RutaConConductorDTO;

import com.utolima.vehiculosdocumentosapi.dto.TrayectoRequestDTO;
import com.utolima.vehiculosdocumentosapi.dto.TrayectoResponseDTO;
import com.utolima.vehiculosdocumentosapi.service.TrayectoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trayectos") // prefijo común de todos los servicios de trayectos
@RequiredArgsConstructor          // genera el constructor que inyecta trayectoService
public class TrayectoController {

    private final TrayectoService trayectoService;

    // Ya existía: crea una ruta completa (lista de paradas)
    @PostMapping("/rutas")
    public ResponseEntity<Void> crearRuta(@RequestBody List<TrayectoRequestDTO> paradas) {
        trayectoService.crearRuta(paradas);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // CONSULTA 1 (protegida): paradas de una ruta, ordenadas por ordenParada.
    // @PathVariable toma {codigoRuta} de la URL y lo entrega como parámetro del método.
    @GetMapping("/rutas/{codigoRuta}")
    public ResponseEntity<List<TrayectoResponseDTO>> consultarRuta(@PathVariable String codigoRuta) {
        return ResponseEntity.ok(trayectoService.consultarRutaPorCodigo(codigoRuta));
    }

    // CONSULTA 2 (protegida): rutas de un conductor según su número de identificación.
    // Devuelve solo los códigos de ruta (List<String>), sin repetir.
    @GetMapping("/conductores/{identificacion}/rutas")
    public ResponseEntity<List<String>> consultarRutasPorConductor(@PathVariable String identificacion) {
        return ResponseEntity.ok(trayectoService.consultarRutasPorConductor(identificacion));
    }

    // CONSULTA 3 (protegida): rutas de un vehículo según su placa, agrupadas por código de ruta.
    @GetMapping("/vehiculos/{placa}/rutas")
    public ResponseEntity<List<RutaConConductorDTO>> consultarRutasPorVehiculo(@PathVariable String placa) {
        return ResponseEntity.ok(trayectoService.consultarRutasPorVehiculo(placa));
    }
}