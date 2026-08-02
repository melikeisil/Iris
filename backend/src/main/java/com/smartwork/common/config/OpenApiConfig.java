package com.smartwork.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI smartWorkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartWork AI API")
                        .version("v1")
                        .description("""
                                AI-powered enterprise work orchestration platform.

                                The AI service classifies, decomposes and explains work. Every decision
                                with organizational consequence - assignment, approval routing, workflow
                                transition - is made by deterministic backend logic and is auditable.
                                """)
                        .contact(new Contact().name("SmartWork AI"))
                        .license(new License().name("Hackathon MVP - not licensed for production use")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
