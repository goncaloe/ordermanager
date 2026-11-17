package com.ordermanager.exception;

// Excepção quando quebra uma regra de negócio (duplicados, validações, edits inválidos, etc)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

}