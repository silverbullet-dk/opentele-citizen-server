package org.opentele.server.citizen.model

import dk.silverbullet.kih.api.auditlog.SkipAuditLog
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.ConferenceMeasurementDraftType
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.model.*

import javax.servlet.AsyncContext

class PatientConferenceMobileController {

    def springSecurityService
    def conferenceStateService

    // Patient
    @Secured(PermissionName.JOIN_VIDEO_CALL)
    @SkipAuditLog
    def patientHasPendingConference() {
        def patient = currentPatient()

        request.setAttribute('org.apache.catalina.ASYNC_SUPPORTED', true)
        AsyncContext context = request.startAsync()
        context.timeout = getAsyncTimeoutMillis();
        conferenceStateService.add(new Date(), patient.id, context)
    }


    // Patient
    @Secured(PermissionName.JOIN_VIDEO_CALL)
    @SkipAuditLog
    def patientHasPendingMeasurement() {
        def patient = currentPatient()

        def waitingConferenceMeasurementDraft = waitingConferenceMeasurementDraft(patient,
                ConferenceMeasurementDraftType.BLOOD_PRESSURE,
                ConferenceMeasurementDraftType.LUNG_FUNCTION,
                ConferenceMeasurementDraftType.SATURATION)
        if (waitingConferenceMeasurementDraft != null) {
            def reply = [type: waitingConferenceMeasurementDraft.type.name()]
            render reply as JSON
        } else {
            render ''
        }
    }


    // Patient
    @Secured(PermissionName.JOIN_VIDEO_CALL)
    def measurementFromPatient() {
        def patient = currentPatient()
        def measurementDetails = request.JSON
        def measurementType = ConferenceMeasurementDraftType.find { it.name() == measurementDetails.type }
        if (measurementType == null) {
            throw new IllegalArgumentException("Unknown measurement type: '${measurementDetails.type}'")
        }

        def waitingConferenceMeasurementDraft = waitingConferenceMeasurementDraft(patient, measurementType)
        if (waitingConferenceMeasurementDraft == null) {
            // Don't update anything, and don't throw any exceptions
            render ''
            return
        }

        switch (measurementType) {
            case ConferenceMeasurementDraftType.LUNG_FUNCTION:
                fillOutLungFunctionMeasurement(waitingConferenceMeasurementDraft, measurementDetails.measurement)
                break;
            case ConferenceMeasurementDraftType.BLOOD_PRESSURE:
                fillOutBloodPressureMeasurement(waitingConferenceMeasurementDraft, measurementDetails.measurement)
                break;
            case ConferenceMeasurementDraftType.SATURATION:
                fillOutSaturationMeasurement(waitingConferenceMeasurementDraft, measurementDetails.measurement)
                break;
            default:
                throw new IllegalArgumentException("Unsupported automatic measurement type: '${measurementType}'")
        }

        waitingConferenceMeasurementDraft.deviceId = measurementDetails.deviceId
        waitingConferenceMeasurementDraft.waiting = false
        render ''
    }

    private ConferenceMeasurementDraft waitingConferenceMeasurementDraft(Patient patient, ConferenceMeasurementDraftType... types) {
        def allUnfinishedConferences = Conference.findAllByPatientAndCompleted(patient, false, [sort: 'id'])
        def conferenceWithWaitingMeasurementDraft = allUnfinishedConferences.find {
            it.measurementDrafts.any { it.automatic && it.waiting && it.type in types }
        }
        if (conferenceWithWaitingMeasurementDraft == null) {
            return null
        }
        def sortedDrafts = conferenceWithWaitingMeasurementDraft.measurementDrafts.sort { it.id }
        sortedDrafts.find { it.automatic && it.waiting && it.type in types }
    }

    private fillOutLungFunctionMeasurement(ConferenceLungFunctionMeasurementDraft draft, submittedMeasurement) {
        draft.fev1 = submittedMeasurement.fev1
        draft.fev6 = submittedMeasurement.fev6
        draft.fev1Fev6Ratio = submittedMeasurement.fev1Fev6Ratio
        draft.fef2575 = submittedMeasurement.fef2575
        draft.goodTest = submittedMeasurement.goodTest
        draft.softwareVersion = submittedMeasurement.softwareVersion
    }

    private fillOutBloodPressureMeasurement(ConferenceBloodPressureMeasurementDraft draft, submittedMeasurement) {
        draft.systolic = submittedMeasurement.systolic
        draft.diastolic = submittedMeasurement.diastolic
        draft.pulse = submittedMeasurement.pulse
        draft.meanArterialPressure = submittedMeasurement.meanArterialPressure
    }

    private fillOutSaturationMeasurement(ConferenceSaturationMeasurementDraft draft, submittedMeasurement) {
        draft.saturation = submittedMeasurement.saturation
        draft.pulse = submittedMeasurement.pulse
    }

    private currentPatient() {
        Patient.findByUser(springSecurityService.currentUser)
    }

    private long getAsyncTimeoutMillis() {
        grailsApplication.config.video.connection.asyncTimeoutMillis
    }
}
