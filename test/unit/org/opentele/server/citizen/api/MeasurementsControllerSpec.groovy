package org.opentele.server.citizen.api

import grails.buildtestdata.mixin.Build
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.TestFor
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject
import org.opentele.builders.CompletedQuestionnaireBuilder
import org.opentele.builders.MeasurementBuilder
import org.opentele.builders.MeasurementTypeBuilder
import org.opentele.builders.PatientBuilder
import org.opentele.server.citizen.CitizenMeasurementService
import org.opentele.server.core.model.types.MeasurementTypeName
import org.opentele.server.model.*
import org.opentele.server.model.cgm.ContinuousBloodSugarMeasurement
import org.opentele.server.model.cgm.CoulometerReadingEvent
import org.opentele.server.model.cgm.ExerciseEvent
import org.opentele.server.model.cgm.GenericEvent
import org.opentele.server.model.cgm.HyperAlarmEvent
import org.opentele.server.model.cgm.HypoAlarmEvent
import org.opentele.server.model.cgm.ImpendingHyperAlarmEvent
import org.opentele.server.model.cgm.ImpendingHypoAlarmEvent
import org.opentele.server.model.cgm.InsulinEvent
import org.opentele.server.model.cgm.MealEvent
import org.opentele.server.model.cgm.StateOfHealthEvent
import org.opentele.server.model.patientquestionnaire.CompletedQuestionnaire
import org.opentele.server.model.patientquestionnaire.MeasurementNodeResult
import org.opentele.server.model.patientquestionnaire.PatientBooleanNode
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.questionnaire.BooleanNode
import org.opentele.server.model.questionnaire.QuestionnaireHeader
import spock.lang.Specification

@TestFor(MeasurementsController)
@Build([Patient, Measurement, MeasurementNodeResult, QuestionnaireHeader, CompletedQuestionnaire, MeasurementType, PatientQuestionnaire, Clinician, PatientBooleanNode, BooleanNode, ContinuousBloodSugarMeasurement])
class MeasurementsControllerSpec extends Specification{
    Patient patient
    CompletedQuestionnaire completedQuestionnaire
    MeasurementType pulseMeasurementType
    MeasurementType urineMeasurementType
    MeasurementType urineGlucoseMeasurementType
    MeasurementType bloodSugarGlucoseMeasurementType
    MeasurementType bloodPressureGlucoseMeasurementType
    MeasurementType cgmMeasurementType
    def mockSpringSecurityService
    def mockCitizenMeasurementService

