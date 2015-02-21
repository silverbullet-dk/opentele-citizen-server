package org.opentele.server.citizen
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import org.opentele.server.citizen.QuestionnaireDownloadService
import org.opentele.server.model.patientquestionnaire.PatientBooleanNode
import org.opentele.server.model.patientquestionnaire.PatientEndNode
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.questionnaire.Questionnaire
import org.opentele.server.model.questionnaire.QuestionnaireHeader

@TestMixin(GrailsUnitTestMixin)
class QuestionnaireDownloadServiceTests {
    QuestionnaireDownloadService service = new QuestionnaireDownloadService()
    PatientQuestionnaire questionnaire = questionnaire()

    void testFillsOutMetadata() {
        def output = service.asJson(questionnaire)

        assert output['name'] == 'Test questionnaire'
        assert !output['cpr']
        assert output['id'] == 5
        assert output['startNode'] == '22'
        assert output['endNode'] == '23'
    }

    void testOutputsNodesCorrespondingToQuestionnaireNodes() {
        def output = service.asJson(questionnaire)

        def nodes = output['nodes']

        assert nodes.size == 2
        assert nodeOfType('AssignmentNode', nodes) != null
        assert nodeOfType('EndNode', nodes) != null
    }

    void testOutputsOutputVariables() {
        def output = service.asJson(questionnaire)

        def outputVariables = output['output']
        assert outputVariables.size == 1
        assert outputVariables[0]['name'] == 'variable'
        assert outputVariables[0]['type'] == 'Boolean'
    }

    private def nodeOfType(type, nodes) {
        nodes.find { it[type] != null }
    }

    private def questionnaire() {
        // We create a very simple questionnaire with just two nodes: A boolean node and an end node.
        // The actual construction of the output node list and output variables should not be tested here,
        // only the fact that it seems like the service delegates the construction to something that knows
        // what to do.
        def questionnaireHeader = new QuestionnaireHeader( name: 'Test questionnaire' )
        def templateQuestionnaire = new Questionnaire( questionnaireHeader: questionnaireHeader )
        def result = new PatientQuestionnaire(templateQuestionnaire: templateQuestionnaire)
        result.id = 5
        result.startNode = booleanNode(22)
        result.nodes = [result.startNode, endNode(23)]
//        result.patient = new Patient(cpr: '1234567890')
        result
    }

    private def booleanNode(id) {
        def result = new PatientBooleanNode()
        result.id = id
        result.defaultNext = dummyNode()
        result.variableName = 'variable'
        result.value = true
        result
    }

    private def endNode(id) {
        def result = new PatientEndNode()
        result.id = id
        result
    }

    private def dummyNode() {
        new PatientBooleanNode()
    }
}
