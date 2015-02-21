package org.opentele.server.citizen.model

import grails.test.mixin.TestMixin
import grails.test.mixin.integration.IntegrationTestMixin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.opentele.server.core.test.AbstractControllerIntegrationTest
import org.opentele.server.core.util.JSONMarshallerUtil
import org.opentele.server.model.*

@TestMixin(IntegrationTestMixin)
class PatientMessageControllerTests extends AbstractControllerIntegrationTest {

	def controller
    def grailsApplication
    
    @Before
	void setUp() {
        // Avoid conflicts with objects in session created earlier. E.g. in bootstrap
        grailsApplication.mainContext.sessionFactory.currentSession.clear()

		JSONMarshallerUtil.registerCustomJSONMarshallers(grailsApplication)
		
		controller = new PatientMessageController()
		controller.response.format = "json"
	}

	@Test
	void testList() {
		Department d = Department.build()
        d.save(failOnError: true)
		
        User u = User.build(username:"toUser", password: "toUser12", enabled:true)
		u.save(failOnError: true)

        Patient p = Patient.build(user: u, cpr: "0102800102", thresholds: new ArrayList<Threshold>())
        p.save(failOnError: true)

        Message messageFromClinician = Message.build(department: d,patient: p)
		messageFromClinician.save(failOnError: true)
		Message messageFromPatient = Message.build(department: d,patient: p, sentByPatient: true)
		messageFromPatient.save(failOnError: true)

        authenticate 'toUser','toUser12'
		controller.list()

		def JSONresponse = controller.response.json

		assert JSONresponse.messages[0].to.id == p.id
		assert JSONresponse.messages[0].from.id == d.id

		//Should now return all messages
		assert JSONresponse.messages.length() == 2
	}

    @Test
    void testSendMessage() {
        def login = "Erna"

        authenticate login, 'abcd1234'

        def p = Patient.findByFirstName(login)

        controller.params.department = p.id
        controller.params.title = "Test message"
        controller.params.text = "This is a message"
        controller.request.method = 'POST'

        controller.save()

        println "Response: " + controller.response.status
        assert controller.response.status == 200
    }
}
