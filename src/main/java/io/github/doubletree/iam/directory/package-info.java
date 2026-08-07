@org.springframework.modulith.ApplicationModule(
        displayName = "Directory and access control",
        allowedDependencies = {"shared", "audit"},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.doubletree.iam.directory;
