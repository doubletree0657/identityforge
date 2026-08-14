package io.github.doubletree.iam.oauth.infrastructure;

/** Checks whether the grant backing a self-contained access token is still active. */
public interface AccessTokenAuthorizationState {

    boolean isActive(String tokenValue);
}
