package io.monohull.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import io.monohull.repository.AppUserRepository;

import java.util.List;

/**
 * Loads Monohull login accounts from the {@code app_user} table for Spring Security.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())
                        .disabled(!u.isEnabled())
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }
}
