package com.ordermanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordermanager.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}