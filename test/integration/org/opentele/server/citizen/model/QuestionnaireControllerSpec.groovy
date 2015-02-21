package org.opentele.server.citizen.model

import org.opentele.server.core.test.AbstractIntegrationSpec
import org.opentele.server.model.Measurement
import org.opentele.server.model.MeasurementType
import org.opentele.server.model.Patient
import org.opentele.server.model.Threshold
import org.opentele.server.model.UrineThreshold
import org.opentele.server.model.patientquestionnaire.CompletedQuestionnaire
import org.opentele.server.model.patientquestionnaire.MeasurementNodeResult
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.questionnaire.Questionnaire
import org.opentele.server.model.questionnaire.QuestionnaireHeader
import org.opentele.server.core.model.types.MeasurementTypeName
import org.opentele.server.core.model.types.ProteinValue
import org.opentele.server.core.model.types.Severity
import org.opentele.server.core.model.types.Unit

import static org.junit.Assert.assertNotNull

class QuestionnaireControllerSpec extends AbstractIntegrationSpec {

    QuestionnaireMobileController controller
    def patient

    def setup() {
        controller = new QuestionnaireMobileController()
        authenticate 'NancyAnn','abcd1234'
        patient = Patient.findByCpr '2512484916'
    }

    def 'can list all questionnaires for a patient'() {
        when:
        controller.listing()

        then:
        !controller.response.json.cpr //should never ever reply with cpr number

        def bloodPressureQuestionnaire = controller.response.json.questionnaires.find { q -> q.name.equalsIgnoreCase("Blodtryk og puls") }

        bloodPressureQuestionnaire != null
        bloodPressureQuestionnaire.version == '1.0'
    }

    def 'can upload blood pressure and pulse result and set severity according to worst measurement'() {
        when:
        inQuestionnaire 'Blodtryk'

        def output = [
            systolic:[type:'Integer', value:systolic.toString()],
            diastolic:[type:'Integer', value:diastolic.toString()],
            pulse:[type:'Integer', value:pulse.toString()],
            deviceId:[type:'String', value:"FFFF2D65"]
        ]
        if (meanArterialPressure != null) { output['meanArterialPressure'] = [type:'Integer', value:meanArterialPressure.toString()] }
        withOutput('BloodPressureDeviceNode', output)

        def (bloodPressureMeasurement, pulseMeasurement) = newestMeasurements(2)
        MeasurementNodeResult measurementNodeResult = bloodPressureMeasurement.measurementNodeResult

        then:
        controller.response.json[0][0].toString() == 'success'
        bloodPressureMeasurement.systolic == systolic
        bloodPressureMeasurement.diastolic == diastolic
        bloodPressureMeasurement.meanArterialPressure == meanArterialPressure
        bloodPressureMeasurement.deviceIdentification == "FFFF2D65"
        pulseMeasurement.value == pulse
        pulseMeasurement.deviceIdentification == "FFFF2D65"
        measurementNodeResult.severity == expectedSeverity

        where:
        systolic | diastolic | meanArterialPressure | pulse | expectedSeverity
             110 |        80 |                  101 |    80 | null
             130 |        80 |                  101 |    80 | Severity.YELLOW // Systolic: Yellow
             110 |        97 |                  101 |    80 | Severity.YELLOW // Diastolic: Yellow
             110 |        80 |                  101 |    95 | Severity.YELLOW // Pulse: Yellow
             130 |        80 |                  101 |   120 | Severity.RED // Blood pressure: Yellow. Pulse: Red
             150 |        91 |                  101 |    95 | Severity.RED // Blood pressure: Red. Pulse: Yellow
               0 |        91 |                  101 |    80 | Severity.RED
             110 |         0 |                  101 |    80 | Severity.RED
             110 |        91 |                    0 |    80 | null // Mean arterial pressure has no thresholds
             110 |        91 |                  101 |     0 | Severity.RED
             110 |        91 |                 null |    80 | null // Mean arterial pressure has no thresholds
    }

