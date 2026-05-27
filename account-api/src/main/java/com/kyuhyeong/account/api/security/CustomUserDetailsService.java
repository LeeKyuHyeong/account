package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 세션 인증 (formLogin) 의 UserDetails 로딩.
 *
 * <p>Spring Security 의 {@code DaoAuthenticationProvider} 가 본 Service 를 호출하여
 * 사용자를 로드하고 BCrypt 비밀번호 검증을 수행한다. 검증 성공 시 반환된
 * {@link CustomUserDetails} 가 principal 로 HttpSession 에 저장된다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final HouseholdMemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        List<HouseholdMember> memberships = memberRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            // 가구 미가입은 데이터 정합성 오류 — 로그인 차단.
            throw new UsernameNotFoundException("User " + user.getId() + " has no household membership");
        }
        HouseholdMember active = memberships.get(0);
        return new CustomUserDetails(
                user.getId(),
                active.getHousehold().getId(),
                active.getRole().name(),
                user.getEmail(),
                user.getPasswordHash());
    }
}
