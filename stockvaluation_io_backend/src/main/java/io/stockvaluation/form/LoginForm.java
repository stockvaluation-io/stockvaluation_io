package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class LoginForm {

    private String email;

    private String password;

    private Boolean rememberMe;
}
