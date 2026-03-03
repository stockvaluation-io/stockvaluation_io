package io.stockvaluation.enums;

import lombok.Getter;

@Getter
public enum Role {

    SUPER_ADMIN("super admin"),
    ADMIN("admin"),
    USER("user");

    private String name;

    Role(String name) {
        this.name = name;
    }

}
