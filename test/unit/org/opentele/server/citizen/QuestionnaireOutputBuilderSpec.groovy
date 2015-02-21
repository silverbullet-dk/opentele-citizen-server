package org.opentele.server.citizen

import grails.test.mixin.Mock
import org.opentele.server.model.MeterType
import org.opentele.server.model.patientquestionnaire.PatientBooleanNode
import org.opentele.server.model.patientquestionnaire.PatientChoiceNode
import org.opentele.server.model.patientquestionnaire.PatientEndNode
import org.opentele.server.model.patientquestionnaire.PatientMeasurementNode
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.patientquestionnaire.PatientTextNode
import org.opentele.server.model.questionnaire.MeasurementNode
import org.opentele.server.model.questionnaire.Questionnaire
import org.opentele.server.model.questionnaire.QuestionnaireHeader
import org.opentele.server.core.model.types.DataType
import org.opentele.server.core.model.types.MeterTypeName
import org.opentele.server.core.model.types.Operation
import org.opentele.server.core.model.types.Severity
import org.opentele.server.citizen.QuestionnaireOutputBuilder
import org.springframework.context.MessageSource
import spock.lang.Specification
import spock.lang.Unroll

@Mock([PatientBooleanNode, PatientMeasurementNode])
class QuestionnaireOutputBuilderSpec extends Specification {

    @Unroll
    def 'tests that paths from IONode based measurement nodes contains severity assignments'() {
        setup:
        QuestionnaireOutputBuilder outputBuilder = new QuestionnaireOutputBuilder(Mock(MessageSource))

        when:
        outputBuilder.build(questionnaireWithMeasurementNode(meterTypeName, false, mapToInputFields))

        then:
        !outputBuilder.nodes.isEmpty()
        !outputBuilder.nodes.outputVariables.isEmpty()

        def val = outputBuilder.nodes.find{ it.IONode != null }
        def button = val.IONode.elements.find {it.TwoButtonElement != null }

        button.TwoButtonElement.rightNext.startsWith("ANSEV")
        button.TwoButtonElement.leftNext.startsWith("AN_")

        outputBuilder.outputVariables.find{ it.key.endsWith("CANCEL") != null }
        outputBuilder.outputVariables.find{ it.key.endsWith("SEVERITY") != null }

        where:
        meterTypeName                        | mapToInputFields
        MeterTypeName.BLOOD_PRESSURE_PULSE   | true
        MeterTypeName.TEMPERATURE            | true
        MeterTypeName.WEIGHT                 | true
        MeterTypeName.HEMOGLOBIN             | true
        MeterTypeName.SATURATION             | true
        MeterTypeName.SATURATION_W_OUT_PULSE | true
    }

