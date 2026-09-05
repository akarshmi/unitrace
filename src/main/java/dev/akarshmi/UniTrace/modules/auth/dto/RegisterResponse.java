package dev.akarshmi.UniTrace.modules.auth.dto;

public record RegisterResponse(
        String uniEmail,
        String token,
        Long otpTtlMinutes
){

}
