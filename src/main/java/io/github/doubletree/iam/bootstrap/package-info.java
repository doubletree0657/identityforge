@org.springframework.modulith.ApplicationModule(
        displayName = "Bootstrap",
        allowedDependencies = {"shared", "audit", "directory", "authentication", "applications", "oauth"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.bootstrap;
