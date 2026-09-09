package com.planb.domain.user.service;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.TermsAgreement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public User create(UserCreateRequest userCreateRequest){

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
                                        .privacyCollectionAgreed()))
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

    public void save(User user){
        userRepository.save(user);
    }

    public void delete(User user){
        user.delete();
    }

    public void resetPassword(
            User user,
            String newPassword
    ) {

        user.changePassword(bCryptPasswordEncoder
                .encode(newPassword));
    }

}
