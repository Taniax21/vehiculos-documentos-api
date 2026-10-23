package com.utolima.vehiculosdocumentosapi.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.utolima.vehiculosdocumentosapi.security.ApiKeyFilter;
import com.utolima.vehiculosdocumentosapi.security.CustomAuthenticationEntryPoint;
import com.utolima.vehiculosdocumentosapi.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // activa la seguridad personalizada -- en cuanto existe este @Bean, Spring Boot DEJA de generar la contrasena aleatoria de antes
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyFilter apiKeyFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Activa CORS usando el bean corsConfigurationSource de abajo. Su filtro corre ANTES que el de JWT,
            // así las peticiones "de reconocimiento" (preflight) del navegador, que no llevan token, no se rechazan.
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()) // API REST sin sesiones/cookies ni formularios HTML -- CSRF no aplica aqui
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // nunca guarda sesion: cada peticion se autentica sola, con su propio JWT
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/publico/**", "/api/auth/**").permitAll() // 1ro: valida el JWT
                .anyRequest().authenticated() // 2do: valida el APIKey
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(apiKeyFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    // Qué páginas web tienen permiso de llamar a esta API desde el navegador
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Solo desarrollo local (cualquier puerto). En un despliegue real se pondría el dominio exacto del front.
        config.setAllowedOriginPatterns(List.of(
                "http://localhost", "http://localhost:*",
                "http://127.0.0.1", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // X-API-Key es un header propio: si no está en esta lista, el navegador bloquea la llamada
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
        config.setMaxAge(3600L); // el navegador recuerda el permiso 1 hora y no repite el preflight

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}