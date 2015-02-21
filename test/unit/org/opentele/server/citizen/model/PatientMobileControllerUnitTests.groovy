package org.opentele.server.citizen.model

import grails.buildtestdata.mixin.Build
import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.domain.DomainClassUnitTestMixin
import org.codehaus.groovy.grails.web.json.JSONObject
import org.junit.Test
import org.opentele.builders.PatientBuilder
import org.opentele.server.citizen.RealTimeCTGService
import org.opentele.server.model.*

@SuppressWarnings("GroovyAccessibility")
@TestFor(PatientMobileController)
@Build([Patient, User, Role, UserRole])
@TestMixin(DomainClassUnitTestMixin)
class PatientMobileControllerUnitTests {

    @Test
    void testLoginDoesNotReturnSensitiveData() {
        def mockSpringSecurityService = mockFor(SpringSecurityService)
        mockSpringSecurityService.metaClass.encodePassword = {p ->
            return p
        }

        def user = User.build(password: 'abcd1234', springSecurityService: mockSpringSecurityService)
        Patient patient = new PatientBuilder().forUser(user).build()

        mockSpringSecurityService.metaClass.getCurrentUser = { ->
            patient.getUser()
        }
        controller.springSecurityService = mockSpringSecurityService

        def mockRealTimeCTGService = mockFor(RealTimeCTGService)
        mockRealTimeCTGService.demand.patientCanDoRealtimeCTGs { Patient p ->
            false
        }
        controller.realTimeCTGService = mockRealTimeCTGService.createMock()

        request.addHeader("Client-version", "1000.0.0")

        controller.login()
        def responseJson = new JSONObject(response.text)

        assert responseJson.keys().size() == 5
        assert responseJson.firstName
        assert responseJson.lastName
        assert responseJson.id
        assert responseJson.showRealtimeCTG == false

        assert responseJson.user.keys().size() == 2
        assert responseJson.user.id
        assert responseJson.user.containsKey('changePassword')
    }
}
