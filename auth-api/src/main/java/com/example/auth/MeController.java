package com.example.auth;

import org.springframework.security.core.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class MeController {
    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("sub", jwt.getSubject(), "email", jwt.getClaimAsString("email"), "roles", jwt.getClaim("realm_access"));
    }

    @GetMapping("/admin/health")
    public Map<String, String> adminOnly() {
        return Map.of("scope", "admin");
    }
}