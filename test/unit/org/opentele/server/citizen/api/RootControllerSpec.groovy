package org.opentele.server.citizen.api

import grails.test.mixin.TestFor
import org.codehaus.groovy.grails.web.json.JSONObject
import spock.lang.Specification

@TestFor(RootController)
class RootControllerSpec extends Specification {

    void "can determine citizen API version"() {
        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.apiVersion != null
        body.apiVersion != ""
        body.apiVersion != "0.0.0"
    }

    void "can get links to available resources"() {
        when:
        controller.show()

        then:
        def body = new JSONObject(response.text)
        body.links.patient.toURI() != null
        body.links.self.toURI() != null
    }
}
