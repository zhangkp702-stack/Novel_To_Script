package com.zkp.my12306.ntc.config;

import com.zkp.my12306.ntc.entity.NtcUserEntity;
import com.zkp.my12306.ntc.service.AuthUserService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SecurityUserDetailsServiceAdapter implements UserDetailsService {

    private final AuthUserService authUserService;

    public SecurityUserDetailsServiceAdapter(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        NtcUserEntity user = authUserService.findByAccount(username)
                .orElseThrow(() -> new UsernameNotFoundException("account not found"));

        boolean disabled = user.getStatus() == null || user.getStatus() != 1
                || (user.getIsDeleted() != null && user.getIsDeleted() == 1);
        boolean accountLocked = user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now());

        return User.withUsername(user.getAccount())
                .password(user.getPasswordHash())
                .authorities("ROLE_USER")
                .disabled(disabled)
                .accountLocked(accountLocked)
                .build();
    }
}
