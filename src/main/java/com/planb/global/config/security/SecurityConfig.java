package com.planb.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import com.planb.global.security.auth.UserDetailsServiceImpl;
import com.planb.global.security.filter.JwtFilter;
import com.planb.global.security.filter.JwtLoginFilter;
import com.planb.global.security.filter.JwtLogoutFilter;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.validator.RefreshTokenValidator;
import com.planb.global.utils.web.CookieUtil;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final RefreshService refreshService;
    private final UserAuthCacheService userAuthCacheService;
    private final CookieUtil cookieUtil;
    private final RefreshTokenValidator refreshTokenValidator;
    private final ObjectMapper objectMapper;
    private final UserAuthCacheRepository userAuthCacheRepository;


    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration)
            throws Exception {

        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            UserDetailsServiceImpl userDetailsService,
            BCryptPasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public JwtLogoutFilter jwtLogoutFilter(){
        return new JwtLogoutFilter(jwtUtil,
                refreshService,
                cookieUtil,
                refreshTokenValidator);
    }

    @Bean
    public JwtLoginFilter jwtLoginFilter(AuthenticationManager authenticationManager){
        return new JwtLoginFilter(objectMapper,
                authenticationManager,
                jwtUtil,
                refreshService,
                userAuthCacheService,
                cookieUtil);
    }

    @Bean
    public JwtFilter jwtFilter(){
        return new JwtFilter(jwtUtil,
                userAuthCacheRepository);

    }

    // API 문서 및 옵저버 툴 관련 설정
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity httpSecurity)
            throws Exception {

        httpSecurity
                .securityMatcher(
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/mock/api/**",
                        "/docs/api/**",
                        "/ws-stomp",
                        "/ws-stomp/**"
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain
            (HttpSecurity httpSecurity, JwtLoginFilter jwtLoginFilter)
            throws Exception{

        httpSecurity
                .csrf(AbstractHttpConfigurer::disable);

        httpSecurity
                .formLogin(AbstractHttpConfigurer::disable);
        httpSecurity
                .httpBasic(AbstractHttpConfigurer::disable);
        httpSecurity
                .addFilterAt(jwtLoginFilter,
                        UsernamePasswordAuthenticationFilter.class); // 필터 순서2 (로그인)
        httpSecurity
                .addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class); // 필터 순서1 (JWT인증)
        httpSecurity
                .addFilterBefore(jwtLogoutFilter(), LogoutFilter.class); // 필터순서 1 ( 로그아웃 )

        httpSecurity
                .cors(Customizer.withDefaults());
        httpSecurity
                .sessionManagement((session)->session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        httpSecurity
                .authorizeHttpRequests((auth)->auth
                        .requestMatchers("/login",
                                "/api/v1/user/create",
                                "/api/v1/user/check/duplication/**").permitAll()
                        .anyRequest().authenticated()
                );

        return httpSecurity.build();
    }
}
