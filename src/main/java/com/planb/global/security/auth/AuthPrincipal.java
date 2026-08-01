package com.planb.global.security.auth;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.planb.global.security.dto.UserAuthCache;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String role;

    public AuthPrincipal(UserAuthCache userAuthCache){
        this.userId = userAuthCache.userId();
        this.username = userAuthCache.username();
        this.role = userAuthCache.role();
    }



    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public @Nullable String getPassword() {
        return "Password Is Encoded";
    }

    @Override
    public String getUsername() {
        return username;
    }
}
