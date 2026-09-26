package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.InputField;
import com.aiblockclock.domain.workflow.IoNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.workflow.InputFieldType;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionInputValidatorTest {
    private static final List<InputField> FIELDS = List.of(
            new InputField("brand_name", "브랜드명", InputFieldType.TEXT, true, List.of()),
            new InputField("budget", "예산", InputFieldType.NUMBER, false, List.of()),
            new InputField("price_tier", "가격대", InputFieldType.CHOICE, true, List.of("보급형", "중가")),
            new InputField("urgent", "급한가", InputFieldType.BOOLEAN, false, List.of()));

    private final ExecutionInputValidator validator = new ExecutionInputValidator();

    @Test
    @DisplayName("시작 노드의 입력 항목에 맞는 값은 그대로 돌려준다")
    void passesValuesMatchingFields() {
        String input = validator.validate(withStart(FIELDS),
                "{\"brand_name\":\"하루\",\"budget\":300,\"price_tier\":\"중가\",\"urgent\":true}");

        assertThat(input).isEqualTo("{\"brand_name\":\"하루\",\"budget\":300,\"price_tier\":\"중가\",\"urgent\":true}");
    }

    @Test
    @DisplayName("필수가 아닌 항목은 비워도 된다")
    void allowsOptionalFieldsToBeMissing() {
        assertThat(validator.validate(withStart(FIELDS), "{\"brand_name\":\"하루\",\"price_tier\":\"보급형\"}"))
                .isEqualTo("{\"brand_name\":\"하루\",\"price_tier\":\"보급형\"}");
    }

    @Test
    @DisplayName("필수 항목이 빠지면 막는다")
    void rejectsMissingRequiredField() {
        assertRejects(withStart(FIELDS), "{\"brand_name\":\"하루\"}", "price_tier");
    }

    @Test
    @DisplayName("값을 아예 보내지 않아도 필수 항목이 있으면 막는다")
    void rejectsNoInputWhenFieldIsRequired() {
        assertRejects(withStart(FIELDS), null, "brand_name");
    }

    @Test
    @DisplayName("숫자 항목에 글자가 오면 막는다")
    void rejectsTextForNumber() {
        assertRejects(withStart(FIELDS), "{\"brand_name\":\"하루\",\"price_tier\":\"중가\",\"budget\":\"삼백\"}", "budget");
    }

    @Test
    @DisplayName("선택지에 없는 값이 오면 막는다")
    void rejectsValueOutsideChoices() {
        assertRejects(withStart(FIELDS), "{\"brand_name\":\"하루\",\"price_tier\":\"프리미엄\"}", "price_tier");
    }

    @Test
    @DisplayName("정의하지 않은 항목이 오면 막는다")
    void rejectsUnknownKey() {
        assertRejects(withStart(FIELDS), "{\"brand_name\":\"하루\",\"price_tier\":\"중가\",\"memo\":\"x\"}", "memo");
    }

    @Test
    @DisplayName("JSON 객체가 아니면 막는다")
    void rejectsNonObject() {
        assertThatThrownBy(() -> validator.validate(withStart(FIELDS), "[1, 2]"))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_INPUT_INVALID);
        assertThatThrownBy(() -> validator.validate(withStart(FIELDS), "{brand"))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_INPUT_INVALID);
    }

    @Test
    @DisplayName("시작 노드가 없으면 값 없이 실행한다")
    void sendsNothingWithoutStartNode() {
        assertThat(validator.validate(GraphFixtures.straightLine(), null)).isNull();
    }

    @Test
    @DisplayName("시작 노드가 없는데 값을 보내면 막는다")
    void rejectsInputWithoutStartNode() {
        assertRejects(GraphFixtures.straightLine(), "{\"brand_name\":\"하루\"}", "brand_name");
    }

    private void assertRejects(WorkflowGraph graph, String input, String key) {
        assertThatThrownBy(() -> validator.validate(graph, input))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.EXECUTION_INPUT_INVALID);
                    assertThat(((CoreException) e).getData()).isEqualTo(Map.of("key", key));
                });
    }

    private static WorkflowGraph withStart(List<InputField> fields) {
        return WorkflowGraph.of(
                List.of(new IoNode(NodeKey.of("s1"), IoType.START, fields, null, null),
                        GraphFixtures.node("n1"), GraphFixtures.end("e1")),
                List.of(GraphFixtures.edge("g1", "s1", "n1"), GraphFixtures.edge("g2", "n1", "e1")));
    }
}
