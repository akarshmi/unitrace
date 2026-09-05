package dev.akarshmi.UniTrace.modules.auth.service;

import dev.akarshmi.UniTrace.modules.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Service
public interface AuthService {

    RegisterResponse register(RegisterRequest request);
    RegistrationVerifyResponse verifyRegistration(VerifyRegistrationRequest request);
    TokenResponse login(LoginRequest request, HttpServletResponse response);
    TokenResponse refresh(HttpServletRequest request, HttpServletResponse response);
    void logout(HttpServletRequest request, HttpServletResponse response);
}
