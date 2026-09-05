package dev.akarshmi.UniTrace.modules.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User { 

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(nullable = false, unique = true, updatable = false)
    private Long registrationNumber;

    @NotBlank
    private String firstName;
    private String lastName;

    @Email
    @Column(nullable = false, unique = true, updatable = false)
    private String uniEmail;

    @Email
    private String personalEmail;

    private String phoneNumber;
    private String hashedPassword;
    private String department;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.STUDENT;

    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;

    public String getFullName(){
        return firstName + " " + lastName;
    }
    //this is it for now.
}