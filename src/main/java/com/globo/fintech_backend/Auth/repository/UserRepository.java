package com.globo.fintech_backend.Auth.repository;

import com.globo.fintech_backend.Auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);
}