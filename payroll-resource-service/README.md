# Payroll Resource Service

This directory is an independent Spring Boot OAuth2 resource server for the
IdentityForge external-service demo. It trusts IdentityForge's public signing
keys, requires the configured issuer and `payroll-api` audience, and maps the
token's application scopes to individual Payroll endpoints.

From the repository root:

```bash
./mvnw -f payroll-resource-service/pom.xml test
./mvnw -f payroll-resource-service/pom.xml spring-boot:run
```

IdentityForge should be running at `http://localhost:8080`; this service starts
at `http://localhost:8090`. For token commands, expected allowed and denied
responses, configuration, and Docker instructions, see the
[complete external Payroll demo guide](../docs/demos/external-payroll-resource-service.md).
