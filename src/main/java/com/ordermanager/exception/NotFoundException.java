package com.ordermanager.exception;

// Lança exceção quando uma entidade não existe. Http codigo 404.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String entity, Object id) {
        super("%s with id %s was not found".formatted(entity, id));
    }
}