package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Column(length = 200)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String role = "ADMIN";

    @Column(name = "customer_id")
    private Long customerId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // UserDetails — these fields are for Spring Security only, not for JSON serialization
    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getUsername() {
        return login;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAccountNonExpired()     { return true; }
    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isAccountNonLocked()      { return true; }
    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEnabled()               { return Boolean.TRUE.equals(active); }
}
