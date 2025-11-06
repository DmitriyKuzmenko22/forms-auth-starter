package com.example.auth;

import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.*;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.*;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login.html", "/register.html", "/dashboard.html", "/assets/**").permitAll()
                .requestMatchers("/public/api/register", "/public/api/login", "/public/static/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/me").authenticated()
                .requestMatchers("/submissions/**").hasRole("user")
                .requestMatchers("/admin/**").hasRole("admin")
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((req, res, ex) -> res.sendError(HttpStatus.FORBIDDEN.value()))
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extract);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extract(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role != null) {
                        String r = role.toString();
                        authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
                    }
                }
            }
        }

        // resource_access.<client>.roles (опционально берём все клиентские роли)
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            for (Object clientObj : resourceAccess.values()) {
                if (clientObj instanceof Map<?, ?> clientMap) {
                    Object rolesObj = clientMap.get("roles");
                    if (rolesObj instanceof Collection<?> roles) {
                        for (Object role : roles) {
                            if (role != null) {
                                String r = role.toString();
                                authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
                            }
                        }
                    }
                }
            }
        }

        return authorities;
    }
}