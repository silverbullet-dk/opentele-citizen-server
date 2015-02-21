package org.opentele.server.citizen.api

import grails.plugin.springsecurity.annotation.Secured
import grails.util.Environment
import org.opentele.server.citizen.model.SemanticVersion

class RootController {
    static allowedMethods = [show: "GET"]

    @Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
    def show() {
        def apiVersion = new SemanticVersion(grailsApplication.metadata['app.apiVersion'])

        render(contentType: 'application/json') {[
                'apiVersion': apiVersion.version,
                'serverEnvironment': Environment.getCurrent()?.getName(),
                'links': [
                        'self': createLink(mapping: 'root', action: 'show', absolute: true),
                        'patient': createLink(controller: 'patient', action: 'show', absolute: true),
                        'api-doc': createLink(uri: '/patient-api.html', absolute: true)
                ]
        ]}
    }
}
