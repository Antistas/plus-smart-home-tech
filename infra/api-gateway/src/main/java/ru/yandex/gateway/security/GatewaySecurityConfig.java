package ru.yandex.gateway.security;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String[] CATALOG_PATHS = {
            "/api/products", "/api/products/**",
            "/api/categories", "/api/categories/**",
            "/api/inventory", "/api/inventory/**"
    };

    private static final String[] USER_ORDER_PATHS = {
            "/api/orders/by-email", "/api/orders/by-email/",
            "/api/orders/{id:[0-9]+}", "/api/orders/{id:[0-9]+}/"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                        GlobalCorsProperties corsProperties) {
        UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.setCorsConfigurations(corsProperties.getCorsConfigurations());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .httpBasic(Customizer.withDefaults())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, CATALOG_PATHS).permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/orders", "/api/orders/").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, USER_ORDER_PATHS).hasRole("USER")
                        .pathMatchers(HttpMethod.POST, "/api/orders", "/api/orders/**").hasRole("USER")
                        .pathMatchers(HttpMethod.POST, CATALOG_PATHS).hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, CATALOG_PATHS).hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, CATALOG_PATHS).hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, CATALOG_PATHS).hasRole("ADMIN")
                        .anyExchange().denyAll())
                .build();
    }
}
