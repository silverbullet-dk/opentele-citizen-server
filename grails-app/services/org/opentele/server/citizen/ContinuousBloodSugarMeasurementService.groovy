package org.opentele.server.citizen

import org.opentele.server.core.util.ISO8601DateParser
import org.opentele.server.model.*
import org.opentele.server.model.cgm.ContinuousBloodSugarEvent
import org.opentele.server.model.cgm.ContinuousBloodSugarMeasurement
import org.opentele.server.model.cgm.CoulometerReadingEvent
import org.opentele.server.model.cgm.ExerciseEvent
import org.opentele.server.model.cgm.HyperAlarmEvent
import org.opentele.server.model.cgm.HypoAlarmEvent
import org.opentele.server.model.cgm.ImpendingHyperAlarmEvent
import org.opentele.server.model.cgm.ImpendingHypoAlarmEvent
import org.opentele.server.model.cgm.InsulinEvent
import org.opentele.server.model.cgm.MealEvent
import org.opentele.server.model.cgm.StateOfHealthEvent
import org.opentele.server.model.cgm.GenericEvent

class ContinuousBloodSugarMeasurementService {

    def lastContinuousBloodSugarRecordNumberForPatient(Patient patient) {
        return ContinuousBloodSugarEvent.executeQuery("select MAX(continuousBloodSugarMeasurement.recordNumber) from ContinuousBloodSugarMeasurement continuousBloodSugarMeasurement where continuousBloodSugarMeasurement.measurement.patient = ?", patient);
    }

    ContinuousBloodSugarEvent[] parseEvents(def rawEvents) {
        def parsedEvents =  []
        rawEvents.each {
            parsedEvents << parseEvent(it)
        }

        parsedEvents
    }

    ContinuousBloodSugarEvent parseEvent(def rawEvent) {
        switch (rawEvent.eventType) {
            case "ExerciseEvent":
                return parseExerciseEvent(rawEvent)
            case "StateOfHealthEvent":
                return parseStateOfHealthEvent(rawEvent)
            case "ContinuousBloodSugarMeasurement":
                return parseContinuousBloodSugarMeasurement(rawEvent)
            case "HypoAlarmEvent":
                return  parseHypoAlarmEvent(rawEvent)
            case "HyperAlarmEvent":
                return parseHyperAlarmEvent(rawEvent)
            case "ImpendingHypoAlarmEvent":
                return parseImpendingHypoAlarmEvent(rawEvent)
            case "ImpendingHyperAlarmEvent":
                return parseImpendingHyperAlarmEvent(rawEvent)
            case "CoulometerReadingEvent":
                return parseCoulometerReadingEvent(rawEvent)
            case "MealEvent":
                return parseMealEvent(rawEvent)
            case "InsulinEvent":
                return parseInsulinEvent(rawEvent)
            case "GenericEvent":
                return parseGenericEvent(rawEvent)
            default:
                log.error("Unknown ContinuousBloodSugarEvent type: ${rawEvent.eventType}")
                break
        }
    }

    ContinuousBloodSugarEvent parseGenericEvent(def rawEvent) {
        GenericEvent genericEvent = new GenericEvent()
        parseCommonFields(genericEvent, rawEvent)

        genericEvent.indicatedEvent = rawEvent.indicatedEvent

        genericEvent
    }

    def parseInsulinEvent(def rawEvent) {
        InsulinEvent insulinEvent = new InsulinEvent()
        parseCommonFields(insulinEvent, rawEvent)

        insulinEvent.insulinType = org.opentele.server.model.types.cgm.InsulinType.valueOf(rawEvent.insulinType)
        insulinEvent.units = rawEvent.units

        insulinEvent
    }

    def parseMealEvent(def rawEvent) {
        MealEvent mealEvent = new MealEvent()
        parseCommonFields(mealEvent, rawEvent)

        mealEvent.carboGrams = rawEvent.carboGrams
        mealEvent.foodType = org.opentele.server.model.types.cgm.FoodType.valueOf(rawEvent.foodType)

        mealEvent
    }

    def parseCoulometerReadingEvent(def rawEvent) {
        CoulometerReadingEvent coulometerReadingEvent = new CoulometerReadingEvent()
        parseCommonFields(coulometerReadingEvent, rawEvent)

        coulometerReadingEvent.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)

        coulometerReadingEvent
    }

    def parseImpendingHyperAlarmEvent(def rawEvent) {
        ImpendingHyperAlarmEvent impendingHyperAlarmEvent = new ImpendingHyperAlarmEvent()
        parseCommonFields(impendingHyperAlarmEvent, rawEvent)

        impendingHyperAlarmEvent.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)
        impendingHyperAlarmEvent.impendingNess = rawEvent.impendingNess

        impendingHyperAlarmEvent
    }

    def parseImpendingHypoAlarmEvent(def rawEvent) {
        ImpendingHypoAlarmEvent impendingHypoAlarmEvent = new ImpendingHypoAlarmEvent()
        parseCommonFields(impendingHypoAlarmEvent, rawEvent)

        impendingHypoAlarmEvent.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)
        impendingHypoAlarmEvent.impendingNess = rawEvent.impendingNess

        impendingHypoAlarmEvent
    }

    def parseHyperAlarmEvent(def rawEvent) {
        HyperAlarmEvent hyperAlarmEvent = new HyperAlarmEvent()
        parseCommonFields(hyperAlarmEvent, rawEvent)

        hyperAlarmEvent.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)

        hyperAlarmEvent
    }

    def parseHypoAlarmEvent(def rawEvent) {
        HypoAlarmEvent hypoAlarmEvent = new HypoAlarmEvent()
        parseCommonFields(hypoAlarmEvent, rawEvent)

        hypoAlarmEvent.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)

        hypoAlarmEvent
    }

    def parseStateOfHealthEvent(def rawEvent) {
        StateOfHealthEvent stateOfHealthEvent = new StateOfHealthEvent()
        parseCommonFields(stateOfHealthEvent, rawEvent)

        stateOfHealthEvent.stateOfHealth = org.opentele.server.model.types.cgm.HealthState.valueOf(rawEvent.stateOfHealth)

        stateOfHealthEvent
    }

    def parseExerciseEvent(def rawEvent) {
        ExerciseEvent exerciseEvent = new ExerciseEvent()

        parseCommonFields(exerciseEvent, rawEvent)

        exerciseEvent.durationInMinutes = Integer.parseInt(rawEvent.durationInMinutes)
        exerciseEvent.exerciseIntensity = org.opentele.server.model.types.cgm.ExerciseIntensity.valueOf(rawEvent.exerciseIntensity)
        exerciseEvent.exerciseType = org.opentele.server.model.types.cgm.ExerciseType.valueOf(rawEvent.exerciseType)

        exerciseEvent
    }

    def parseContinuousBloodSugarMeasurement(def rawEvent) {
        ContinuousBloodSugarMeasurement continuousBloodSugarMeasurement = new ContinuousBloodSugarMeasurement();

        parseCommonFields(continuousBloodSugarMeasurement, rawEvent)
        continuousBloodSugarMeasurement.glucoseValueInmmolPerl = Double.parseDouble(rawEvent.glucoseValueInmmolPerl)

        continuousBloodSugarMeasurement
    }

    def parseCommonFields(ContinuousBloodSugarEvent event, def rawEvent) {
        event.recordNumber = rawEvent.recordId
        event.time = ISO8601DateParser.parse(rawEvent.eventTime)
    }
}
