package com.thinklab.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

/**
 * Infrastructure Component: OpenAPI 3.0 Documentation Metadata.
 *
 * <p><b>Architectural Role:</b>
 * Centralizes the global API contract definition. During the Ahead-of-Time (AOT)
 * compilation phase, the 'micronaut-openapi' AST processor parses these annotations
 * to statically generate the official swagger.yml specification.
 *
 * @author Thinklab Core Infrastructure Team
 * @version 1.0.0
 * @since 1.0
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Thinklab IT DR Registry Service Domain",
                version = "v1.0.0",
                description = "BIAN-aligned Service Domain (Control Record: RecoveryPlan) for the IT DR registry: the promise made about recovering an asset from a disaster (strategy, recovery time and point objectives, how often to prove it), the exercises the people and tools report, and how well each promise holds when it is read.",
                contact = @Contact(
                        name = "Thinklab SRE & Security Operations",
                        email = "sre-core@thinklab.com",
                        url = "https://engineering.thinklab.com"
                ),
                license = @License(
                        name = "Proprietary & Confidential - Thinklab Internal Only",
                        url = "https://thinklab.com/security/compliance"
                )
        )
)
public class OpenApiConfig {
    // Empty class serving strictly as an AST metadata anchor for Swagger generation.
}
