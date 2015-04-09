package org.opentele.server.citizen.model

import grails.plugin.springsecurity.annotation.Secured
import grails.validation.ValidationException
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.model.Patient
import org.opentele.server.model.RealTimeCtg

@Secured(PermissionName.NONE)
class RealTimeCTGController {

    def springSecurityService
    def realTimeCTGService
    def grailsApplication

    static allowedMethods = [save: "POST"]

    @Secured(PermissionName.REALTIME_CTG_SAVE)
    def save() {

        def patient = Patient.findByUser(springSecurityService.currentUser)

        def maxSamplesPerPatient = grailsApplication.config.milou.realtimectg.maxPerPatient
        def noOfSamples = RealTimeCtg.countByPatient(patient)
        if (noOfSamples > maxSamplesPerPatient) {
            deleteSamples(patient)
            render ''
            return
        }

        saveSamples(patient, params)
        render ''
    }

    private def deleteSamples(patient) {
        realTimeCTGService.deleteFor(patient)
        response.sendError(429)
    }

    private def saveSamples(patient, params) {

        params.patient = patient
        try {
            realTimeCTGService.save(params)
            response.setStatus(201)
        } catch (ValidationException ex) {
            response.sendError(400)
            return;
        }
    }
}
