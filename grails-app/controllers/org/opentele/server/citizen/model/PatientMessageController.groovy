package org.opentele.server.citizen.model

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.model.*
import org.opentele.server.core.model.types.PermissionName

import static javax.servlet.http.HttpServletResponse.*

@Secured(PermissionName.NONE)
class PatientMessageController {

    def springSecurityService
    def messageService
    def citizenMessageService

    static allowedMethods = [save: "POST", update: "POST", delete: ["POST": "DELETE"], markAsRead: "POST"]

    /**
     * The following methods are used by the client:
     * list()
     * - createMessagesListResult(..)
     * -- createMessageResult(..)
     * save()
     * show()
     * getClinicians()
     * - createJsonForDepartments(..)
     */

    //Action used when a patient is logged in
    @Secured([PermissionName.MESSAGE_READ, PermissionName.MESSAGE_READ_JSON])
    def list() {
        if (springSecurityService.authentication.isAuthenticated()) {
            def user = springSecurityService.currentUser
            def patient = Patient.findByUser(user)
            def messages = Message.findAllByPatient(patient)

            render createMessagesListResult(messages) as JSON
        } else {
            withFormat {
                json {
                    response.status = 403 // Forbidden
                    def results = []
                    results << "error"
                    results << message(code: 'user.not.authenticated', default: "User not authenticated")
                    render  results as JSON
                }
                html {
                }
            }
        }
    }

    @Secured([PermissionName.MESSAGE_WRITE,PermissionName.MESSAGE_WRITE_JSON ])
    def markAsRead() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)
        def ids = request.JSON.collect { it as long }

        if (!ids.empty) {
            def messages = Message.findAllByPatientAndIdInList(patient, ids)
            messages.each {
                messageService.setRead(it);
            }
        }

        render ""
    }

    /**
     * Retrieves a list of recipients that a patient can send messages to
     */
    @Secured([PermissionName.MESSAGE_READ_JSON])
    def messageRecipients() {
        def user = springSecurityService.currentUser

        if (!user) {
            user = User.get(params.user)
        }

        def patient = Patient.findByUser(user)

        def departments = citizenMessageService.possibleRecipientsFor(patient)

        render departments.collect {
            createJsonForDepartments(it)
        } as JSON
    }

    private createJsonForDepartments(Department department) {
        [
            "id": department.id,
            "name": department.name
        ]
    }

    @Secured([PermissionName.MESSAGE_WRITE,PermissionName.MESSAGE_WRITE_JSON ])
    def save() {
        def user = springSecurityService.currentUser

        params.sentByPatient = true
        params.patient = Patient.findByUser(user)
        params.department = Department.get(params.department)
        params.inReplyTo = Message.get(params.inReplyTo)

        def messageInstance = messageService.saveMessage(params)

        if (!messageInstance.hasErrors()) {
            withFormat {
                json {
                    response.status = SC_OK
                    render createMessageResult(messageInstance) as JSON
                }
            }
        } else {
            withFormat {
                json {
                    response.status = SC_BAD_REQUEST
                    render messageInstance as JSON
                }
            }
        }
    }

    private createMessagesListResult(Collection<Message> messages) {
        def items = messages.collect { createMessageResult(it) }
        def unread = messages.count { !it.sentByPatient && !it.isRead }

        [
            unread: unread,
            messages: items
        ]
    }

    private createMessageResult(Message message) {
        def patient = [type: 'Patient', id: message.patient.id, name: message.patient.name]
        def department = [type: 'Department', id: message.department.id, name: message.department.name]

        def from = message.sentByPatient ? patient : department
        def to = message.sentByPatient ? department : patient

        [
            id: message.id,
            title: message.title,
            text: message.text,
            to: to,
            from: from,
            isRead: message.isRead,
            sendDate: message.sendDate,
            readDate: message.readDate
        ]
    }
}
