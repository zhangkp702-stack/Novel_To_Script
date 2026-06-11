package com.zkp.my12306.ntc.service.impl;

import com.zkp.my12306.ntc.dto.LoginRequestDto;
import com.zkp.my12306.ntc.dto.LoginResponseDto;
import com.zkp.my12306.ntc.dto.RegisterRequestDto;
import com.zkp.my12306.ntc.dto.RegisterResponseDto;
import com.zkp.my12306.ntc.dto.UserInfoResponseDto;
import org.springframework.dao.DuplicateKeyException;
import com.zkp.my12306.ntc.entity.NtcUserEntity;
import com.zkp.my12306.ntc.service.AuthSessionService;
import com.zkp.my12306.ntc.service.AuthUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final AuthUserService authUserService;
    private final PasswordEncoder passwordEncoder;

    public AuthSessionServiceImpl(
            AuthUserService authUserService,
            PasswordEncoder passwordEncoder) {
        this.authUserService = authUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("username and password are required");
        }
        String username = request.username().trim();
        String password = request.password();

        NtcUserEntity user = authUserService.findByAccount(username)
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        if (!isUserActive(user)) {
            throw new BadCredentialsException("invalid credentials");
        }
        if (!isPasswordValid(password, user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getAccount(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        persistSecurityContextToSession(context);

        authUserService.updateLastLoginAtByAccount(authentication.getName());
        return new LoginResponseDto(authentication.getName(), resolveCurrentSessionId());
    }

    @Override
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (request.account() == null || request.account().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        String account = request.account().trim();
        if (authUserService.findByAccount(account).isPresent()) {
            throw new IllegalStateException("账户已经存在，请直接登录");
        }

        try {
            String passwordHash = passwordEncoder.encode(request.password());
            authUserService.createUser(account, account, passwordHash);
            return new RegisterResponseDto("账户创建成功，请登录");
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("账户已经存在，请直接登录");
        }
    }

    @Override
    public UserInfoResponseDto me(String username) {
        return new UserInfoResponseDto(username, true);
    }

    @Override
    public void logout(Authentication authentication) {
        ServletRequestAttributes attributes = currentServletAttributes();
        if (attributes == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        logoutHandler.logout(attributes.getRequest(), attributes.getResponse(), authentication);
    }

    private void persistSecurityContextToSession(SecurityContext context) {
        ServletRequestAttributes attributes = currentServletAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private String resolveCurrentSessionId() {
        ServletRequestAttributes attributes = currentServletAttributes();
        if (attributes == null) {
            return "";
        }
        HttpSession session = attributes.getRequest().getSession(false);
        return session == null ? "" : session.getId();
    }

    private boolean isUserActive(NtcUserEntity user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            return false;
        }
        return user.getLockedUntil() == null || !user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private boolean isPasswordValid(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (storedPassword.startsWith("{")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    private ServletRequestAttributes currentServletAttributes() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes;
        }
        return null;
    }
}
