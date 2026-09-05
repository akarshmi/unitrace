package dev.akarshmi.UniTrace.modules.auth.mapper;
import dev.akarshmi.UniTrace.modules.auth.dto.PendingRegistration;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PendingRegistrationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "verified", constant = "true")
    User toUser(PendingRegistration pending);
}