package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.List;

public record IoNode(
        NodeKey key,
        IoType ioType,
        List<InputField> fields,
        ResultShape resultShape,
        ResultFormat resultFormat) implements GraphNode {
    public IoNode {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    @Override
    public SourceType sourceType() {
        return SourceType.IO;
    }

    @Override
    public NodeReference reference() {
        return NodeReference.of(null);
    }

    @Override
    public InputSource inputSource() {
        return InputSource.defaultSource();
    }

    public boolean isStart() {
        return ioType == IoType.START;
    }

    public boolean isEnd() {
        return ioType == IoType.END;
    }
}
