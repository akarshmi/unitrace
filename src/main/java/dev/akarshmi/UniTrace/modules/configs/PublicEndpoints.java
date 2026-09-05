package dev.akarshmi.UniTrace.modules.configs;

import lombok.RequiredArgsConstructor;

import java.util.Set;

public class PublicEndpoints {

    private PublicEndpoints() {}


    public static final Set<String> URLS = Set.of(
            "/api/uni/v1/auth/registration/initiation",
            "/api/uni/v1/auth/registration/verification",
            "/api/uni/v1/auth/login",
            "/api/uni/v1/auth/refresh"
    );
    }
