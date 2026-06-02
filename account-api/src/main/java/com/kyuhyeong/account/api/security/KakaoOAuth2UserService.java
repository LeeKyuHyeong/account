package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 카카오 OAuth2 로그인 시 사용자 로딩 — formLogin 의 {@code CustomUserDetailsService} 를 대체.
 *
 * <p>흐름:
 * <ol>
 *   <li>카카오 user-info 에서 {@code id}(providerUserId) + 닉네임 추출.</li>
 *   <li>{@code (KAKAO, providerUserId)} 로 기존 유저 조회.</li>
 *   <li>없으면: dev 링크 매핑({@link DevKakaoLinkProperties})에 있으면 그 시드 유저에 연결,
 *       아니면 신규 유저 생성(가구 없음 → 온보딩).</li>
 *   <li>첫 {@link HouseholdMember} 로 활성 가구/역할 결정 → {@link AccountPrincipal} 반환.</li>
 * </ol>
 *
 * <p>가입 직후(가구 없음)는 activeHouseholdId=null 로 반환되고, 인증 성공 핸들러 +
 * {@link SessionHouseholdContextFilter} 가 {@code /web/onboarding} 으로 유도한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoOAuth2UserService extends DefaultOAuth2UserService {

    private static final String FALLBACK_NICKNAME = "카카오사용자";

    private final UserRepository userRepository;
    private final HouseholdMemberRepository memberRepository;
    private final DevKakaoLinkProperties devLinks;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User kakaoUser = super.loadUser(userRequest);
        Map<String, Object> attributes = kakaoUser.getAttributes();

        String providerUserId = String.valueOf(attributes.get("id"));
        String nickname = extractNickname(attributes);

        // 로컬 개발에서 account.dev.kakao-links 매핑을 채울 수 있도록 providerUserId 를 로그로 남긴다.
        log.info("Kakao login: providerUserId={}, nickname={}", providerUserId, nickname);

        User user = userRepository.findByProviderAndProviderUserId(User.PROVIDER_KAKAO, providerUserId)
                .orElseGet(() -> linkOrCreate(providerUserId, nickname));
        user.touchLogin();

        List<HouseholdMember> memberships = memberRepository.findByUserId(user.getId());
        Long activeHouseholdId = null;
        String role = null;
        if (!memberships.isEmpty()) {
            HouseholdMember active = memberships.get(0);
            activeHouseholdId = active.getHousehold().getId();
            role = active.getRole().name();
        }

        return new AccountPrincipal(user.getId(), activeHouseholdId, role, nickname, attributes);
    }

    /** 매핑이 있으면 시드 유저에 연결(로컬 데이터 이어받기), 없으면 신규 카카오 유저 생성. */
    private User linkOrCreate(String providerUserId, String nickname) {
        String seedEmail = devLinks.getKakaoLinks().get(providerUserId);
        if (seedEmail != null) {
            User seed = userRepository.findByEmail(seedEmail).orElseThrow(() ->
                    new IllegalStateException("account.dev.kakao-links 대상 시드 유저 없음: " + seedEmail));
            seed.linkKakao(providerUserId);
            log.info("Linked kakao providerUserId={} to seed user {}", providerUserId, seedEmail);
            return seed;
        }
        return userRepository.save(User.createKakao(providerUserId, nickname));
    }

    /** kakao_account.profile.nickname → properties.nickname → fallback 순으로 닉네임 추출. */
    @SuppressWarnings("unchecked")
    private String extractNickname(Map<String, Object> attributes) {
        Object kakaoAccount = attributes.get("kakao_account");
        if (kakaoAccount instanceof Map<?, ?> account) {
            Object profile = ((Map<String, Object>) account).get("profile");
            if (profile instanceof Map<?, ?> p) {
                Object nickname = ((Map<String, Object>) p).get("nickname");
                if (nickname instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }
        Object properties = attributes.get("properties");
        if (properties instanceof Map<?, ?> props) {
            Object nickname = ((Map<String, Object>) props).get("nickname");
            if (nickname instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return FALLBACK_NICKNAME;
    }
}
