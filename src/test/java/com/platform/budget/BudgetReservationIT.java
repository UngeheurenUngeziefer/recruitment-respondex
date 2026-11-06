package com.platform.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.support.AbstractPostgresIT;
import com.platform.support.TestDatabaseAvailability;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Budget reservations against real PostgreSQL. The conditional UPDATE and the CHECK constraint are
 * the guarantees under test, so this must not be a mock.
 */
@EnabledIf("com.platform.support.TestDatabaseAvailability#databaseAvailable")
@SpringBootTest
@ActiveProfiles("postgres")
class BudgetReservationIT extends AbstractPostgresIT {
    @Autowired
    private BudgetService budget;

    @Test
    void reservationIsRejectedWhenItWouldExceedTheLimitAndLeavesNoPartialState() {
        JdbcTemplate owner = ownerJdbc();
        UUID org = insertOrganization(owner, "Budget org");
        UUID user = insertUser(owner);
        UUID account = insertAccount(owner, org, new BigDecimal("100.00"));
        UUID studyId = UUID.randomUUID();
        assertThat(budget.reserve(org, user, "op-" + UUID.randomUUID(), new BigDecimal("60.00"),
                "STUDY", studyId, List.of(account))).isTrue();
        assertThatThrownBy(() -> budget.reserve(org, user, "op-" + UUID.randomUUID(), new BigDecimal("60.00"),
                "STUDY", studyId, List.of(account)))
                .isInstanceOf(BudgetLimitExceededException.class);
        assertThat(reserved(owner, account)).isEqualByComparingTo("60.00");
    }

    @Test
    void sameOperationKeyIsAppliedOnlyOnce() {
        JdbcTemplate owner = ownerJdbc();
        UUID org = insertOrganization(owner, "Idempotent org");
        UUID user = insertUser(owner);
        UUID account = insertAccount(owner, org, new BigDecimal("100.00"));
        String opKey = "retry-" + UUID.randomUUID();
        assertThat(budget.reserve(org, user, opKey, new BigDecimal("10.00"), "STUDY", UUID.randomUUID(),
                List.of(account))).isTrue();
        assertThat(budget.reserve(org, user, opKey, new BigDecimal("10.00"), "STUDY", UUID.randomUUID(),
                List.of(account))).isFalse();
        assertThat(reserved(owner, account)).isEqualByComparingTo("10.00");
    }

    private static UUID insertAccount(JdbcTemplate owner, UUID org, BigDecimal limit) {
        UUID id = UUID.randomUUID();
        owner.update("""
                insert into budget_account (id, org_id, scope_type, scope_id, currency, limit_amount)
                values (?, ?, 'ORG', ?, 'EUR', ?)
                """, id, org, org, limit);
        return id;
    }

    private static BigDecimal reserved(JdbcTemplate owner, UUID account) {
        return owner.queryForObject("select reserved_amount from budget_account where id = ?",
                BigDecimal.class, account);
    }
}