    def 'can upload blood pressure result without pulse'() {
        when:
        def oldMeasurement = newestMeasurement()
        inQuestionnaire 'Blodtryk'
        withOutput('BloodPressureDeviceNode', [
            systolic:[type:'Integer', value:'150'],
            diastolic:[type:'Integer', value:'91'],
            meanArterialPressure:[type:'Integer', value:'101'],
            deviceId:[type:'String', value:"ABCD1234"]
        ])
        def (stillOldMeasurement, bloodPressureMeasurement) = newestMeasurements(2)

        then:
        controller.response.json[0][0].toString() == 'success'
        stillOldMeasurement == oldMeasurement
        bloodPressureMeasurement.systolic == 150
        bloodPressureMeasurement.diastolic == 91
        bloodPressureMeasurement.meanArterialPressure == 101
        bloodPressureMeasurement.deviceIdentification == "ABCD1234"
    }

    def 'can upload urine result with legal value'() {
        when:
        inQuestionnaire 'Proteinindhold i urin'
        withOutput('UrineDeviceNode', [
            urine:[type:'String', value:value]
        ])

        then:
        controller.response.json[0][0].toString() == response

        where:
        value                             | response
        ProteinValue.PLUS_THREE.ordinal() | 'success'
        '+5'                              | 'failure'
        '+4.1'                            | 'failure'
        'negativeasdasd'                  | 'failure'
        '1'                               | 'failure'
    }

    def 'can upload temperature result'() {
        when:
        inQuestionnaire 'Temperatur test'
        withOutput('TemperatureDeviceNode', [
            temperature:[type:'Float', value:value]
        ])
        def temperatureMeasurement = newestMeasurement()

        then:
        controller.response.json[0][0].toString() == response
        temperatureMeasurement.value == value

        where:
        value | response
        77.12 | 'success'
            0 | 'success'
    }

    def 'can upload weight result'() {
        when:
        inQuestionnaire 'Vejning'
        withOutput('WeightDeviceNode', [
            weight:[type:'Float', value:value],
            deviceId:[type:'String', value:"1234"],

        ])
        def weightMeasurement = newestMeasurement()

        then:
        controller.response.json[0][0].toString() == response
        weightMeasurement.value == value
        weightMeasurement.deviceIdentification == "1234"

        where:
        value | response
         76.4 | 'success'
            0 | 'success'
    }

    def 'can upload hemoglobin result'() {
        when:
        inQuestionnaire 'Hæmoglobin indhold i blod'
        withOutput('HaemoglobinDeviceNode', [
            haemoglobinValue:[type:'Float', value:value]
        ])
        def hemoglobinMeasurement = newestMeasurement()

        then:
        controller.response.json[0][0].toString() == response
        hemoglobinMeasurement.value == value

        where:
        value | response
            7 | 'success'
            0 | 'success'
    }

    def 'can upload lung function result'() {
        when:
        inQuestionnaire 'Lungefunktion'
        withOutput('LungMonitorDeviceNode', [
            fev1:[type:'Float', value:fev1.toString()],
            fev6:[type:'Float', value:fev6.toString()],
            fev1Fev6Ratio:[type:'Float', value:fev1Fev6Ratio.toString()],
            fef2575:[type:'Float', value:fef2575.toString()],
            goodTest:[type:'Boolean', value:true],
            softwareVersion:[type:'Integer', value:933],
            deviceId:[type:'String', value: "00012345678"]
        ])
        def lungFunctionMeasurement = newestMeasurement()

        then:
        controller.response.json[0][0].toString() == 'success'
        lungFunctionMeasurement.unit == Unit.LITER
        lungFunctionMeasurement.value == fev1
        lungFunctionMeasurement.fev6 == fev6
        lungFunctionMeasurement.fev1Fev6Ratio == fev1Fev6Ratio
        lungFunctionMeasurement.fef2575 == fef2575
        lungFunctionMeasurement.isGoodTest
        lungFunctionMeasurement.fevSoftwareVersion == 933
        lungFunctionMeasurement.deviceIdentification == "00012345678"

        where:
        fev1 | fev6 | fev1Fev6Ratio | fef2575
        4.01 | 4.23 |          0.93 |    4.32
           0 | 4.23 |          0.93 |    4.32
        4.01 |    0 |          0.93 |    4.32
        4.01 | 4.23 |             0 |    4.32
        4.01 | 4.23 |          0.93 |       0
    }

