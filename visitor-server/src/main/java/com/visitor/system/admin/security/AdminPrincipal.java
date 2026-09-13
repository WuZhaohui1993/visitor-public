package com.visitor.system.admin.security;

import java.util.Set;

public record AdminPrincipal(
    Long userId,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions,
    boolean mustChangePassword
) {
}
