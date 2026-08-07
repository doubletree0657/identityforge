@org.springframework.modulith.ApplicationModule(
        displayName = "Application registry",
        allowedDependencies = {"shared", "audit", "directory"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.applications;
