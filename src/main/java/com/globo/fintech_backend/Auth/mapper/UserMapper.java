package com.globo.fintech_backend.Auth.mapper;

import com.globo.fintech_backend.Auth.dto.LoginResponseDTO;
import com.globo.fintech_backend.Auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    LoginResponseDTO toDTO(User user);
}