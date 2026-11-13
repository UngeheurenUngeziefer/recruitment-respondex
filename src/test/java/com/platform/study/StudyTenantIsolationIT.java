package com.platform.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.support.AbstractPostgresIT;
import com.platform.support.TestDatabaseAvailability;

import java.util.UUID;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tenant isolation end to end: the HTTP API, the service, and PostgreSQL RLS working together.
 * The application connects as app_rt, so a leak here would come from a real database policy failure.
 */
@EnabledIf("com.platform.support.TestDatabaseAvailability#databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
class StudyTenantIsolationIT extends AbstractPostgresIT {
    @Autowired
    private MockMvc mvc;

    @Test
    void studiesAreVisibleOnlyToTheOrganizationThatCreatedThem() throws Exception {
        JdbcTemplate owner = ownerJdbc();
        UUID orgA = insertOrganization(owner, "Org A");
        UUID orgB = insertOrganization(owner, "Org B");
        UUID user = insertUser(owner);
        mvc.perform(post("/api/v1/studies")
                        .header("X-Org-Id", orgA)
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Org A study","sessionDurationMin":60,"targetN":10}
                                """))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/studies").header("X-Org-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Org A study"));
        mvc.perform(get("/api/v1/studies").header("X-Org-Id", orgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void creatingAStudyWritesOneAuditRecordInTheSameTransaction() throws Exception {
        JdbcTemplate owner = ownerJdbc();
        UUID org = insertOrganization(owner, "Audited org");
        UUID user = insertUser(owner);
        mvc.perform(post("/api/v1/studies")
                        .header("X-Org-Id", org)
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Audited study","sessionDurationMin":30,"targetN":5}
                                """))
                .andExpect(status().isCreated());
        Integer records = owner.queryForObject(
                "select count(*) from audit_event where org_id = ? and entity = 'study' and action = 'CREATE'",
                Integer.class, org);
        assertThat(records).isEqualTo(1);
    }
}