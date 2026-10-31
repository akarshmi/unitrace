package dev.akarshmi.UniTrace.modules.user.dto;

import java.util.UUID;

import dev.akarshmi.UniTrace.modules.user.entity.Role;
import dev.akarshmi.UniTrace.modules.user.entity.User;

/** Safe projection of a user (never contains the password hash). */
public record UserSummary(
        UUID id,
        Long registrationNumber,
        String fullName,
        String uniEmail,
        String personalEmail,
        String phoneNumber,
        String department,
        Role role,
        boolean verified) {

    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getRegistrationNumber(), u.getFullName(), u.getUniEmail(),
                u.getPersonalEmail(), u.getPhoneNumber(), u.getDepartment(), u.getRole(), u.isVerified());
    }
}
