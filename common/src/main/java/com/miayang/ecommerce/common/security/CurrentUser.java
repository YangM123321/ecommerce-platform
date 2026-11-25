package com.miayang.ecommerce.common.security;

import org.springframework.security.core.Authentication;

/**
 * The logged-in user, taken from the JWT: id from the "sub" claim, admin if they have ROLE_ADMIN.
 * Lets any module know who is calling without depending on the user module.
 */
public record CurrentUser(Long id, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new CurrentUser(Long.valueOf(authentication.getName()), admin);
    }
}
