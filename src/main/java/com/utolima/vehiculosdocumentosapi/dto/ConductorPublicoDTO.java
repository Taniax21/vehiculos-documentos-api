package com.utolima.vehiculosdocumentosapi.dto;

// Datos del conductor aptos para un servicio PÚBLICO: sin identificación ni correo.
public record ConductorPublicoDTO(
        String nombres,
        String apellidos) {
}