package ru.yandex.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class GatewayUsersConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public MapReactiveUserDetailsService userDetailsService(SecurityProperties properties,
                                                          PasswordEncoder passwordEncoder) {
        List<UserDetails> users = properties.users().stream()
                .map(user -> User.withUsername(user.username())
                        .password(passwordEncoder.encode(user.password()))
                        .roles(user.roles().toArray(String[]::new))
                        .build())
                .toList();

        return new MapReactiveUserDetailsService(users);
    }
}
