package com.utolima.vehiculosdocumentosapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // sin esta anotación, los métodos @Scheduled se ignoran EN SILENCIO: no da error, simplemente nunca corren
public class SchedulingConfig {
}