package com.planb.domain.user.service;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.TermsAgreement;
import com.planb.domain.user.dto.request.ResetPasswordRequest;
import com.planb.domain.user.dto.response.RecoveryQuestionResponse;
import com.planb.domain.user.dto.response.ResetPasswordResponse;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public User create(UserCreateRequest userCreateRequest) {

        return User
                .builder()
                .username(userCreateRequest
                        .username())
                .password(bCryptPasswordEncoder
                        .encode(userCreateRequest
                                .password()))
                .nickname(userCreateRequest.nickname())
                .termsAgreement(
                        new TermsAgreement(
                                userCreateRequest
                                        .ageRequirementAgreed(),
                                userCreateRequest
                                        .serviceTermsAgreed(),
                                userCreateRequest
                                        .privacyCollectionAgreed()
                        ))
                .accountRecovery(
                        AccountRecovery.of(
                                userCreateRequest
                                        .recoveryQuestion(),
                                userCreateRequest
                                        .recoveryAnswer()))
                .role("USER")
                .deleted(false)
                .build();

    }

    public void save(User user) {
        userRepository.save(user);
    }

    public void delete(User user) {
        user.delete();
    }

    public void resetPassword(
            User user,
            String newPassword
    ) {

        user.changePassword(bCryptPasswordEncoder
                .encode(newPassword));
    }

    public UserCreateResponse createResponse(User user) {

        return new UserCreateResponse(
                user.getUsername(),
                Instant.now(),
                Instant.now()
        );
    }

    public UserDeleteResponse deleteResponse(User user) {

        return new UserDeleteResponse(
                user.getUsername(),
                user.getDeletedAt()
        );
    }

    public List<RecoveryQuestionResponse> findRecoveryQuestions() {

        return RecoveryQuestionResponse.all();
    }

    public ResetPasswordResponse resetPassword(
            User user,
            ResetPasswordRequest request
    ) {

        boolean matched = user.getAccountRecovery() != null
                && user
                        .getAccountRecovery()
                        .matches(
                        request.recoveryQuestion(),
                        request.recoveryAnswer()
                );

        if (!matched) {
            throw new BaseException(BaseExceptionEnum.RECOVERY_ANSWER_MISMATCH);
        }

        resetPassword(user, request.newPassword());

        return new ResetPasswordResponse(
                user.getUsername(),
                Instant.now()
        );
    }

}
