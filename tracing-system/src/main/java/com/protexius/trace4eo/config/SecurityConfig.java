package com.protexius.trace4eo.config;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    private static final String ROLE_VIEWER = "viewer";
    private static final String ROLE_SIGNER = "signer";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_EMAIL = "email";

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${signer.allowed-domains:}")
    private List<String> signerAllowedDomains;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/provenance/check-access").hasRole(ROLE_SIGNER)
                .requestMatchers(HttpMethod.POST, "/api/provenance").hasRole(ROLE_SIGNER)
                .requestMatchers(HttpMethod.POST, "/api/provenance/validate-predecessors").hasRole(ROLE_SIGNER)
                .requestMatchers("/api/**").hasAnyRole(ROLE_VIEWER, ROLE_SIGNER)
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
            if (realmAccess != null) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get(CLAIM_ROLES);
                if (roles != null) {
                    roles.stream()
                        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        .forEach(authorities::add);
                }
            }

            String email = jwt.getClaimAsString(CLAIM_EMAIL);
            if (email != null && signerAllowedDomains != null) {
                boolean domainMatch = signerAllowedDomains.stream()
                    .filter(domain -> !domain.isBlank())
                    .anyMatch(email::endsWith);
                if (domainMatch && authorities.stream().noneMatch(a -> a.getAuthority().equals(ROLE_PREFIX + ROLE_SIGNER))) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + ROLE_SIGNER));
                }
            }

            return Collections.unmodifiableList(authorities);
        });
        return converter;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
