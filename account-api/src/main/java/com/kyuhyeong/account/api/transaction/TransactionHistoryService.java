package com.kyuhyeong.account.api.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.entity.TransactionHistory;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.ChangeType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.TransactionHistoryRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 변경 이력 적재 (docs/account.md §11 결정 #5).
 *
 * <p>가구 멤버 모두 거래 수정 가능하므로 누가/언제/어떻게 바꿨는지 추적. 본 서비스는
 * 항상 호출자의 {@code @Transactional} 안에서 동작하므로 별도 트랜잭션 어노테이션
 * 없이 propagation 만 받는다 — 거래 수정이 실패하면 이력도 함께 롤백되어야 한다.
 *
 * <p>{@code Transaction.deletedAt} 체크 등 도메인 검증은 호출자 책임 — 본 서비스는
 * JSON snapshot 직렬화 + insert 만.
 */
@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TransactionHistoryService.class);

    private final TransactionHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void logCreate(Transaction created, Long actorUserId) {
        User actor = userRepository.getReferenceById(actorUserId);
        TransactionHistory record = TransactionHistory.builder()
                .transaction(created)
                .household(created.getHousehold())
                .changedBy(actor)
                .changeType(ChangeType.CREATE)
                .afterJson(serialize(Snapshot.from(created)))
                .build();
        historyRepository.save(record);
    }

    public void logUpdate(Transaction updated, Snapshot before, Long actorUserId) {
        User actor = userRepository.getReferenceById(actorUserId);
        TransactionHistory record = TransactionHistory.builder()
                .transaction(updated)
                .household(updated.getHousehold())
                .changedBy(actor)
                .changeType(ChangeType.UPDATE)
                .beforeJson(serialize(before))
                .afterJson(serialize(Snapshot.from(updated)))
                .build();
        historyRepository.save(record);
    }

    private String serialize(Snapshot snap) {
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize transaction snapshot; storing null", e);
            return null;
        }
    }

    /**
     * 거래의 변경 추적 가능한 필드만 담은 평탄화 스냅샷. Lazy association 직렬화 이슈를
     * 회피하고 history payload 크기를 줄인다.
     */
    public record Snapshot(
            Long categoryId,
            String categoryName,
            BigDecimal amount,
            LocalDateTime occurredAt,
            String merchant,
            String paymentMethod,
            String memo,
            TransactionStatus status
    ) {
        public static Snapshot from(Transaction t) {
            return new Snapshot(
                    t.getCategory().getId(),
                    t.getCategory().getName(),
                    t.getAmount(),
                    t.getOccurredAt(),
                    t.getMerchant(),
                    t.getPaymentMethod(),
                    t.getMemo(),
                    t.getStatus()
            );
        }
    }
}
