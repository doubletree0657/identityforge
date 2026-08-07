@org.springframework.modulith.ApplicationModule(
        displayName = "OAuth2 and OpenID Connect",
        allowedDependencies = {"shared", "audit", "directory", "authentication", "applications"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.oauth;
