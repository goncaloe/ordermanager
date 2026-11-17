package com.ordermanager.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ordermanager.domain.User;
import com.ordermanager.dto.UserRequest;
import com.ordermanager.dto.UserResponse;
import com.ordermanager.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // paginação na listagem para prevenir overhead de memória: OutOfMemoryError
    @GetMapping
    public Page<UserResponse> list(Pageable pageable) {
        return userService.list(pageable).map(UserResponse::from);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return UserResponse.from(userService.get(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        User user = userService.create(request.name(), request.email()); // cria entidade
        URI location = URI.create("/api/users/" + user.getId()); // para o cliente saber onde consultar o user criado
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return UserResponse.from(userService.update(id, request.name(), request.email()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}