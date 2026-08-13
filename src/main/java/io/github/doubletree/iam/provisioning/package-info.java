@org.springframework.modulith.ApplicationModule(
        displayName = "Provisioning",
        allowedDependencies = {"shared", "directory", "audit"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.provisioning;
