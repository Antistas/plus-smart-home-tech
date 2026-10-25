package ru.yandex.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(GatewaySecurityConfigTest.TestBackendConfig.class)
class GatewaySecurityConfigTest {
    @Autowired
    private WebTestClient client;

    @Test
    void catalogGet_isPublic() {
        client.get().uri("/api/products").exchange().expectStatus().isOk();
    }

    @Test
    void orderCreate_withoutCredentials_isUnauthorized() {
        client.post().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void orderCreate_withUserCredentials_passesSecurity() {
        client.post().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void productWrite_withUserCredentials_isForbidden() {
        client.patch().uri("/api/products/1").header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void productWrite_withAdminCredentials_passesSecurity() {
        client.patch().uri("/api/products/1").header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void allOrders_withUserCredentials_isForbidden() {
        client.get().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void allOrders_withAdminCredentials_passesSecurity() {
        client.get().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void unknownRoute_withAdminCredentials_isForbidden() {
        client.get().uri("/unknown").header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void corsPreflight_isPublic() {
        client.options().uri("http://localhost:8080/api/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:8443")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .exchange().expectStatus().isOk();
    }

    private String basic(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class TestBackendConfig {
        @Bean
        RouterFunction<ServerResponse> testBackendRoutes() {
            return route(path("/test-backend"), request -> ServerResponse.ok().build());
        }

        @Bean
        RouteLocator testGatewayRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("test-backend", route -> route
                            .path("/api/products", "/api/products/**", "/api/orders", "/api/orders/**")
                            .uri("forward:/test-backend"))
                    .build();
        }
    }
}
