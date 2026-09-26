package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.List;
import java.util.UUID;

public record NodePlacement(
        NodeKey key,
        SourceType sourceType,
        UUID sourceId,
        InputSource inputSource,
        NodeDraft draft,
        ControlType controlType,
        BranchCondition condition,
        IoType ioType,
        List<InputField> fields,
        ResultShape resultShape,
        ResultFormat resultFormat) {
}
