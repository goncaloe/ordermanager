package com.ordermanager.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ordermanager.domain.User;
import com.ordermanager.exception.BusinessException;
import com.ordermanager.exception.NotFoundException;
import com.ordermanager.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User create(String name, String email) {
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new BusinessException("A user with email '%s' already exists".formatted(normalized));
        }
        User user = userRepository.save(new User(name.trim(), normalized));
        return user;
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User update(Long id, String name, String email) {
        User user = get(id);
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, id)) {
            throw new BusinessException("A user with email '%s' already exists".formatted(normalized));
        }
        user.setName(name.trim());
        user.setEmail(normalized);

        return user;
    }

    @Transactional
    public void delete(Long id) {
        User user = get(id);
        userRepository.delete(user);
    }
}