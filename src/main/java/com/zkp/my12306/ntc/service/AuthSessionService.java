package com.zkp.my12306.ntc.service;

import com.zkp.my12306.ntc.dto.LoginResponseDto;
import com.zkp.my12306.ntc.dto.LoginRequestDto;
import com.zkp.my12306.ntc.dto.RegisterRequestDto;
import com.zkp.my12306.ntc.dto.RegisterResponseDto;
import com.zkp.my12306.ntc.dto.UserInfoResponseDto;
import org.springframework.security.core.Authentication;

public interface AuthSessionService {

    LoginResponseDto login(LoginRequestDto request);

    RegisterResponseDto register(RegisterRequestDto request);

    UserInfoResponseDto me(String username);

    void logout(Authentication authentication);
}
