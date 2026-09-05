package dev.akarshmi.UniTrace.modules.user.repository;

import dev.akarshmi.UniTrace.modules.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Boolean existsUserByUniEmailOrRegistrationNumber(@Email String uniEmail, @NotBlank Long registrationNumber);


    Optional<User> findByUniEmail(@Email @NotBlank String uniEmail);
}
