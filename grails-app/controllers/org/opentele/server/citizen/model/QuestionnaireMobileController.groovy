package org.opentele.server.citizen.model

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.core.util.ISO8601DateParser
import org.opentele.server.model.Patient
import org.opentele.server.model.patientquestionnaire.CompletedQuestionnaire
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire

import java.text.ParseException

@Secured(PermissionName.NONE)
class QuestionnaireMobileController {

    def questionnaireService
    def questionnaireDownloadService
    def springSecurityService
    def completedQuestionnaireService
    def i18nService
    def continuousBloodSugarMeasurementService

    @Secured(PermissionName.QUESTIONNAIRE_READ_ALL)
    def listing() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def results = questionnaireService.list(patient.cpr)

        render results.encodeAsJSON()
    }

    @Secured(PermissionName.QUESTIONNAIRE_DOWNLOAD)
    def download() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        String nameParam = params.name
        def questionnaireId = params.id

        if (!patient || !questionnaireId) {
            render [["failure"], ["A required parameter was empty. Required params: id"]] as JSON
            return
        }

        def q = PatientQuestionnaire.get(questionnaireId)

        if (!q) {
            render [["failure"], ["Patient Questionnaire not found for Patient id:${patient.id}, name:${nameParam}"]] as JSON
            return
        }

        render questionnaireDownloadService.asJson(q) as JSON
    }

    @Secured(PermissionName.QUESTIONNAIRE_UPLOAD)
    def upload() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def jsonRequest = request.JSON

        def patientQuestionnaireId = jsonRequest.QuestionnaireId as Long

        def errors = []
        def hasErrors = false

        if (!patient || !patientQuestionnaireId) {
            hasErrors = true
            errors <<  "A required parameter is missing. Received: QuestionnaireId:${patientQuestionnaireId}."
        }

        def date
        if (jsonRequest.date) {
            try {
                date = ISO8601DateParser.parse(jsonRequest.date)
            } catch (ParseException pax) {
                log.warn("ParseException: ${pax}", pax)
            }
        }
        if (!date) {
            hasErrors = true
            errors <<  "Required parameter 'date' is missing or not parseable. Received: ${jsonRequest.date}."
        }

        def results
        if (hasErrors) {
            results = []
            results << ["failure"]
            results << errors
        } else {
            results = completedQuestionnaireService.handleResults(patient, patientQuestionnaireId, date, jsonRequest.output)
        }

        render results as JSON
    }

    @Secured(PermissionName.QUESTIONNAIRE_UPLOAD)
    def lastContinuousBloodSugarRecordNumber() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        render continuousBloodSugarMeasurementService.lastContinuousBloodSugarRecordNumberForPatient(patient) as JSON
    }

    @Secured([PermissionName.QUESTIONNAIRE_ACKNOWLEDGED_READ])
    def acknowledgements() {

        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def thirtyDaysAgo = new Date() - 30;
        def questionnaires = CompletedQuestionnaire.findAllByPatientAndShowAcknowledgementToPatientAndAcknowledgedDateGreaterThanEquals(patient, true, thirtyDaysAgo,[max: 20, sort: "acknowledgedDate", order: "desc"])

        render createAcknowledgementsListResult(questionnaires) as JSON
    }

    private createAcknowledgementsListResult(Collection<CompletedQuestionnaire> completedQuestionnaires) {

        def acknowledgements = completedQuestionnaires.collect { createAcknowledgementResult(it) }
        [
                acknowledgements: acknowledgements
        ]
    }

    private createAcknowledgementResult(CompletedQuestionnaire completedQuestionnaire) {

        def acknowledgedDate = completedQuestionnaire.acknowledgedDate
        def receivedDate = completedQuestionnaire.receivedDate
        def name = completedQuestionnaire.patientQuestionnaire?.name

        def message = i18nService.message(code: 'completedquestionnaire.message.body',
                args: [name,
                        receivedDate.format(i18nService.message(code: 'default.date.format.notime.short')),
                        receivedDate.format(i18nService.message(code: 'default.time.format.noseconds')),
                        acknowledgedDate.format(i18nService.message(code: 'default.date.format.notime.short')),
                        acknowledgedDate.format(i18nService.message(code: 'default.time.format.noseconds')),
                        receivedDate.format(i18nService.message(code: 'default.date.noyear.format.long')),
                        acknowledgedDate.format(i18nService.message(code: 'default.date.noyear.format.long'))
                ])

        [
                id: completedQuestionnaire.id,
                message: message
        ]
    }

}