    def setup() {
        patient = new PatientBuilder().build()
        patient.user = new User()

        completedQuestionnaire = new CompletedQuestionnaireBuilder().forPatient(patient).build()
        pulseMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.PULSE).build()
        urineMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.URINE).build()
        urineGlucoseMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.URINE_GLUCOSE).build()
        bloodSugarGlucoseMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.BLOODSUGAR).build()
        bloodPressureGlucoseMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.BLOOD_PRESSURE).build()
        cgmMeasurementType = new MeasurementTypeBuilder().ofType(MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT).build()

        mockSpringSecurityService = mockFor(SpringSecurityService)
        mockSpringSecurityService.metaClass.getCurrentUser = { ->
            patient.getUser()
        }
        controller.springSecurityService = mockSpringSecurityService

        mockCitizenMeasurementService = mockFor(CitizenMeasurementService)
        mockCitizenMeasurementService.metaClass.dataForTables = { p, f ->
            return [];
        }
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [];
        }
        controller.citizenMeasurementService = mockCitizenMeasurementService
    }

    void "can get an empty list of measurements links"() {
        when:
        controller.list()

        then:
        def body = new JSONObject(response.text)
        body.measurements.size() == 0
        body.links.self.toURI() != null
    }

    void "can get list of measurement links"() {
        setup:
        mockCitizenMeasurementService.metaClass.dataForTables = { p, f ->
            return [[type: 'BLOOD_SUGAR'], [type: 'PULSE'], [type: 'BLOOD_PRESSURE']]
        }

        when:
        controller.list()

        then:
        def body = new JSONObject(response.text)
        body.measurements.size() == 3
        body.measurements[0].name == 'blood_sugar'
        body.measurements[0].links['measurement'].toURI() != null
        body.measurements[0].links['measurement'].endsWith("/blood_sugar") == true
        body.measurements[1].name == 'pulse'
        body.measurements[1].links['measurement'].toURI() != null
        body.measurements[1].links['measurement'].endsWith("/pulse") == true
        body.measurements[2].name == 'blood_pressure'
        body.measurements[2].links['measurement'].toURI() != null
        body.measurements[2].links['measurement'].endsWith("/blood_pressure") == true
    }

    void "can get empty measurement series"() {
        setup:

        when:
        controller.show('pulse', null)

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "pulse"
        body.unit.equals(null)
        body.measurements == []
        body.links.self.toURI() != null
    }

    void "can get measurement series for simple values"() {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.PULSE).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.PULSE).inQuestionnaire(completedQuestionnaire).build()
        def m3 = new MeasurementBuilder().ofType(MeasurementTypeName.PULSE).inQuestionnaire(completedQuestionnaire).build()
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2, m3];
        }

        when:
        controller.show('pulse', 'ALL')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "pulse"
        body.unit == m1.unit.value()
        body.measurements.size() == 3
        body.measurements[0].timestamp != null
        body.measurements[0].measurement > 0.0
    }

    void "can get measurement series for protein in urine measurements" () {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.URINE).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.URINE).inQuestionnaire(completedQuestionnaire).build()
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2];
        }

        when:
        controller.show('urine', 'Month')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "urine"
        body.unit == m1.unit.value()
        body.measurements.size() == 2
        body.measurements[0].timestamp != null
        body.measurements[0].measurement == m1.protein.value()
    }

    void "can get measurement series for glucose in urine measurements" () {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.URINE_GLUCOSE).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.URINE_GLUCOSE).inQuestionnaire(completedQuestionnaire).build()
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2];
        }

        when:
        controller.show('urine_glucose', 'quarter')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "urine_glucose"
        body.unit == m1.unit.value()
        body.measurements.size() == 2
        body.measurements[0].timestamp != null
        body.measurements[0].measurement == m1.glucoseInUrine.value()
    }

    void "can get measurement series for blood sugar measurements"() {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.BLOODSUGAR).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.BLOODSUGAR).inQuestionnaire(completedQuestionnaire).build()
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2];
        }

        when:
        controller.show('bloodsugar', 'yEar')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "bloodsugar"
        body.unit == m1.unit.value()
        body.measurements.size() == 2
        body.measurements[0].timestamp != null
        body.measurements[0].measurement.value == m1.value
        body.measurements[0].measurement.isAfterMeal == m1.isAfterMeal
        body.measurements[0].measurement.isBeforeMeal == m1.isBeforeMeal
        body.measurements[0].measurement.isControlMeasurement.equals(m1.isControlMeasurement)
        body.measurements[0].measurement.isOutOfBounds.equals(m1.isOutOfBounds)
        body.measurements[0].measurement.otherInformation.equals(m1.otherInformation)
        body.measurements[0].measurement.hasTemperatureWarning.equals(m1.hasTemperatureWarning)
    }

    void "can get measurement series for continuous blood sugar"() {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT).atTime(2013, Calendar.JANUARY, 10).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT).atTime(2013, Calendar.JANUARY, 11).inQuestionnaire(completedQuestionnaire).build()
        (0..5).each { time ->
            m1.addToContinuousBloodSugarEvents(new ContinuousBloodSugarMeasurement(recordNumber: time, time: new Date(), glucoseValueInmmolPerl: time))
        }

        m1.addToContinuousBloodSugarEvents(new HyperAlarmEvent(recordNumber: 2000, time: new Date(), glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new HypoAlarmEvent(recordNumber: 2001, time: new Date(), glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new ImpendingHyperAlarmEvent(impendingNess: 10, recordNumber: 2002, time: new Date(), glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new ImpendingHypoAlarmEvent(impendingNess: 10, recordNumber: 2003, time: new Date(), glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new CoulometerReadingEvent(recordNumber: 2004, time: new Date(), glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new InsulinEvent(recordNumber: 2005, time: new Date(), insulinType: org.opentele.server.model.types.cgm.InsulinType.INTERMEDIATE, units: 10))
        m1.addToContinuousBloodSugarEvents(new ExerciseEvent(recordNumber: 2006, time: new Date(), durationInMinutes: 13, exerciseIntensity: org.opentele.server.model.types.cgm.ExerciseIntensity.HIGH, exerciseType: org.opentele.server.model.types.cgm.ExerciseType.AEROBICS))
        m1.addToContinuousBloodSugarEvents(new MealEvent(recordNumber: 2007, time: new Date(), foodType: org.opentele.server.model.types.cgm.FoodType.BREAKFAST, carboGrams: 1))
        m1.addToContinuousBloodSugarEvents(new StateOfHealthEvent(recordNumber: 2008, time: new Date(), stateOfHealth: org.opentele.server.model.types.cgm.HealthState.DIZZY))
        m1.addToContinuousBloodSugarEvents(new GenericEvent(recordNumber: 2009, time: new Date(), indicatedEvent: "1"))

        m2.addToContinuousBloodSugarEvents(new ContinuousBloodSugarMeasurement(recordNumber: 2020, time: new Date(), glucoseValueInmmolPerl: 4))
        m2.addToContinuousBloodSugarEvents(new StateOfHealthEvent(recordNumber: 2021, time: new Date(), stateOfHealth: org.opentele.server.model.types.cgm.HealthState.DIZZY))

        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2];
        }

        when:
        controller.show('continuous_blood_sugar_measurement', 'week')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.measurements.size() == 14 // Hyper-, Hypo-, ImpendingHyper-, ImpendingHypoAlarmEvent's should not be included.
        body.type == 'continuous_blood_sugar_measurement'
        body.unit == m1.unit.value()

        body.measurements[2]['timestamp'] != null
        body.measurements[2].measurement.type == 'continuous_blood_sugar_measurement'
        body.measurements[2].measurement.value == 2

        body.measurements[6]['timestamp'] != null
        body.measurements[6].measurement.type == 'coulometer_reading'
        body.measurements[6].measurement.value == 1

        body.measurements[7]['timestamp'] != null
        body.measurements[7].measurement.type == 'insulin'
        body.measurements[7].measurement.value == 15
        body.measurements[7].measurement.insulinType == 'INTERMEDIATE'
        body.measurements[7].measurement.units == '10'

        body.measurements[8]['timestamp'] != null
        body.measurements[8].measurement.type == 'exercise'
        body.measurements[8].measurement.value == 16
        body.measurements[8].measurement.exerciseType == 'AEROBICS'
        body.measurements[8].measurement.exerciseIntensity == 'HIGH'
        body.measurements[8].measurement.durationInMinutes == 13

        body.measurements[9]['timestamp'] != null
        body.measurements[9].measurement.type == 'meal'
        body.measurements[9].measurement.value == 17
        body.measurements[9].measurement.foodType == 'BREAKFAST'
        body.measurements[9].measurement.carboGrams == '1'

        body.measurements[10]['timestamp'] != null
        body.measurements[10].measurement.type == 'state_of_health'
        body.measurements[10].measurement.value == 18
        body.measurements[10].measurement.stateOfHealth == 'DIZZY'

        body.measurements[11]['timestamp'] != null
        body.measurements[11].measurement.type == 'generic'
        body.measurements[11].measurement.value == 19
        body.measurements[11].measurement.indicatedEvent == '1'

        body.measurements[13].measurement.type == 'state_of_health'
    }

    void "CGM events are filtered based on their own timestamp"() {
        setup:
        def before = new Date() - 8
        def after = new Date() + 1
        def inBetween = new Date()

        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT).atTime(2013, Calendar.JANUARY, 10).inQuestionnaire(completedQuestionnaire).build()

        m1.addToContinuousBloodSugarEvents(new ContinuousBloodSugarMeasurement(recordNumber: 1, time: before, glucoseValueInmmolPerl: 1))
        m1.addToContinuousBloodSugarEvents(new ContinuousBloodSugarMeasurement(recordNumber: 2, time: inBetween, glucoseValueInmmolPerl: 2))
        m1.addToContinuousBloodSugarEvents(new ContinuousBloodSugarMeasurement(recordNumber: 3, time: after, glucoseValueInmmolPerl: 3))

        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1];
        }

        when:
        controller.show('continuous_blood_sugar_measurement', 'week')

        then:
        def body = new JSONObject(response.text)
        body.measurements.size() == 1
        body.measurements[0].measurement.value == 2
    }

    void "can get measurement series for blood pressure measurements"() {
        setup:
        def m1 = new MeasurementBuilder().ofType(MeasurementTypeName.BLOOD_PRESSURE).inQuestionnaire(completedQuestionnaire).build()
        def m2 = new MeasurementBuilder().ofType(MeasurementTypeName.BLOOD_PRESSURE).inQuestionnaire(completedQuestionnaire).build()
        mockCitizenMeasurementService.metaClass.patientMeasurementsOfType = { p, f, t ->
            return [m1, m2];
        }

        when:
        controller.show('blood_pressure', 'week')

        then:
        def body = new JSONObject(response.text)
        body != null
        body.type == "blood_pressure"
        body.unit == m1.unit.value()
        body.measurements.size() == 2
        body.measurements[0].timestamp != null
        new Double(body.measurements[0].measurement.systolic).equals(m1.systolic)
        new Double(body.measurements[0].measurement.diastolic).equals(m1.diastolic)
    }

    void "will get error status if measurement type is invalid"() {
        when:
        controller.show('something_invalid', 'year')

        then:
        response.status == 422
        def body = new JSONObject(response.text)
        body.message == "Validation failed"
        body.errors.size() == 1
        def error = body.errors[0]
        error.resource == "measurements"
        error.field == "id"
        error.code == "invalid"
    }

    void "will get error status if time filter is invalid"() {
        when:
        controller.show('pulse', 'invalid_filter')

        then:
        response.status == 422
        def body = new JSONObject(response.text)
        body.message == "Validation failed"
        body.errors.size() == 1
        def error = body.errors[0]
        error.resource == "measurements"
        error.field == "filter"
        error.code == "invalid"
    }

    void "time filter = CUSTOM should be invalid in citizen api"() {
        when:
        controller.show('pulse', 'CUSTOM')

        then:
        response.status == 422
        def body = new JSONObject(response.text)
        body.message == "Validation failed"
        body.errors.size() == 1
        def error = body.errors[0]
        error.resource == "measurements"
        error.field == "filter"
        error.code == "invalid"
    }
}
