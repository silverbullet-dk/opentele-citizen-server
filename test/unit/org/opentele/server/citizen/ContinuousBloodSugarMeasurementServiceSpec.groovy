package org.opentele.server.citizen

import grails.test.mixin.TestFor
import org.opentele.server.citizen.ContinuousBloodSugarMeasurementService
import org.opentele.server.core.util.ISO8601DateParser
import org.opentele.server.model.cgm.*
import org.opentele.server.model.types.cgm.ExerciseIntensity
import org.opentele.server.model.types.cgm.ExerciseType
import org.opentele.server.model.types.cgm.FoodType
import org.opentele.server.model.types.cgm.HealthState
import org.opentele.server.model.types.cgm.InsulinType
import spock.lang.Specification

@TestFor(ContinuousBloodSugarMeasurementService)
class ContinuousBloodSugarMeasurementServiceSpec extends Specification {

    def "can parse an StateOfHealthEvent"() {
        given: "JSON for an state of health event"
        def eventJson = [
                recordId:14746,
                stateOfHealth:jsonStateOfHealth,
                eventTime: '2014-11-18T13:36:04.000+01:00',
                eventType: 'StateOfHealthEvent'
        ]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        StateOfHealthEvent stateOfHealthEvent = events[0] as StateOfHealthEvent

        then: "a corresponding StateOfHealth event is created"

        stateOfHealthEvent.stateOfHealth == stateOfHealth
        stateOfHealthEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        stateOfHealthEvent.recordNumber == 14746

        where:
        jsonStateOfHealth   | stateOfHealth
        "NORMAL"            | HealthState.NORMAL
        "COLD"              | HealthState.COLD
        "SORE_THROAT"       | HealthState.SORE_THROAT
        "INFECTION"         | HealthState.INFECTION
        "TIRED"             | HealthState.TIRED
        "STRESS"            | HealthState.STRESS
        "FEVER"             | HealthState.FEVER
        "FLU"               | HealthState.FLU
        "ALLERGY"           | HealthState.ALLERGY
        "PERIOD"            | HealthState.PERIOD
        "DIZZY"             | HealthState.DIZZY
        "FEEL_LOW"          | HealthState.FEEL_LOW
        "FEEL_HIGH"         | HealthState.FEEL_HIGH
        "UNKNOWN"           | HealthState.UNKNOWN
    }


