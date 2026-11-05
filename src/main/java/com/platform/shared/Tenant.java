package com.platform.shared;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Binds an organization to the current transaction so the tenant_isolation RLS policy applies.
 * set_config(..., is_local = true) only lasts until the transaction ends, preventing the context
 * from leaking to neighboring requests that reuse a pooled connection.
 *
 * Call only inside a transaction (TransactionTemplate or @Transactional).
 */
public final class Tenant {

    private Tenant() {
    }

    public static void bind(JdbcClient jdbc, UUID orgId) {
        jdbc.sql("select set_config('app.org_id', :org, true)")
                .param("org", orgId.toString())
                .query(String.class)
                .single();
    }
}