    @Unroll
    def 'tests that paths from non-IONode based measurement nodes contains severity assignments'() {
        setup:
        QuestionnaireOutputBuilder outputBuilder = new QuestionnaireOutputBuilder(Mock(MessageSource))

        when:
        outputBuilder.build(questionnaireWithMeasurementNode(meterTypeName, simulate, mapToInputFields))

        then:
        !outputBuilder.nodes.isEmpty()
        !outputBuilder.nodes.outputVariables.isEmpty()

        def val = outputBuilder.nodes.find{ it[NodeName] != null }

        val[NodeName].next.startsWith("ANSEV")
        val[NodeName].nextFail.startsWith("AN_")

        outputBuilder.outputVariables.find{ it.key.endsWith("CANCEL") != null }
        outputBuilder.outputVariables.find{ it.key.endsWith("SEVERITY") != null }

        where:
        meterTypeName                                    | mapToInputFields   | simulate  | NodeName
        MeterTypeName.URINE                              | false              | false     | "UrineDeviceNode"
        MeterTypeName.URINE                              | false              | true      | "UrineDeviceNode"
        MeterTypeName.URINE_GLUCOSE                      | false              | false     | "GlucoseUrineDeviceNode"
        MeterTypeName.URINE_GLUCOSE                      | false              | true      | "GlucoseUrineDeviceNode"
        MeterTypeName.CRP                                | false              | false     | "CRPNode"
        MeterTypeName.CRP                                | false              | true      | "CRPNode"
        MeterTypeName.TEMPERATURE                        | false              | false     | "TemperatureDeviceNode"
        MeterTypeName.TEMPERATURE                        | false              | true      | "TemperatureDeviceNode"
        MeterTypeName.WEIGHT                             | false              | false     | "WeightDeviceNode"
        MeterTypeName.WEIGHT                             | false              | true      | "WeightTestDeviceNode"
        MeterTypeName.HEMOGLOBIN                         | false              | false     | "HaemoglobinDeviceNode"
        MeterTypeName.HEMOGLOBIN                         | false              | true      | "HaemoglobinDeviceNode"
        MeterTypeName.LUNG_FUNCTION                      | false              | false     | "LungMonitorDeviceNode"
        MeterTypeName.LUNG_FUNCTION                      | false              | true      | "LungMonitorTestDeviceNode"
        MeterTypeName.BLOOD_PRESSURE_PULSE               | false              | false     | "BloodPressureDeviceNode"
        MeterTypeName.BLOOD_PRESSURE_PULSE               | false              | true      | "BloodPressureTestDeviceNode"
        MeterTypeName.SATURATION                         | false              | false     | "SaturationDeviceNode"
        MeterTypeName.SATURATION                         | false              | true      | "SaturationTestDeviceNode"
        MeterTypeName.SATURATION_W_OUT_PULSE             | false              | false     | "SaturationWithoutPulseDeviceNode"
        MeterTypeName.SATURATION_W_OUT_PULSE             | false              | true      | "SaturationWithoutPulseTestDeviceNode"
        MeterTypeName.CTG                                | false              | false     | "MonicaDeviceNode"
        MeterTypeName.CTG                                | false              | true      | "MonicaTestDeviceNode"
        MeterTypeName.BLOODSUGAR                         | false              | false     | "BloodSugarDeviceNode"
        MeterTypeName.BLOODSUGAR                         | true               | false     | "BloodSugarManualDeviceNode"
        MeterTypeName.BLOODSUGAR                         | false              | true      | "BloodSugarTestDeviceNode"
        MeterTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT | false              | true      | "ContinuousBloodSugarTestDeviceNode"
        MeterTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT | false              | false     | "ContinuousBloodSugarDeviceNode"
    }

    @Unroll
    def 'tests that json for relevant devices contain device_id'() {
        setup:
        QuestionnaireOutputBuilder outputBuilder = new QuestionnaireOutputBuilder(Mock(MessageSource))

        when:
        outputBuilder.build(questionnaireWithMeasurementNode(meterTypeName, false, false))

        then:
        !outputBuilder.nodes.isEmpty()
        !outputBuilder.nodes.outputVariables.isEmpty()

        def val = outputBuilder.nodes.find{ it[NodeName] != null }

        val[NodeName].deviceId != null
        val[NodeName].deviceId.name.endsWith("DEVICE_ID")
        val[NodeName].deviceId.type == "String"

        where:
        meterTypeName                                    | NodeName
        MeterTypeName.WEIGHT                             | "WeightDeviceNode"
        MeterTypeName.LUNG_FUNCTION                      | "LungMonitorDeviceNode"
        MeterTypeName.BLOOD_PRESSURE_PULSE               | "BloodPressureDeviceNode"
        MeterTypeName.SATURATION                         | "SaturationDeviceNode"
        MeterTypeName.SATURATION_W_OUT_PULSE             | "SaturationWithoutPulseDeviceNode"
        MeterTypeName.CTG                                | "MonicaDeviceNode"
        MeterTypeName.BLOODSUGAR                         | "BloodSugarDeviceNode"
        MeterTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT | "ContinuousBloodSugarDeviceNode"
    }