    def 'can upload saturation result'() {
        when:
        inQuestionnaire 'Saturation'
        withOutput('SaturationDeviceNode', [
            saturation:[type:'Integer', value:saturation],
            pulse:[type:'Integer', value:pulse],
            deviceId:[type:'String', value:"123458"]
        ])
        def (saturationMeasurement, pulseMeasurement) = newestMeasurements(2)

        then:
        controller.response.json[0][0].toString() == 'success'
        saturationMeasurement.value == saturation
        saturationMeasurement.deviceIdentification == "123458"
        pulseMeasurement.value == pulse
        pulseMeasurement.deviceIdentification == "123458"

        where:
        saturation | pulse
              95.4 |    65
                 0 |    65
              95.4 |     0
    }

    def 'can upload saturation without pulse'() {
        when:
        def oldMeasurement = newestMeasurement()
        inQuestionnaire 'Saturation'
        withOutput('SaturationDeviceNode', [
            saturation:[type:'Integer', value:97],
            deviceId:[type:'String', value: "123xab"]
        ])
        def (stillOldMeasurement, saturationMeasurement) = newestMeasurements(2)

        then:
        stillOldMeasurement == oldMeasurement
        controller.response.json[0][0].toString() == 'success'
        saturationMeasurement.value == 97.0
        saturationMeasurement.deviceIdentification == "123xab"
    }

    def 'can upload blood sugar result'() {
        when:
        inQuestionnaire 'Blodsukker'
        withOutput('BloodSugarDeviceNode', [
            deviceId:[type:'String', value: "123xab"],
            bloodSugarMeasurements:[
                type:'BloodSugarMeasurements',
                value:[
                    serialNumber: "123xab",
                    measurements: [
                        [
                            timeOfMeasurement: '2013-05-24T11:30:02+02',
                            result: 5.6,
                            isBeforeMeal: true,
                            hasTemperatureWarning: false,
                            isControlMeasurement: false,
                            isAfterMeal: false,
                            isOutOfBounds: false
                        ],
                        [
                            timeOfMeasurement: '2013-05-24T13:00:20+02',
                            result: 6.5,
                            otherInformation: true,
                            isBeforeMeal: false,
                            hasTemperatureWarning: false,
                            isControlMeasurement: false,
                            isAfterMeal: true,
                            isOutOfBounds: false
                        ],
                    ],
                ]
            ]
        ])
        def (firstBloodSugarMeasurement, secondBloodSugarMeasurement) = newestMeasurements(2)

        then:
        controller.response.json[0][0].toString() == 'success'

        firstBloodSugarMeasurement.time == at(2013, Calendar.MAY, 24, 11, 30, 2)
        firstBloodSugarMeasurement.isBeforeMeal
        !firstBloodSugarMeasurement.isAfterMeal
        !firstBloodSugarMeasurement.otherInformation
        firstBloodSugarMeasurement.value == 5.6
        firstBloodSugarMeasurement.deviceIdentification == '123xab'

        secondBloodSugarMeasurement.time == at(2013, Calendar.MAY, 24, 13, 0, 20)
        !secondBloodSugarMeasurement.isBeforeMeal
        secondBloodSugarMeasurement.isAfterMeal
        secondBloodSugarMeasurement.otherInformation
        secondBloodSugarMeasurement.value == 6.5
        secondBloodSugarMeasurement.deviceIdentification == '123xab'
    }

