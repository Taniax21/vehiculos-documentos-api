package com.utolima.vehiculosdocumentosapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String apikey; // nueva: se entrega junto con el token, el usuario nunca lo escribe
}