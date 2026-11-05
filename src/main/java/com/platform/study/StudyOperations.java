package com.platform.study;

import java.util.List;
import java.util.UUID;

public interface StudyOperations {

    StudyView create(UUID orgId, UUID actorId, CreateStudyRequest request);

    List<StudyView> list(UUID orgId);
}