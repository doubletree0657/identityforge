package io.github.doubletree.iam.directory.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class UsernameNormalizer {

    private UsernameNormalizer() {
    }

    public static String normalize(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username must be provided");
        }
        String normalized = Normalizer.normalize(username.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        return normalized;
    }
}
