package org.opentele.server.citizen.api

import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.types.MeasurementFilterType
import org.opentele.server.core.model.types.MeasurementTypeName
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.core.util.TimeFilter
import org.opentele.server.model.Measurement
import org.opentele.server.model.Patient
import org.opentele.server.model.cgm.ContinuousBloodSugarMeasurement
import org.opentele.server.model.cgm.CoulometerReadingEvent
import org.opentele.server.model.cgm.ExerciseEvent
import org.opentele.server.model.cgm.GenericEvent
import org.opentele.server.model.cgm.InsulinEvent
import org.opentele.server.model.cgm.MealEvent
import org.opentele.server.model.cgm.StateOfHealthEvent
import sun.reflect.generics.reflectiveObjects.NotImplementedException

@Secured(PermissionName.NONE)
class MeasurementsController {
    static allowedMethods = [show: "GET", list: "GET"]

    def springSecurityService
    def citizenMeasurementService

    @Secured(PermissionName.PATIENT_LOGIN)
    def list() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def availableMeasurements = citizenMeasurementService.dataForTables(patient, TimeFilter.all())

        render(contentType: 'application/json') {
            def measurements = []

            availableMeasurements.each() {
                def type = it.type.toString().toLowerCase()
                measurements << ['name': type, 'links': [measurement: createLink(mapping: 'measurement', params: [id: type], absolute: true)]]
            }

            return [
                    'measurements': measurements,
                    'links': [self: createLink(mapping: 'measurements', absolute: true)]
            ]
        }
    }

    @Secured(PermissionName.PATIENT_LOGIN)
    def show(String id, String filter) {
        def measurementType = MeasurementTypeName.safeValueOf(id.toUpperCase())
        def filterType = MeasurementFilterType.safeValueOf(filter ? filter.toUpperCase() : 'WEEK')

        def errors = validateInput(measurementType, filterType)
        if (errors.size() > 0) {
            render(status: 422, contentType: 'application/json') {
                return ['message': 'Validation failed', 'errors': errors]
            }
            return
        }

        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def timeFilter = TimeFilter.fromFilterType(filterType)
        def measurements = citizenMeasurementService.patientMeasurementsOfType(patient, timeFilter, measurementType)

        render(contentType: 'application/json') {
            def unit = measurements ? measurements[0].unit.value() : null
            def body = [
                    'type': measurementType.value().toLowerCase(),
                    'unit': unit,
                    'measurements': measurements.collect { toClientValues(it, timeFilter) }.flatten(),
                    'links':  [self: createLink(mapping: 'measurement', params: [id: id], absolute: true)]
            ]

            return body
        }
    }

    private def validateInput(MeasurementTypeName measurementType, MeasurementFilterType filterType) {
        def errors = []
        if (measurementType == null) {
            errors << ['resource': 'measurements', 'field': 'id', 'code': 'invalid']
        }

        if (filterType == null || filterType == MeasurementFilterType.CUSTOM) {
            errors << ['resource': 'measurements', 'field': 'filter', 'code': 'invalid']
        }

        return errors
    }

    private def toClientValues(Measurement measurement, TimeFilter timeFilter) {
        switch (measurement.measurementType.name) {
            case MeasurementTypeName.BLOOD_PRESSURE:
                return ['timestamp': measurement.time, 'measurement': [
                        'systolic': measurement.systolic,
                        'diastolic': measurement.diastolic
                ]]
            case MeasurementTypeName.BLOODSUGAR:
                return ['timestamp': measurement.time, 'measurement': [
                        'value': measurement.value,
                        'isAfterMeal': measurement.isAfterMeal,
                        'isBeforeMeal': measurement.isBeforeMeal,
                        'isControlMeasurement': measurement.isControlMeasurement,
                        'isOutOfBounds': measurement.isOutOfBounds,
                        'otherInformation': measurement.otherInformation,
                        'hasTemperatureWarning': measurement.hasTemperatureWarning
                ]]
            case MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT:
                return toCgmValues(measurement, timeFilter)
            case MeasurementTypeName.URINE:
                return ['timestamp': measurement.time, 'measurement': measurement.protein.value()]
            case MeasurementTypeName.URINE_GLUCOSE:
                return ['timestamp': measurement.time, 'measurement': measurement.glucoseInUrine.value()]
            default:
                return ['timestamp': measurement.time, 'measurement': measurement.value]
        }
    }

    private def toCgmValues(Measurement measurement, TimeFilter timeFilter) {
        def events = []
        measurement.continuousBloodSugarEvents.each { cgmEvent ->
            if(timeFilter.isLimited && (cgmEvent.time.before(timeFilter.start)|| cgmEvent.time.after(timeFilter.end))) {
                return  // The CGM event falls outside the time filter period even though the measurement is within
            }

            def mapped = ['timestamp': cgmEvent.time, 'measurement': [:]]
            switch (cgmEvent) {
                case ContinuousBloodSugarMeasurement:
                    mapped.measurement = ['value': cgmEvent.glucoseValueInmmolPerl, 'type': 'continuous_blood_sugar_measurement']
                    events << mapped
                    break
                case CoulometerReadingEvent:
                    mapped.measurement = ['value': cgmEvent.glucoseValueInmmolPerl, 'type': 'coulometer_reading']
                    events << mapped
                    break
                case InsulinEvent:
                    mapped.measurement = ['value': 15, 'type': 'insulin', 'insulinType': cgmEvent.insulinType.toString(), 'units': cgmEvent.units]
                    events << mapped
                    break
                case ExerciseEvent:
                    mapped.measurement = ['value': 16, 'type': 'exercise', 'exerciseType': cgmEvent.exerciseType.toString(), 'durationInMinutes': cgmEvent.durationInMinutes, 'exerciseIntensity': cgmEvent.exerciseIntensity.toString()]
                    events << mapped
                    break
                case MealEvent:
                    mapped.measurement = ['value': 17, 'type': 'meal', 'carboGrams': cgmEvent.carboGrams, 'foodType': cgmEvent.foodType.toString()]
                    events << mapped
                    break
                case StateOfHealthEvent:
                    mapped.measurement = ['value': 18, 'type': 'state_of_health', 'stateOfHealth': cgmEvent.stateOfHealth.toString()]
                    events << mapped
                    break
                case GenericEvent:
                    mapped.measurement = ['value': 19, 'type': 'generic', 'indicatedEvent': cgmEvent.indicatedEvent]
                    events << mapped
                    break
                default:
                    log.debug("Unknown ContinuousBloodSugarEvent type: ${cgmEvent}")
            }
        }

        return events
    }
}
