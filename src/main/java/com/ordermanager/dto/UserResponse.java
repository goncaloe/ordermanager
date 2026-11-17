package com.ordermanager.dto;

import java.time.Instant;

import com.ordermanager.domain.User;

// Payload de retorno para um user
public record UserResponse(Long id, String name, String email, Instant createdAt) {

    // Helper para converter Entity para DTO
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}