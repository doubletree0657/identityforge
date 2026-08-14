package io.github.doubletree.iam.oauth.infrastructure;

import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSigningKeyProvider implements SigningKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSigningKeyProvider.class);
    private final RSAKey currentKey;

    public FileSigningKeyProvider(String configuredPath) {
        this(configuredPath, true);
    }

    @Autowired
    public FileSigningKeyProvider(
            @Value("${iam.security.signing-key-file:}") String configuredPath,
            @Value("${iam.security.allow-signing-key-generation:false}") boolean allowKeyGeneration) {
        if (configuredPath == null || configuredPath.isBlank()) {
            if (!allowKeyGeneration) {
                throw new IllegalStateException("iam.security.signing-key-file must be configured");
            }
            this.currentKey = ephemeralKey();
            return;
        }
        this.currentKey = load(Path.of(configuredPath), allowKeyGeneration);
    }

    @Override
    public RSAKey currentKey() {
        return currentKey;
    }

    private RSAKey load(Path path, boolean allowKeyGeneration) {
        try {
            if (Files.exists(path)) {
                return read(path);
            }
            if (!allowKeyGeneration) {
                throw new IllegalStateException("OAuth2 signing key file does not exist: " + path);
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            RSAKey generated = generateKey("identityforge-" + UUID.randomUUID());
            Properties properties = new Properties();
            properties.setProperty("keyId", generated.getKeyID());
            properties.setProperty("publicKey", Base64.getEncoder().encodeToString(
                    generated.toRSAPublicKey().getEncoded()));
            properties.setProperty("privateKey", Base64.getEncoder().encodeToString(
                    generated.toRSAPrivateKey().getEncoded()));
            try (OutputStream output = Files.newOutputStream(
                    path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                properties.store(output, "IdentityForge local signing key. Do not commit.");
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                return read(path);
            }
            restrictPermissions(path);
            return generated;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Unable to load or create OAuth2 signing key at " + path, exception);
        }
    }

    private RSAKey read(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(required(properties, "publicKey"))));
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(required(properties, "privateKey"))));
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(required(properties, "keyId"))
                .build();
    }

    private String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Signing key file is missing " + name);
        }
        return value;
    }

    private RSAKey ephemeralKey() {
        LOGGER.warn("No iam.security.signing-key-file is configured; tokens will not survive a restart");
        return generateKey("ephemeral-" + UUID.randomUUID());
    }

    private RSAKey generateKey(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(keyId)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate RSA signing key", exception);
        }
    }

    private void restrictPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems must protect this path through platform ACLs.
        }
    }
}
