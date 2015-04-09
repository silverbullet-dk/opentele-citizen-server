package org.opentele.server.citizen

import org.opentele.server.model.Patient
import org.opentele.server.model.RealTimeCtg

class RealTimeCTGService {

    def save(def params) {
        new RealTimeCtg(params).save(failOnError: true)
    }

    def deleteFor(patientWithSamples) {
        RealTimeCtg.where {patient == patientWithSamples}.deleteAll()
    }

    public boolean patientCanDoRealtimeCTGs(Patient patient) {
        return patient.patient2PatientGroups?.patientGroup.any {it.showRunningCtgMessaging}
    }
}
