package com.whut.training.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankingInvalidationTest {
    @Test void bothCommitAndRollbackInvalidateAgainAfterTheWriteTransactionEnds() {
        var repository = new UserRepository(mock(JdbcTemplate.class));
        for (int status : new int[]{TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_ROLLED_BACK}) {
            long before = repository.rankingVersion();
            TransactionSynchronizationManager.initSynchronization();
            try {
                repository.invalidateRanking();
                assertThat(repository.rankingVersion()).isEqualTo(before + 1);
                for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(status);
                assertThat(repository.rankingVersion()).isEqualTo(before + 2);
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
    }
}
