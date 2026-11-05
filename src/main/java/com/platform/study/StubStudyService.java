package com.platform.study;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("demo")
public class StubStudyService implements StudyOperations {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<StudyView>> studies =
            new ConcurrentHashMap<>();

    @Override
    public StudyView create(UUID orgId, UUID actorId, CreateStudyRequest request) {
        StudyView study = new StudyView(
                UUID.randomUUID(),
                request.title().trim(),
                "DRAFT",
                request.sessionDurationMin(),
                request.targetN());
        studies.computeIfAbsent(orgId, ignored -> new CopyOnWriteArrayList<>()).add(0, study);
        return study;
    }

    @Override
    public List<StudyView> list(UUID orgId) {
        return List.copyOf(studies.getOrDefault(orgId, new CopyOnWriteArrayList<>()));
    }
}