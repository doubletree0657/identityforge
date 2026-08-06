package io.github.doubletree.iam.security.authentication;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;

@Configuration
public class LocalUserAuthenticationConfiguration {

    @Bean
    AuthenticationManager authenticationManager(LocalUserAuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }
}
