package com.miayang.ecommerce.user.internal;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A role such as USER or ADMIN. Rows are created by Flyway migration V2. */
@Entity
@Table(name = "roles", schema = "users")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    protected Role() {
        // for JPA
    }

    public Role(RoleName name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    // Roles are identified by their name, so two Role objects for "USER" count as equal.
    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && name == role.name;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
