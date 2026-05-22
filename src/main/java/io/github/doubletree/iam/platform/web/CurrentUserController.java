package io.github.doubletree.iam.platform.web;

import io.github.doubletree.iam.platform.web.dto.CurrentUserResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    @GetMapping
    public CurrentUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = claimSet(jwt, "roles");
        Set<String> scopes = claimSet(jwt, "scope");
        return new CurrentUserResponse(
                jwt.getSubject(),
                jwt.getSubject(),
                jwt.getClaimAsString("user_id"),
                jwt.getClaimAsString("tenant_id"),
                jwt.getClaimAsString("display_name"),
                roles,
                scopes);
    }

    private Set<String> claimSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof String value) {
            if (value.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(value.split(" "))
                    .filter(scope -> !scope.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return new LinkedHashSet<>(jwt.getClaimAsStringList(claimName) == null
                ? Set.of()
                : jwt.getClaimAsStringList(claimName));
    }
}