    def 'ignores duplicate blood sugar measurements'() {
        when:
        createMeasurement(patient, MeasurementTypeName.BLOODSUGAR, at(2013, Calendar.MAY, 24, 11, 30, 2), 5)
        inQuestionnaire 'Blodsukker'
        withOutput('BloodSugarDeviceNode', [
            bloodSugarMeasurements:[
                type:'BloodSugarMeasurements',
                value:[
                    measurements: [
                        [
                            // Should be ignored since we already have the above measurement at this time
                            timeOfMeasurement: '2013-05-24T11:30:02+02',
                            result: 5.6,
                            isControlMeasurement: true
                        ],
                        [
                            timeOfMeasurement: '2013-05-24T13:00:20+02',
                            result: 6.5,
                            isControlMeasurement: true
                        ],
                    ]
                ]
            ]
        ])
        def (firstBloodSugarMeasurement, secondBloodSugarMeasurement) = newestMeasurements(2)

        then:
        controller.response.json[0][0].toString() == 'success'

        firstBloodSugarMeasurement.time == at(2013, Calendar.MAY, 24, 11, 30, 2)
        firstBloodSugarMeasurement.value == 5.0

        secondBloodSugarMeasurement.time == at(2013, Calendar.MAY, 24, 13, 0, 20)
        secondBloodSugarMeasurement.value == 6.5
    }

    def 'can set severity for blood sugar measurements'() {
        when:
        inQuestionnaire 'Blodsukker'
        withOutput('BloodSugarDeviceNode', [
                bloodSugarMeasurements:[
                        type:'BloodSugarMeasurements',
                        value:[
                                measurements: [
                                        [
                                                timeOfMeasurement: '2013-05-24T11:30:02+02',
                                                result: firstVal,
                                                isBeforeMeal: true,
                                                hasTemperatureWarning: false,
                                                isControlMeasurement: false,
                                                isAfterMeal: false,
                                                isOutOfBounds: false
                                        ],
                                        [
                                                timeOfMeasurement: '2013-05-24T13:00:20+02',
                                                result: secondVal,
                                                otherInformation: true,
                                                isBeforeMeal: false,
                                                hasTemperatureWarning: false,
                                                isControlMeasurement: false,
                                                isAfterMeal: true,
                                                isOutOfBounds: false
                                        ],
                                ]
                        ]
                ]
        ])
        def (firstBloodSugarMeasurement, secondBloodSugarMeasurement) = newestMeasurements(2)
        MeasurementNodeResult measurementNodeResult = firstBloodSugarMeasurement.measurementNodeResult
        CompletedQuestionnaire completedQuestionnaire = measurementNodeResult.completedQuestionnaire

        then:
        controller.response.json[0][0].toString() == 'success'
        firstBloodSugarMeasurement.value == firstVal
        secondBloodSugarMeasurement.value == secondVal

        measurementNodeResult.severity == expectedSeverity
        completedQuestionnaire.severity == expectedSeverityForCompletedQuestionnaire

        // Nancy's thresholds are:
        // alert: 20 warning: 15 warningLow: 3 alertLow: 1

        where:
        firstVal| secondVal | expectedSeverity | expectedSeverityForCompletedQuestionnaire
        5       |         6 | null             | Severity.GREEN
        5       |         2 | Severity.YELLOW  | Severity.YELLOW
        2       |         5 | Severity.YELLOW  | Severity.YELLOW
        21      |         0 | Severity.RED     | Severity.RED
        8       |         0 | Severity.RED     | Severity.RED
        17      |         5 | Severity.YELLOW  | Severity.YELLOW
    }

