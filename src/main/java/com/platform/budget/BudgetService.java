package com.platform.budget;

import com.platform.shared.Tenant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserves budget (architecture section 4.3). The conditional UPDATE prevents exceeding the limit
 * even under concurrent requests, while the database CHECK protects against code errors.
 */
@Service
@Profile("!demo")
public class BudgetService {

    /** Zero updated rows means the limit is exhausted; avoid SELECT-then-UPDATE. */
    private static final String RESERVE_SQL = """
            update budget_account
               set reserved_amount = reserved_amount + :amount,
                   version = version + 1
             where id = :id
               and limit_amount - reserved_amount - spent_amount >= :amount
            """;

    private static final String INSERT_ENTRY_SQL = """
            insert into budget_entry
                (org_id, account_id, type, amount, currency, ref_type, ref_id, idempotency_key, created_by)
            select org_id, id, 'RESERVE', :amount, currency, :refType, :refId, :key, :actor
              from budget_account
             where id = :id
            """;

    private static final String KEY_EXISTS_SQL =
            "select count(*) from budget_entry where idempotency_key = :key";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public BudgetService(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /**
    * Reserves an amount across all listed accounts in one transaction.
     *
    * @param accountIds accounts in a fixed lock order: ORG, then STUDY, then CAMPAIGN.
    *                   A consistent order prevents deadlocks between concurrent reservations.
    * @return true if the reservation was applied now; false if the operation was already applied (idempotency).
    * @throws BudgetLimitExceededException if any account cannot accept the amount (the whole operation rolls back).
     */
    public boolean reserve(UUID orgId, UUID actorId, String opKey, BigDecimal amount,
                           String refType, UUID refId, List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            throw new IllegalArgumentException("accountIds must not be empty");
        }
        return tx.execute(status -> {
            Tenant.bind(jdbc, orgId);

            if (count(opKey + ":" + accountIds.get(0)) > 0) {
                return false;
            }

            for (UUID accountId : accountIds) {
                int updated = jdbc.sql(RESERVE_SQL)
                        .param("amount", amount)
                        .param("id", accountId)
                        .update();
                if (updated == 0) {
                    // RuntimeException rolls back all previous updates in this transaction.
                    throw new BudgetLimitExceededException(accountId);
                }
                jdbc.sql(INSERT_ENTRY_SQL)
                        .param("amount", amount)
                        .param("refType", refType)
                        .param("refId", refId)
                        .param("key", opKey + ":" + accountId)
                        .param("actor", actorId)
                        .param("id", accountId)
                        .update();
            }
            return true;
        });
    }

    private long count(String key) {
        return jdbc.sql(KEY_EXISTS_SQL).param("key", key).query(Long.class).single();
    }
}