    def 'tests that textNode is generated correctly'() {
        setup:
        QuestionnaireOutputBuilder outputBuilder = new QuestionnaireOutputBuilder(Mock(MessageSource))

        when:
        outputBuilder.build(questionnaireWithTextNode())

        then:
        !outputBuilder.nodes.isEmpty()

        def ioNode = outputBuilder.nodes.find{ it.IONode != null }
        ioNode.IONode.elements.find { it.ButtonElement != null }
        def textViewElements = ioNode.IONode.elements.findAll { it.TextViewElement != null }
        textViewElements.each {

            assert it.TextViewElement != null
            assert it.TextViewElement.text == "Headline" || it.TextViewElement.text == "text"
        }
    }

    def 'tests that questionnaire w/choiceNode is generated correctly'() {
        setup:
        QuestionnaireOutputBuilder outputBuilder = new QuestionnaireOutputBuilder(Mock(MessageSource))

        when:
        outputBuilder.build(questionnaireWithChoiceNode())

        then:
        !outputBuilder.nodes.isEmpty()
    }

    private def questionnaireWithMeasurementNode(typeName, simulate, mapToInputFields) {

        def questionnaireHeader = new QuestionnaireHeader( name: 'Test questionnaireWithMeasurementNode' )
        def templateQuestionnaire = new Questionnaire( questionnaireHeader: questionnaireHeader )

        def questionnaire = new PatientQuestionnaire(templateQuestionnaire: templateQuestionnaire, simulate: simulate, mapToInputFields: mapToInputFields)
        questionnaire.id = 5

        def endNode = endNode(23)
        def bpNode = measurementNode(22, endNode, typeName, simulate, mapToInputFields)

        questionnaire.startNode = bpNode
        questionnaire.nodes = [bpNode, endNode]

        questionnaire
    }

    private def questionnaireWithTextNode() {

        def questionnaireHeader = new QuestionnaireHeader( name: 'Test questionnaireWithTextNode' )
        def templateQuestionnaire = new Questionnaire( questionnaireHeader: questionnaireHeader )

        def questionnaire = new PatientQuestionnaire(templateQuestionnaire: templateQuestionnaire)
        questionnaire.id = 5

        def endNode = endNode(23)
        def textNode = textNode(22, endNode)

        questionnaire.startNode = textNode
        questionnaire.nodes = [textNode, endNode]

        questionnaire
    }

    private def questionnaireWithChoiceNode() {

        def questionnaireHeader = new QuestionnaireHeader( name: 'Test questionnaireWithChoiceNode' )
        def templateQuestionnaire = new Questionnaire( questionnaireHeader: questionnaireHeader )

        def questionnaire = new PatientQuestionnaire(templateQuestionnaire: templateQuestionnaire)
        questionnaire.id = 5

        def endNode = endNode(23)

        def choiceNode = choiceNode(22, endNode)

        def bpNode = measurementNode(42, choiceNode, MeterTypeName.BLOOD_PRESSURE_PULSE, false, false)

        choiceNode.inputNode = bpNode
        choiceNode.inputVar = MeasurementNode.SYSTOLIC_VAR

        questionnaire.startNode = choiceNode
        questionnaire.nodes = [choiceNode, bpNode, endNode]

        questionnaire
    }

    private def textNode(id, next) {

        def node = new PatientTextNode()
        node.id = id
        node.defaultNext = next
        node.headline = "Headline"
        node.text = "text"

        node
    }

    private def choiceNode(id, next) {

        def node = new PatientChoiceNode()
        node.id = id
        node.defaultNext = next
        node.defaultSeverity = Severity.GREEN
        node.alternativeNext= next
        node.alternativeSeverity = Severity.RED
        node.dataType = DataType.INTEGER
        node.operation = Operation.GREATER_THAN
        node.nodeValue =  new Integer(50)

        node
    }

    private def measurementNode(id, next, meterTypeName, simulate, mapToInputFields) {

        def node = new PatientMeasurementNode()
        node.id = id
        node.defaultNext = next
        node.nextFail = next
        node.nextFailSeverity = Severity.RED
        node.defaultSeverity = Severity.YELLOW
        node.mapToInputFields = mapToInputFields
        node.simulate = simulate
        node.text = "MeasurementNode"
        node.meterType = new MeterType(name: meterTypeName)

        node
    }

    private def endNode(id) {
        def result = new PatientEndNode()
        result.id = id
        result
    }
}
