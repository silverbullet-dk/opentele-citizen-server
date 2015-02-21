package org.opentele.server.citizen.model

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.util.Environment
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.model.Patient

@Secured(PermissionName.NONE)
class PatientMobileController {

    def springSecurityService
    def realTimeCTGService

	static allowedMethods = [login: "GET"]

	@Secured(PermissionName.PATIENT_LOGIN)
	def login() {
        def user = springSecurityService.currentUser
		def patient = Patient.findByUser(user)

        def requiredClientVersion = grailsApplication.metadata['app.requiredClientVersion']
        def actualClientVersion = request.getHeader("Client-version") as String
        def minimumClientVersion = new SemanticVersion(requiredClientVersion)
        def clientVersion = new SemanticVersion(actualClientVersion)

        //Check client version
        def versionChecksOut = minimumClientVersion > new SemanticVersion("0.0.0") && minimumClientVersion <= clientVersion

        if (actualClientVersion == '${version}' || Environment.getCurrent().getName().equals(Environment.DEVELOPMENT.getName())) {
            versionChecksOut = true //version was not set in android app. Most likely because client wasn't built by Maven
        }

        if (versionChecksOut) {
            //Everything seems to be in order
            boolean changePassword = patient.user.cleartextPassword
            boolean showRealtimeCTGMenu = realTimeCTGService.patientCanDoRealtimeCTGs(patient)
            def result = [id: patient.id, firstName: patient.firstName, lastName: patient.lastName, user: [id: patient.user.id, changePassword: changePassword], showRealtimeCTG: showRealtimeCTGMenu]
            render result as JSON
        } else {
            log.warn("User: ${user?.username}, Patient: ${patient}, client version too old. Was ${actualClientVersion} should be ${requiredClientVersion}.")
            render (status: 403, text: "Client version too old for this server. Update the client.") as JSON
        }
    }
}
