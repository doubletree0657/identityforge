package io.github.doubletree.iam.oauth.infrastructure;

import com.nimbusds.jose.jwk.RSAKey;

public interface SigningKeyProvider {

    RSAKey currentKey();
}
