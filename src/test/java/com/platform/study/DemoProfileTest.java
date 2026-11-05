package com.platform.study;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoProfileTest {

    private static final String ORG_ID = "00000000-0000-0000-0000-00000000000a";
    private static final String USER_ID = "00000000-0000-0000-0000-0000000000a1";

    @Autowired
    private MockMvc mvc;

    @Test
    void startsWithoutDatabaseAndStoresStudiesInMemory() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk());

        mvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("__bundler/template")));

        mvc.perform(post("/api/v1/studies")
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Demo study","sessionDurationMin":60,"targetN":15}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Demo study"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(get("/api/v1/studies").header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Demo study"));
    }
}