package de.marsg.wishlist.wishlist.request.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Allow public access to /health endpoint
                        .requestMatchers(
                            "/health/**",
                            "/docs/**"
                        ).permitAll()

                        // require auth for all other
                        .anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractJwtRoles);
        return converter;
    }

    // Helper class to convert Keycloak roles to Spring security versions
    private Collection<GrantedAuthority> extractJwtRoles(Jwt jwt) {
        try {
            Object realmAccessObj = jwt.getClaim("realm_access");
            if (!(realmAccessObj instanceof Map<?, ?> realmAccess))
                return List.of();

            Object rolesObj = realmAccess.get("roles");
            if (!(rolesObj instanceof List<?> rolesList))
                return List.of();

            return rolesList.stream()
                    .filter(r -> r instanceof String)
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + (String) r))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
