package org.opentele.server.citizen

import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaireNode
import org.opentele.server.core.model.patientquestionnaire.PatientQuestionnaireNodeVisitor
import org.springframework.context.MessageSource

class QuestionnaireDownloadService {
    MessageSource messageSource

    def asJson(PatientQuestionnaire questionnaire) {
        def outputBuilder = new QuestionnaireOutputBuilder(messageSource)
        outputBuilder.build(questionnaire)

        [
            name: questionnaire.name,
            id: questionnaire.id,
            startNode: startNodeId(questionnaire),
            endNode: endNodeId(questionnaire),

            nodes: outputBuilder.nodes,
            output: output(outputBuilder.outputVariables)
        ]
    }

    private def startNodeId(PatientQuestionnaire questionnaire) {
        questionnaire.startNode.id as String
    }

    private def endNodeId(PatientQuestionnaire questionnaire) {
        for (PatientQuestionnaireNode n : questionnaire.getNodes()) {
            EndNodeIdentifier identifier = new EndNodeIdentifier()
            n.visit(identifier)
            if (identifier.isEndNode) {
                return n.id as String
            }
        }
        null
    }

    private def output(Map<String, String> outputVariables) {
        outputVariables.collect {
            [
                name: it.key,
                type: it.value
            ]
        }
    }

    private class EndNodeIdentifier implements PatientQuestionnaireNodeVisitor {
        boolean isEndNode = false

        @Override
        void visitEndNode(PatientQuestionnaireNode node) {
            isEndNode = true
        }

        @Override
        void visitBooleanNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitChoiceNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitInputNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitNoteInputNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitDelayNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitMeasurementNode(PatientQuestionnaireNode node) {
        }

        @Override
        void visitTextNode(PatientQuestionnaireNode node) {
        }
    }
}
