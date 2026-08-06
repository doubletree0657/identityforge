package io.github.doubletree.iam.authorization;

import io.github.doubletree.iam.domain.Client;
import io.github.doubletree.iam.domain.ClientStatus;
import io.github.doubletree.iam.repository.ClientRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RepositoryRegisteredClientRepository implements RegisteredClientRepository {

    private final ClientRepository clientRepository;
    private final RegisteredClientMapper registeredClientMapper;

    public RepositoryRegisteredClientRepository(
            ClientRepository clientRepository,
            RegisteredClientMapper registeredClientMapper) {
        this.clientRepository = clientRepository;
        this.registeredClientMapper = registeredClientMapper;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Registered clients must be managed through ClientApplicationService");
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        UUID clientId;
        try {
            clientId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        return clientRepository.findById(clientId)
                .filter(this::isActive)
                .map(registeredClientMapper::toRegisteredClient)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        List<Client> activeClients = clientRepository.findAllByClientId(clientId).stream()
                .filter(this::isActive)
                .toList();
        if (activeClients.isEmpty()) {
            return null;
        }
        if (activeClients.size() > 1) {
            throw new IllegalStateException("Multiple active clients registered for client_id: " + clientId);
        }
        return registeredClientMapper.toRegisteredClient(activeClients.getFirst());
    }

    private boolean isActive(Client client) {
        return client.getStatus() == ClientStatus.ACTIVE;
    }
}
