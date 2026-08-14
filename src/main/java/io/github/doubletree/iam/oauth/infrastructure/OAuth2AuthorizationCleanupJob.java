package io.github.doubletree.iam.oauth.infrastructure;

import io.github.doubletree.iam.oauth.application.OAuth2AuthorizationLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthorizationCleanupJob {

    private final OAuth2AuthorizationLifecycleService lifecycleService;

    public OAuth2AuthorizationCleanupJob(OAuth2AuthorizationLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${iam.oauth.authorization-cleanup-interval:PT1H}")
    public void purgeExpiredAuthorizations() {
        lifecycleService.purgeExpiredAuthorizations();
    }
}
