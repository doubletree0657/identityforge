package io.github.doubletree.iam.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI identityForgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IdentityForge API")
                        .version("0.1.0")
                        .description("""
                                Public portfolio IAM platform API.
                                /api/health is public. Management and SCIM APIs require OAuth2 JWT scopes:
                                iam.write for write operations and iam.read for read operations.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
