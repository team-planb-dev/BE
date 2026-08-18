package com.planb.domain.user.entity;

import com.planb.global.validation.password.ValidPassword;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import com.planb.global.converter.BooleanToYNConverter;
import com.planb.global.jpa.BaseEntity;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    @NotBlank(message = "이메일은 필수 입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String username;

    @Column(nullable = false)
    @NotBlank(message = "비밀번호는 필수 입니다.")
    private String password;

    @Column(nullable = false)
    private String role;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean deleted;

    @Column(nullable = false)
    @NotBlank(message = "닉네임은 필수 입니다.")
    private String nickname;

    @Embedded
    private TermsAgreement termsAgreement;

    public void delete(){
        this.deleted = true;
        markDeleted();
    }

}
