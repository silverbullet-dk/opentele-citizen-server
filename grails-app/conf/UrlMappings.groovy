import grails.util.Environment

class UrlMappings {

	static mappings = {
		name root: "/" {
            controller = 'root'
            action = 'show'
        }

        name patient: "/patient"(parseRequest:true) {
            controller = 'patient'
            action = 'show'
        }

        name measurements: "/patient/measurements"(parseRequest:true) {
            controller = 'measurements'
            action = 'list'
        }

        name measurement: "/patient/measurements/$id?"(parseRequest:true) {
            controller = 'measurements'
            action = 'show'
        }

        // Special case of questionnaire controller
        "/rest/questionnaire/download/$id"(parseRequest:true){
            controller="questionnaireMobile"
            action = [GET:"download"]
        }

        "/rest/helpImage/$id"{
            controller="helpImage"
            action = [GET:"downloadimage"]
        }

        "/rest/patient/login"(parseRequest:true){
            controller="patientMobile"
            action = [GET:"login"]
        }

        name password: "/rest/password/update"(parseRequest: true) {
            controller="password"
            action = [POST:"update"]
        }

        name questionnaires: "/rest/questionnaire/listing"(parseRequest:true){
            controller="questionnaireMobile"
            action = [GET:"listing", POST: "upload"]
        }

        "/rest/conference/patientHasPendingConference"(parseRequest: true) {
            controller="patientConferenceMobile"
            action = [GET:"patientHasPendingConference"]
        }

        "/rest/conference/patientHasPendingMeasurement"(parseRequest: true) {
            controller="patientConferenceMobile"
            action = [GET:"patientHasPendingMeasurement"]
        }

        "/rest/conference/measurementFromPatient"(parseRequest: true) {
            controller="patientConferenceMobile"
            action = [POST:"measurementFromPatient"]
        }


        name patientMeasurementsMobile: "/rest/patient/measurements" {
            controller = "PatientMeasurementMobile"
            action = 'index'
        }

        name patientMeasurementsTypeMobile: "/rest/patient/measurements/$type" {
            controller = "PatientMeasurementMobile"
            action = "measurement"
        }

        "/rest/measurements/lastContinuousBloodSugarRecordNumber" (controller: 'QuestionnaireMobile', action:'lastContinuousBloodSugarRecordNumber')

        name reminders: "/rest/reminder/next" {
            controller = "Reminder"
            action = [GET:"next"]
        }

        // USED BY?
        "/rest/$controller/new"(parseRequest:true) {
            action = [GET:"newMessages"]
        }

        // Client
        name messageThreads: "/rest/message/recipients"(parseRequest:true){
            controller = "patientMessage"
            action = [GET:"messageRecipients"]
        }

        name acknowledgements: "/rest/questionnaire/acknowledgements"(parseRequest:true){
            controller="questionnaireMobile"
            action = [GET:"acknowledgements"]
        }

        "/rest/$controller/markAsRead"(parseRequest:true){
            action = [POST:"markAsRead"]
        }

        "/rest/realTimeCTG/save"(parseRequest:true) {
            controller="RealTimeCTG"
            action = [POST:"save"]
        }

        //For the meta controller
        "/currentVersion"(controller: "meta", action: "currentServerVersion")
        "/isAlive"(controller: "meta", action: "isAlive")
        "/isAlive/html"(controller: "meta", action: "isAlive")
        "/isAlive/json"(controller: "meta", action: "isAliveJSON")

        // The REST API for mobile devices
		"/rest/$controller/element/$id"(parseRequest:true){
			action = [GET:"show", DELETE: "delete", PUT: "update", POST: "read"]
		}

		name messages: "/rest/message/list" {
            controller = "PatientMessage"
			action = [GET:"list", POST: "save"]
		}

        "500"(view:"${(Environment.current == Environment.DEVELOPMENT) ? '/error' : '/productionError'}")
	}
}
