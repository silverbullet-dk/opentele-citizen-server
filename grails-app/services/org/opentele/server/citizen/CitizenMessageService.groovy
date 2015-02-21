package org.opentele.server.citizen

import org.opentele.server.model.Patient
import org.opentele.server.model.Patient2PatientGroup

class CitizenMessageService {
    def isMessagesAvailableTo(Patient patient) {
        return possibleRecipientsFor(patient).size() > 0
    }

    def possibleRecipientsFor(Patient patient) {
        def patientGroups = Patient2PatientGroup.findAllByPatient(patient)*.patientGroup
        return  patientGroups.findAll {!it.disableMessaging}.collect {it.department}.unique()
    }
}
