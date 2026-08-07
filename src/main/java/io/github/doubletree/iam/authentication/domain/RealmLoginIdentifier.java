package io.github.doubletree.iam.authentication.domain;

import io.github.doubletree.iam.directory.domain.UsernameNormalizer;

public record RealmLoginIdentifier(String realm, String normalizedUsername) {

    public static RealmLoginIdentifier parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Login identifier must be provided");
        }
        int separator = value.indexOf('/');
        if (separator < 1 || separator == value.length() - 1 || value.indexOf('/', separator + 1) >= 0) {
            throw new IllegalArgumentException("Login identifier must use realm/username");
        }
        String realm = value.substring(0, separator).strip().toLowerCase(java.util.Locale.ROOT);
        if (!realm.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new IllegalArgumentException("Login realm is invalid");
        }
        return new RealmLoginIdentifier(realm, UsernameNormalizer.normalize(value.substring(separator + 1)));
    }
}
