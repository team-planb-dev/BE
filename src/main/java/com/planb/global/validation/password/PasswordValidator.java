package com.planb.global.validation.password;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator
        implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(
            String password,
            ConstraintValidatorContext context
    ) {

        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasLetter = password.matches(".*[A-Za-z].*"); // 영어 (대문자 또는 소문자) 포함
        boolean hasNumber = password.matches(".*\\d.*"); // 숫자 포함
        boolean hasSpecialCharacter = password.matches(".*[!@#$%^&*].*"); // 특수문자 포함

        int conditionCount =
                (hasLetter ? 1 : 0)
                        + (hasNumber ? 1 : 0)
                        + (hasSpecialCharacter ? 1 : 0);

        return conditionCount >= 2;
    }
}