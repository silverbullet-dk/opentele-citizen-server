package org.opentele.server.citizen

import org.opentele.server.model.Patient
import org.opentele.server.model.RealTimectg

class RealTimeCTGService {


    def save(def params) {
        new RealTimectg(params).save(failOnError: true)
    }

    public boolean patientCanDoRealtimeCTGs(Patient patient) {
        return patient.patient2PatientGroups?.patientGroup.any {it.showRunningCtgMessaging}
    }
}
