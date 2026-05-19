package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.application.service.ClientApplicationService;
import io.github.doubletree.iam.platform.application.result.ClientSecretResult;
import io.github.doubletree.iam.platform.domain.Client;
import io.github.doubletree.iam.platform.web.dto.ClientResponse;
import io.github.doubletree.iam.platform.web.dto.ClientSecretResponse;
import io.github.doubletree.iam.platform.web.dto.CreateClientRequest;
import io.github.doubletree.iam.platform.web.dto.UpdateClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@Tag(name = "Clients", description = "OAuth2 client management APIs")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class ClientController {

    private final ClientApplicationService clientApplicationService;

    public ClientController(ClientApplicationService clientApplicationService) {
        this.clientApplicationService = clientApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create client", description = "Requires iam.write scope.")
    public ClientSecretResponse createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientSecretResult result = clientApplicationService.createClientWithSecret(
                request.tenantId(),
                request.clientId(),
                request.name(),
                request.clientType(),
                request.requirePkce(),
                request.requireConsent(),
                request.redirectUris(),
                request.grantTypes(),
                request.scopes(),
                request.authenticationMethods());
        return ClientSecretResponse.from(result);
    }

    @GetMapping
    @Operation(summary = "List clients", description = "Requires iam.read scope.")
    public List<ClientResponse> listClients(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return clientApplicationService.listClients(tenantId, PageRequest.of(page, size)).stream()
                .map(ClientResponse::from)
                .toList();
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get client", description = "Requires iam.read scope.")
    public ClientResponse getClient(@PathVariable UUID clientId) {
        return ClientResponse.from(clientApplicationService.findClient(clientId));
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Update client", description = "Requires iam.write scope.")
    public ClientResponse updateClient(
            @PathVariable UUID clientId,
            @Valid @RequestBody UpdateClientRequest request) {
        Client client = clientApplicationService.updateClient(
                clientId,
                request.clientName(),
                request.status(),
                request.requirePkce(),
                request.requireConsent(),
                request.redirectUris(),
                request.grantTypes(),
                request.scopes(),
                request.authenticationMethods());
        return ClientResponse.from(client);
    }

    @PostMapping("/{clientId}/secret/rotation")
    @Operation(summary = "Rotate client secret", description = "Requires iam.write scope.")
    public ClientSecretResponse rotateClientSecret(@PathVariable UUID clientId) {
        return ClientSecretResponse.from(clientApplicationService.rotateClientSecret(clientId));
    }
}
