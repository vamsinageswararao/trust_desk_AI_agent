package com.trustdesk.config;

import com.trustdesk.security.ApiSecurityErrorHandler;
import com.trustdesk.security.DemoTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless bearer-token security. All endpoints require the configured demo token. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TrustDeskProperties properties,
                                            ApiSecurityErrorHandler errorHandler) throws Exception {
        // The filter is built here (not a @Bean) so Boot does not also register it as a plain servlet filter.
        DemoTokenAuthenticationFilter tokenFilter = new DemoTokenAuthenticationFilter(properties.auth().token(), properties.auth().managerToken());
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/tool-actions/*/approve").hasRole("SUPPORT_MANAGER")
                .anyRequest().authenticated())
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Disables Boot's auto-generated default user/password (we only use the demo token). */
    @Bean
    UserDetailsService noUsers() {
        return username -> {
            throw new UsernameNotFoundException("Password login is disabled");
        };
    }
}
