package ru.yandex.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<User> users) {

    // возможно я понял "внешняя конфигурация" слишком буквально
    public SecurityProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record User(String username, String password, List<String> roles) {
    }
}
