package org.opentele.server.citizen

import grails.buildtestdata.mixin.Build
import grails.test.mixin.TestFor
import org.opentele.server.model.*
import spock.lang.Specification

@TestFor(RealTimeCTGService)
@Build([Patient, PatientGroup, Patient2PatientGroup])
class RealTimeCTGServiceSpec extends Specification {

    def "can tell if patient may do realtime CTGs"() {

        when:
        def firstPatientGroup = PatientGroup.build(showRunningCtgMessaging: firstPatientGroupAllowsCTG)
        def secondPatientGroup = PatientGroup.build(showRunningCtgMessaging: secondPatientGroupAllowsCTG)
        def patient = Patient.build()

        Patient2PatientGroup.link(patient, firstPatientGroup)
        Patient2PatientGroup.link(patient, secondPatientGroup)

        def replyFromService = service.patientCanDoRealtimeCTGs(patient)

        then:
        replyFromService == mayDoRealtimeCTGs

        where:
        firstPatientGroupAllowsCTG || secondPatientGroupAllowsCTG   || mayDoRealtimeCTGs
        true || false || true
        true || true || true
        false || false || false
    }

}