    def 'marks questionnaires according to thresholds'() {
        when:
        inQuestionnaire 'Proteinindhold i urin'
        withThreshold(UrineThreshold.build(type: MeasurementType.findByName(MeasurementTypeName.URINE), alertHigh: ProteinValue.PLUS_THREE, alertLow: ProteinValue.NEGATIVE, warningLow: ProteinValue.PLUSMINUS, warningHigh: ProteinValue.PLUS_TWO))
        withOutput('UrineDeviceNode', [
                urine:[type:'String', value:value]
        ])

        then:
        completedQuestionnaire('Proteinindhold i urin').severity == severity

        where:
        value                             | severity
        ProteinValue.PLUS_THREE.ordinal() | Severity.RED
        ProteinValue.PLUS_TWO.ordinal()   | Severity.YELLOW
        ProteinValue.PLUS_ONE.ordinal()   | Severity.GREEN
    }

    def 'can retrieve acknowledgements'() {

        when:
        CompletedQuestionnaire completedQuestionnaire = CompletedQuestionnaire.findByPatientAndAcknowledgedBy(patient, null)
        completedQuestionnaire.acknowledgedDate = new Date()
        completedQuestionnaire.showAcknowledgementToPatient = true
        completedQuestionnaire.save(flush:true)
        completedQuestionnaire.refresh()

        authenticate 'NancyAnn','abcd1234'

        controller.response.reset()

        controller.request.addPreferredLocale(locale)
        controller.acknowledgements()

        then:
        controller.response.json.acknowledgements.message[0].startsWith(message)

        where:
        locale                | message
        //Locale.US             | "Results from \"JSON test\" received "
        new Locale("da", "DK")| "Dine data fra"
    }

    Measurement newestMeasurement() {
        newestMeasurements(1)[0]
    }

    List<Measurement> newestMeasurements(int numberOfMeasurements) {
        def allMeasurementsOldestFirst = Measurement.all.sort { it.id }
        allMeasurementsOldestFirst[-numberOfMeasurements..-1]
    }

    def questionnaireId

    def inQuestionnaire(def name) {
        PatientQuestionnaire patientQuestionnaire = patientQuestionnaire(name)

        controller.params.put 'id', patientQuestionnaire.id
        controller.download()

        assertNotNull controller.response.json.id

        questionnaireId = patientQuestionnaire.id
    }

    def withThreshold(Threshold threshold) {
        patient.setThreshold(threshold)
        patient.save(failOnError:true, flush:true)
    }

    def withOutput(String nodeName, def output) {
        def foundNode = false
        def rootNode
        controller.response.json.nodes.each() {
            if (it.has(nodeName)) {
                rootNode = it.get(nodeName)
                foundNode = true
            }
        }

        assert foundNode
        def measurements = output.collect {
            assert rootNode.has(it.key)

            def measurementNode = rootNode.get(it.key)
            [name:measurementNode.name, type:it.value.type, value:it.value.value]
        }

        controller.response.reset()
        controller.request.json = [
            QuestionnaireId: questionnaireId,
            date: new Date(),
            output:measurements
        ]
        controller.upload()
    }

    def patientQuestionnaire(String name) {
        QuestionnaireHeader questionnaireHeader = QuestionnaireHeader.findByName(name)
        Questionnaire questionnaire = questionnaireHeader.activeQuestionnaire
        PatientQuestionnaire pq = PatientQuestionnaire.findByTemplateQuestionnaire(questionnaire)
        pq.refresh()
        pq
    }

    def completedQuestionnaire(String name) {
        def questionnaire = patientQuestionnaire(name)
        CompletedQuestionnaire.findByPatientQuestionnaireAndPatient(questionnaire, patient)
    }

    def createMeasurement(Patient patient, MeasurementTypeName type, Date time, float value) {
        def measurement = Measurement.build(patient:patient,
            measurementType: MeasurementType.findByName(type.value()),
            time:time,
            value:value
        )
        measurement.save(flush:true)
    }

    Date at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set Calendar.YEAR, year
        calendar.set Calendar.MONTH, month
        calendar.set Calendar.DAY_OF_MONTH, day
        calendar.set Calendar.HOUR_OF_DAY, hour
        calendar.set Calendar.MINUTE, minute
        calendar.set Calendar.SECOND, second
        calendar.getTime()
    }
}
