package io.stockvaluation.form;

import io.stockvaluation.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class SignupForm {

    private String firstName;

    private String lastName;

    private String email;

    private String password;

    private String contact;

    private Role role;
}
