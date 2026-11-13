package com.platform.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class StubStudyServiceTest {
    private static final UUID ORG_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ORG_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final StubStudyService service = new StubStudyService();

    @Test
    void createTrimsTitleAndStartsAsDraft() {
        StudyView created = service.create(ORG_A, ACTOR,
                new CreateStudyRequest("  Checkout usability  ", 60, 12));
        assertThat(created.title()).isEqualTo("Checkout usability");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.sessionDurationMin()).isEqualTo(60);
        assertThat(created.targetN()).isEqualTo(12);
    }

    @Test
    void newestStudyIsListedFirst() {
        service.create(ORG_A, ACTOR, new CreateStudyRequest("First", 30, 5));
        service.create(ORG_A, ACTOR, new CreateStudyRequest("Second", 30, 5));
        assertThat(service.list(ORG_A)).extracting(StudyView::title).containsExactly("Second", "First");
    }

    @Test
    void studiesAreScopedToTheirOrganization() {
        service.create(ORG_A, ACTOR, new CreateStudyRequest("Only in A", 30, 5));
        assertThat(service.list(ORG_B)).isEmpty();
    }
}