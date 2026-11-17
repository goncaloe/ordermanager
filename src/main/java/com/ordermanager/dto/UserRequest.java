package com.ordermanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Payload de criação ou edição de um user
public record UserRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email
) { }