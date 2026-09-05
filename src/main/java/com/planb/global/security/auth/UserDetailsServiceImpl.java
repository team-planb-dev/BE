package com.planb.global.security.auth;

import com.planb.domain.user.constant.SystemAccountConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.planb.query.user.service.UserQueryService;


@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserQueryService userQueryService;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        if (SystemAccountConstants.AI_BOT_USERNAME.equals(username)) {
            throw new UsernameNotFoundException(
                    "로그인할 수 없는 계정입니다."
            );
        }

       return new UserDetailsImpl(userQueryService
               .findByUsername(username));

    }
}
