package com.IMJM.config;

import com.IMJM.jwt.AdminJWTFilter;
import com.IMJM.jwt.JWTUtil;
import com.IMJM.jwt.AdminLoginFilter;
import com.IMJM.jwt.UserJWTFilter;
import com.IMJM.jwt.UserSuccessHandler;
import com.IMJM.user.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserSuccessHandler userSuccessHandler;
    private final JWTUtil jwtUtil;
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(AuthenticationConfiguration authenticationConfiguration,
                          UserSuccessHandler userSuccessHandler,
                          JWTUtil jwtUtil,
                          CustomOAuth2UserService customOAuth2UserService) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.userSuccessHandler = userSuccessHandler;
        this.jwtUtil = jwtUtil;
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors((cors) -> cors
                        .configurationSource(new CorsConfigurationSource() {

                            @Override
                            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {

                                CorsConfiguration configuration = new CorsConfiguration();

                                configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173", "http://localhost:5174"));
                                configuration.setAllowedMethods(List.of("*"));
                                configuration.setAllowedHeaders(List.of("*"));
                                configuration.setExposedHeaders(List.of("Set-Cookie", "Authorization"));
                                configuration.setAllowCredentials(true);
                                configuration.setMaxAge(3600L);

                                return configuration;
                            }
                        }));

        http
                .csrf((auth) -> auth.disable())
                .formLogin((auth) -> auth.disable())
                .logout(logout -> logout.disable())
                .httpBasic((auth) -> auth.disable());

        //oauth2
        http
                .oauth2Login((oauth2)-> oauth2
                        .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                                .userService(customOAuth2UserService))
                        .successHandler(userSuccessHandler)
                );

        //경로별 인가 작업
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/login", "/", "/join", "/user").permitAll()
                        .requestMatchers("/admin").hasRole("ADMIN")
                        .requestMatchers("/check-login").authenticated()
                        .anyRequest().authenticated());

        http
                .addFilterBefore(new AdminJWTFilter(jwtUtil), AdminLoginFilter.class)
                .addFilterBefore(new UserJWTFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new UserJWTFilter(jwtUtil), OAuth2LoginAuthenticationFilter.class)
                .addFilterAt(new AdminLoginFilter(authenticationManager(authenticationConfiguration), jwtUtil), UsernamePasswordAuthenticationFilter.class);

        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}