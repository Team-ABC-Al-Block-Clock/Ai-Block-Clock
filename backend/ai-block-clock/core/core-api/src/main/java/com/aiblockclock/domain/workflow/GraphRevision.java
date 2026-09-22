package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.ValidationStatus;

public record GraphRevision(Revision revision, ValidationStatus validationStatus) {
}
