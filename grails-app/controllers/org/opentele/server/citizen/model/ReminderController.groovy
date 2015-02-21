package org.opentele.server.citizen.model

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.model.Patient

@Secured(PermissionName.NONE)
class ReminderController {

    def springSecurityService
    def reminderService

    @Secured(PermissionName.PATIENT_LOGIN)
    def next() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)
        def now = Calendar.getInstance()

        // Get the next reminder time from the questionnaire service.
        def nextReminders = reminderService.getNextReminders(patient, now)
        render nextReminders as JSON
    }
}
