package com.platform.study;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * WARNING: X-Org-Id and X-User-Id headers are used for development only.
 * Before production, use Spring Security: derive the organization and user from a verified token
 * and check the user's organization membership.
 */
@RestController
@RequestMapping("/api/v1/studies")
public class StudyController {

    private final StudyOperations studies;

    public StudyController(StudyOperations studies) {
        this.studies = studies;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyView create(@RequestHeader("X-Org-Id") UUID orgId,
                            @RequestHeader("X-User-Id") UUID userId,
                            @Valid @RequestBody CreateStudyRequest body) {
        return studies.create(orgId, userId, body);
    }

    @GetMapping
    public List<StudyView> list(@RequestHeader("X-Org-Id") UUID orgId) {
        return studies.list(orgId);
    }
}
