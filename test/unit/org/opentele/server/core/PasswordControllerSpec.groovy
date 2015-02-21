package org.opentele.server.core

import grails.test.mixin.TestFor
import org.codehaus.groovy.grails.web.json.JSONObject
import org.opentele.server.model.User
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
@TestFor(PasswordController)
class PasswordControllerSpec extends Specification {

    def setup() {
        controller.passwordService = Mock(PasswordService)
    }

    def "when opening change the model should return an empty command object" () {
        when:
        def model = controller.change()

        then:
        model.command
        !model.command.password
        !model.command.currentPassword
        !model.command.passwordRepeat
    }

    def "when posting a password change with no errors ok is returned"() {
        setup:
        populateParams()
        controller.passwordService.changePassword(_) >> {PasswordCommand cmd -> cmd.user = new User(username: 'blabla')}
        request.method = 'POST'

        when:
        controller.update()

        then:
        response.status == 200
        def body = new JSONObject(response.text)
        body.status == 'ok'
    }

    def "when posting a password change with errors, an error status is returned"() {
        setup:
        populateParams()
        params.password = null
        controller.passwordService.changePassword(_) >> {PasswordCommand cmd -> cmd.validate()}
        request.method = 'POST'

        when:
        controller.update()

        then:
        response.status == 200
        def body = new JSONObject(response.text)
        body.status == 'error'
    }

    def populateParams() {
        params.currentPassword = 'abcd1234'
        params.password = '1234abcd'
        params.passwordRepeat = '1234abcd'
    }

}
