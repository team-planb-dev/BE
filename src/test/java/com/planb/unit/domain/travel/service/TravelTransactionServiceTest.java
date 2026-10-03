package com.planb.unit.domain.travel.service;

import com.planb.domain.travel.service.TravelTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelTransactionServiceTest {

    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private final TravelTransactionService service =
            new TravelTransactionService(transactionManager);

    @Test
    @DisplayName("건강정보 조회용 읽기 전용 트랜잭션 경계")
    void readOnly() {

        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());

        String result = service.readOnly(() -> "snapshot");

        org.mockito.ArgumentCaptor<TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);

        verify(transactionManager)
                .getTransaction(definition.capture());
        verify(transactionManager)
                .commit(any());
        assertTrue(definition
                        .getValue()
                        .isReadOnly());
        assertEquals("snapshot", result);
    }

    @Test
    @DisplayName("여행 전체 저장용 쓰기 트랜잭션 경계와 실패 롤백")
    void writeRollback() {

        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.write(() -> {
                    throw new IllegalStateException("write failed");
                })
        );

        org.mockito.ArgumentCaptor<TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);

        verify(transactionManager)
                .getTransaction(definition.capture());
        verify(transactionManager)
                .rollback(any());
        assertFalse(definition
                        .getValue()
                        .isReadOnly());
        assertEquals("write failed", failure.getMessage());
    }
}