    def "can parse an ContinuousBloodSugarMeasurement"() {
        given: "JSON for an continuous blood sugar"
        def eventJson = [recordId:12496,
                         glucoseValueInmmolPerl: jsonGlucoseValueInmmlPerl,
                         eventTime:"2013-10-21T14:35:16.000+02:00",
                         eventType:"ContinuousBloodSugarMeasurement"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        ContinuousBloodSugarMeasurement continuousBloodSugarMeasurement = events[0] as ContinuousBloodSugarMeasurement

        then: "a corresponding ContinuousBloodSugarMeasurement event is created"

        continuousBloodSugarMeasurement.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        continuousBloodSugarMeasurement.time == ISO8601DateParser.parse(eventJson.eventTime)
        continuousBloodSugarMeasurement.recordNumber == 12496

        where:
        jsonGlucoseValueInmmlPerl   | glucoseValueInmmolPerl
        "5.5"                       | 5.5
        "3.1"                       | 3.1
        "1.8"                       | 1.8
    }

    def "can parse an HypoAlarmEvent"() {
        given: "JSON for an HypoAlarmEvent"
        def eventJson = [recordId:2001,
                         glucoseValueInmmolPerl:jsonGlucoseValueInmmlPerl,
                         eventTime:"2014-11-24T14:08:53.687+01:00",
                         eventType:"HypoAlarmEvent"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        HypoAlarmEvent hypoAlarmEvent = events[0] as HypoAlarmEvent

        then: "a corresponding HypoAlarmEvent event is created"

        hypoAlarmEvent.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        hypoAlarmEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        hypoAlarmEvent.recordNumber == 2001

        where:
        jsonGlucoseValueInmmlPerl   | glucoseValueInmmolPerl
        "5.5"                       | 5.5
        "3.1"                       | 3.1
        "1.8"                       | 1.8

    }

    def "can parse an HyperAlarmEvent"() {
        given: "JSON for an HyperAlarmEvent"
        def eventJson = [
                recordId:2001,
                glucoseValueInmmolPerl:jsonGlucoseValueInmmlPerl,
                eventTime:"2014-11-24T14:08:53.688+01:00",
                eventType:"HyperAlarmEvent"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        HyperAlarmEvent hyperAlarmEvent = events[0] as HyperAlarmEvent

        then: "a corresponding HyperAlarmEvent event is created"

        hyperAlarmEvent.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        hyperAlarmEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        hyperAlarmEvent.recordNumber == 2001

        where:
        jsonGlucoseValueInmmlPerl   | glucoseValueInmmolPerl
        "5.5"                       | 5.5
        "3.1"                       | 3.1
        "1.8"                       | 1.8

    }

    def "can parse an impending hypo alarm"() {
        given: "JSON for an ImpendingHypoAlarm"
        def eventJson = [
                recordId:2001,
                glucoseValueInmmolPerl:jsonGlucoseValueInmmlPerl,
                eventTime:"2014-11-24T14:08:53.689+01:00",
                impendingNess: jsonImpendingNess,
                eventType:"ImpendingHypoAlarmEvent"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        ImpendingHypoAlarmEvent impendingHypoAlarm = events[0] as ImpendingHypoAlarmEvent

        then: "a corresponding ImpendingHypoAlarm event is created"

        impendingHypoAlarm.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        impendingHypoAlarm.impendingNess == impendingNess
        impendingHypoAlarm.time == ISO8601DateParser.parse(eventJson.eventTime)
        impendingHypoAlarm.recordNumber == 2001

        where:
        jsonGlucoseValueInmmlPerl   | jsonImpendingNess | glucoseValueInmmolPerl    | impendingNess
        "5.5"                       | "3"               | 5.5                       | "3"
        "3.1"                       | "1"               | 3.1                       | "1"
        "1.8"                       | "5"               | 1.8                       | "5"
    }

    def "can parse an impending hyper alarm"() {
        given: "JSON for an ImpendingHyperAlarm"
        def eventJson = [
                recordId:2001,
                glucoseValueInmmolPerl:jsonGlucoseValueInmmlPerl,
                eventTime:"2014-11-24T14:08:53.689+01:00",
                impendingNess: jsonImpendingNess,
                eventType:"ImpendingHyperAlarmEvent"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        ImpendingHyperAlarmEvent impendingHyperAlarm = events[0] as ImpendingHyperAlarmEvent

        then: "a corresponding ImpendingHypoAlarm event is created"

        impendingHyperAlarm.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        impendingHyperAlarm.impendingNess == impendingNess
        impendingHyperAlarm.time == ISO8601DateParser.parse(eventJson.eventTime)
        impendingHyperAlarm.recordNumber == 2001

        where:
        jsonGlucoseValueInmmlPerl   | jsonImpendingNess | glucoseValueInmmolPerl    | impendingNess
        "5.5"                       | "3"               | 5.5                       | "3"
        "3.1"                       | "1"               | 3.1                       | "1"
        "1.8"                       | "5"               | 1.8                       | "5"
    }


    def "can parse an coulometer reading"() {
        given: "JSON for an ImpendingHyperAlarm"
        def eventJson = [
                recordId:2001,
                glucoseValueInmmolPerl:jsonGlucoseValueInmmlPerl,
                eventTime:"2014-11-24T14:08:53.691+01:00",
                eventType:"CoulometerReadingEvent"]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        CoulometerReadingEvent coulometerReadingEvent = events[0] as CoulometerReadingEvent

        then: "a corresponding CoulometerReadingEvent event is created"

        coulometerReadingEvent.glucoseValueInmmolPerl == glucoseValueInmmolPerl
        coulometerReadingEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        coulometerReadingEvent.recordNumber == 2001

        where:
        jsonGlucoseValueInmmlPerl   | glucoseValueInmmolPerl
        "5.5"                       | 5.5
        "3.1"                       | 3.1
        "1.8"                       | 1.8

    }


    def "can parse a insulin event"() {
        given: "JSON for an InsulinEvent"
        def eventJson = [
                recordId:2001,
                insulinType:jsonInsulinType,
                eventTime:"2014-11-24T14:08:53.692+01:00",
                eventType:"InsulinEvent",
                units:jsonUnits]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        InsulinEvent insulinEvent = events[0] as InsulinEvent

        then: "a corresponding InsulinEvent event is created"

        insulinEvent.insulinType == insulinType
        insulinEvent.units == units
        insulinEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        insulinEvent.recordNumber == 2001

        where:
        jsonInsulinType | insulinType               | jsonUnits             | units
        "RAPID_ACTING"  | InsulinType.RAPID_ACTING  | "2000"                | "2000"
        "LONG_ACTING"   | InsulinType.LONG_ACTING   | "1000"                | "1000"
        "PRE_MIX"       | InsulinType.PRE_MIX       | "1030"                | "1030"
        "INTERMEDIATE"  | InsulinType.INTERMEDIATE  | "1100"                | "1100"
        "SHORT_ACTING"  | InsulinType.SHORT_ACTING  | "1000"                | "1000"
        "UNKNOWN"       | InsulinType.UNKNOWN       | "UNSELECTED_DEFAULT"  | "UNSELECTED_DEFAULT"
    }

    def "can parse a meal event"() {
        given: "JSON for an MealEvent"
        def eventJson = [
                recordId:2001,
                foodType: jsonFoodType,
                eventTime: "2014-11-24T14:08:53.694+01:00",
                eventType: "MealEvent",
                carboGrams: jsonCarboGrams]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        MealEvent mealEvent = events[0] as MealEvent

        then: "a corresponding jsonCarboGrams event is created"

        mealEvent.foodType == foodType
        mealEvent.carboGrams == carboGrams
        mealEvent.time == ISO8601DateParser.parse(eventJson.eventTime)
        mealEvent.recordNumber == 2001

        where:
        jsonFoodType    | foodType              | jsonCarboGrams        | carboGrams
        "BREAKFAST"     | FoodType.BREAKFAST    | "12"                  | "12"
        "LUNCH"         | FoodType.LUNCH        | "34"                  | "34"
        "DINNER"        | FoodType.DINNER       | "113"                 | "113"
        "SNACK"         | FoodType.SNACK        | "320"                 | "320"
        "UNKNOWN"       | FoodType.UNKNOWN      | "UNSELECTED_DEFAULT"  | "UNSELECTED_DEFAULT"
    }

    def "can parse an ExerciseEvent"() {
        given: "JSON for an exercise event"
        def eventJson = [
                recordId:14746,
                exerciseIntensity:jsonIntensity,
                durationInMinutes:'15',
                eventTime: '2014-11-18T13:36:04.000+01:00',
                eventType: 'ExerciseEvent',
                exerciseType:jsonExerciseType]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        ExerciseEvent exerciseEvent = events[0] as ExerciseEvent

        then: "a corresponding Exercise event is created"

        exerciseEvent.durationInMinutes == 15
        exerciseEvent.exerciseIntensity == exerciseIntensity
        exerciseEvent.exerciseType == exerciseType
        exerciseEvent.time == ISO8601DateParser.parse(eventJson.eventTime)

        where:
        jsonExerciseType        |jsonIntensity   | exerciseType             | exerciseIntensity
        "AEROBICS"              | "LOW"          | ExerciseType.AEROBICS    | ExerciseIntensity.LOW
        "AEROBICS"              | "HIGH"         | ExerciseType.AEROBICS    | ExerciseIntensity.HIGH
        "AEROBICS"              | "MEDIUM"       | ExerciseType.AEROBICS    | ExerciseIntensity.MEDIUM
        "AEROBICS"              | "NONE"         | ExerciseType.AEROBICS    | ExerciseIntensity.NONE
        "AEROBICS"              | "UNKNOWN"      | ExerciseType.AEROBICS    | ExerciseIntensity.UNKNOWN
        "WALKING"               | "LOW"          | ExerciseType.WALKING     | ExerciseIntensity.LOW
        "JOGGING"               | "LOW"          | ExerciseType.JOGGING     | ExerciseIntensity.LOW
        "RUNNING"               | "LOW"          | ExerciseType.RUNNING     | ExerciseIntensity.LOW
        "SWIMMING"              | "LOW"          | ExerciseType.SWIMMING    | ExerciseIntensity.LOW
        "BIKING"                | "LOW"          | ExerciseType.BIKING      | ExerciseIntensity.LOW
        "WEIGHTS"               | "LOW"          | ExerciseType.WEIGHTS     | ExerciseIntensity.LOW
        "OTHER"                 | "LOW"          | ExerciseType.OTHER       | ExerciseIntensity.LOW
        "UNKNOWN"               | "LOW"          | ExerciseType.UNKNOWN     | ExerciseIntensity.LOW
    }

    def "can parse an GenericEvent"() {
        given: "JSON for an generic event"
        def eventJson = [
                recordId:14746,
                indicatedEvent:jsonIndicatedEvent,
                eventTime: '2014-11-18T13:36:04.000+01:00',
                eventType: 'GenericEvent'
                ]

        when: "the json is parsed"
        def events = service.parseEvents([eventJson])
        GenericEvent genericEvent = events[0] as GenericEvent

        then: "a corresponding Generic event is created"
        genericEvent.indicatedEvent == indicatedEvent
        genericEvent.time == ISO8601DateParser.parse(eventJson.eventTime)

        where:
        jsonIndicatedEvent  | indicatedEvent
        "1"                 | "1"
        "2"                 | "2"
        "3"                 | "3"
        "4"                 | "4"
        "5"                 | "5"
        "6"                 | "6"
        "7"                 | "7"
        "8"                 | "8"
        "UNSELECTED_DEFAULT"| "UNSELECTED_DEFAULT"

    }
}
