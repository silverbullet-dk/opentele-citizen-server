package org.opentele.server.citizen.api

import grails.buildtestdata.mixin.Build
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.TestFor
import org.codehaus.groovy.grails.web.json.JSONObject
import org.opentele.builders.PatientBuilder
import org.opentele.server.citizen.CitizenMessageService
import org.opentele.server.model.Patient
import org.opentele.server.model.Role
import org.opentele.server.model.User
import org.opentele.server.model.UserRole
import spock.lang.Specification

@TestFor(PatientController)
@Build([Patient, User, Role, UserRole])
class PatientControllerSpec extends Specification{
    Patient patient
    def mockSpringSecurityService
    def mockCitizenMessageService

    def setup() {
        patient = new PatientBuilder().build()
        patient.user = new User()

        mockSpringSecurityService = mockFor(SpringSecurityService)
        mockSpringSecurityService.metaClass.getCurrentUser = { ->
            patient.getUser()
        }
        controller.springSecurityService = mockSpringSecurityService

        mockCitizenMessageService = mockFor(CitizenMessageService)
        mockCitizenMessageService.metaClass.isMessagesAvailableTo = {p ->
            return true;
        }
        controller.citizenMessageService = mockCitizenMessageService
    }

    void "can get patient"() {
        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.firstName == patient.firstName
        body.lastName == patient.lastName
        body.passwordExpired != null
    }

    void "patient password expired is set false when user has no temp password"() {
        setup:
        patient.user.cleartextPassword = null

        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.passwordExpired == false
    }

    void "patient password expired is set true when user has temp password"() {
        setup:
        patient.user.cleartextPassword = 'foo'

        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.passwordExpired == true
    }

    void "can get links to available resources for patient"() {
        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.links.self.toURI() != null
        body.links.measurements.toURI() != null
        body.links.password.toURI() != null
        body.links.reminders.toURI() != null
        body.links.questionnaires.toURI() != null
        body.links.messageThreads.toURI() != null
        body.links.unreadMessages.toURI() != null
        body.links.acknowledgements.toURI() != null
    }


    void "link to message related resources not present when patient has messages disabled"() {
        setup:
        mockCitizenMessageService.metaClass.isMessagesAvailableTo = {p ->
            return false;
        }

        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.links.messageThreads == null
        body.links.unreadMessages == null
        body.links.acknowledgements == null
        // the others should still be there...
        body.links.measurements.toURI() != null
        body.links.password.toURI() != null
        body.links.reminders.toURI() != null
        body.links.questionnaires.toURI() != null
    }
}
