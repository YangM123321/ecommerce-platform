package com.miayang.ecommerce.user.internal;

import java.util.List;

import com.miayang.ecommerce.user.internal.AuthDtos.UserResponse;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints. Protected twice on purpose (defense in depth):
 * the URL rule in SecurityConfig and @PreAuthorize here.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository users;

    public AdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return users.findAll(Sort.by("id")).stream()
                .map(UserResponse::from)
                .toList();
    }
}
