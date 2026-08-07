@org.springframework.modulith.ApplicationModule(
        displayName = "Authentication",
        allowedDependencies = {"shared", "audit", "directory"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.authentication;
