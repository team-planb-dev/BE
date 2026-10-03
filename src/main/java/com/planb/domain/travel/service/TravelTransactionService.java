package com.planb.domain.travel.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * AI 여행 일정 생성의 조회와 저장 트랜잭션 경계
 */
@Service
public class TravelTransactionService {

    private final TransactionTemplate readOnlyTransaction;
    private final TransactionTemplate writeTransaction;

    public TravelTransactionService(PlatformTransactionManager transactionManager) {

        readOnlyTransaction = new TransactionTemplate(transactionManager);
        readOnlyTransaction.setReadOnly(true);

        writeTransaction = new TransactionTemplate(transactionManager);
    }

    public <T> T readOnly(Supplier<T> operation) {

        return readOnlyTransaction.execute(status -> operation.get());
    }

    public <T> T write(Supplier<T> operation) {

        return writeTransaction.execute(status -> operation.get());
    }
}
