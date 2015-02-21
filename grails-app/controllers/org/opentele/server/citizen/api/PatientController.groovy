package org.opentele.server.citizen.api

import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.model.Patient

@Secured(PermissionName.NONE)
class PatientController {
    static allowedMethods = [show: "GET"]

    def springSecurityService
    def citizenMessageService

    @Secured(PermissionName.PATIENT_LOGIN)
    def show() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def body = [
                'firstName': patient.firstName,
                'lastName': patient.lastName,
                'passwordExpired': patient.user.cleartextPassword ? true : false,
                'links': [
                        'self': createLink(mapping: 'patient', absolute: true),
                        'password': createLink(mapping: 'password', absolute: true),
                        'measurements': createLink(mapping: 'measurements', absolute: true),
                        'questionnaires': createLink(mapping: 'questionnaires', absolute: true),
                        'reminders':  createLink(mapping: 'reminders', absolute: true)
                ]
        ]

        if (citizenMessageService.isMessagesAvailableTo(patient)) {
            body.links['messageThreads'] = createLink(mapping: 'messageThreads', absolute: true)
            body.links['unreadMessages'] = createLink(mapping: 'messages', absolute: true)
            body.links['acknowledgements'] = createLink(mapping: 'acknowledgements', absolute: true)
        }

        render(contentType: 'application/json') {return body}
    }
}
