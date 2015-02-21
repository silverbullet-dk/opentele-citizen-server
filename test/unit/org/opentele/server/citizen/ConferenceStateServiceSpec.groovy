package org.opentele.server.citizen

import grails.buildtestdata.mixin.Build
import grails.test.mixin.*
import org.opentele.server.citizen.ConferenceStateService
import org.opentele.server.model.Clinician
import org.opentele.server.model.Patient
import org.opentele.server.model.PendingConference
import spock.lang.Specification

import javax.servlet.AsyncContext
import javax.servlet.ServletResponse

@TestFor(ConferenceStateService)
@Build([Patient, Clinician, PendingConference])
class ConferenceStateServiceSpec extends Specification {
    // Part of the interface of VideoConferenceService (since we don't necessarily have access to VideoConferenceService
    // when testing
    private static interface TestVideoConferenceService {
        boolean userIsAlreadyPresentInOwnRoom(String userName, String password)
    }

    StringWriter writer, anotherWriter
    AsyncContext context, anotherContext
    def videoConferenceService = Mock(TestVideoConferenceService)
    Patient patient
    Clinician clinician

    def setup() {
        patient = Patient.build()
        clinician = Clinician.build(videoUser: 'clinician', videoPassword: 'clinicianPassword')

        writer = new StringWriter()
        PrintWriter printWriter = new PrintWriter(writer)
        ServletResponse response = Mock(ServletResponse)
        response.writer >> { printWriter }
        context = Mock(AsyncContext)
        context.response >> { response }

        anotherWriter = new StringWriter()
        PrintWriter anotherPrintWriter = new PrintWriter(anotherWriter)
        ServletResponse anotherResponse = Mock(ServletResponse)
        anotherResponse.writer >> { anotherPrintWriter }
        anotherContext = Mock(AsyncContext)
        anotherContext.response >> { anotherResponse }

        service.videoConferenceService = videoConferenceService
        service.metaClass.getServiceUrl = { 'http://serviceUrl' }
        service.metaClass.getTimeoutInMillis = { 5 * 60 * 1000 } // 5 minutes
    }

    def 'does not time out contexts before timeout'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        when:
        service.update(at('2013/09/18 13:05:00'))

        then:
        0 * context.complete()
    }

    def 'times out contexts'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        when:
        service.update(at('2013/09/18 13:05:01'))

        then:
        writer.toString() == ''
        1 * context.complete()
    }

    def 'does nothing when conference occurs for a timed out context'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'abc')
        PendingConference.build(patient: patient, clinician: Clinician.build(), roomKey:'def')
        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'ghi')

        when:
        service.update(at('2013/09/18 13:05:01'))

        then:
        writer.toString() == ''
        1 * context.complete()
    }

    def 'sends required parameters to client when conference occurs before timeout and clinician is present in room'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'abc')
        PendingConference.build(patient: patient, clinician: clinician, roomKey:'def')
        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'ghi')

        videoConferenceService.userIsAlreadyPresentInOwnRoom('clinician', 'clinicianPassword') >> true

        when:
        service.update(at('2013/09/18 13:05:00'))

        then:
        writer.toString() == "{roomKey: 'def', serviceUrl: 'http://serviceUrl'}"
        1 * context.complete()
        PendingConference.count == 3
    }

    def 'sends empty parameters to client when clinician is not present in room anymore'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        videoConferenceService.userIsAlreadyPresentInOwnRoom('clinician', 'clinicianPassword') >> false

        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'abc')
        PendingConference.build(patient: patient, clinician: clinician, roomKey:'def')
        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'ghi')

        when:
        service.update(at('2013/09/18 13:05:00'))

        then:
        writer.toString() == ''
        1 * context.complete()
        PendingConference.count == 2
    }

    def 'sends required parameters to several clients when conference occurs before timeout'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)
        service.add(at('2013/09/18 13:02:00'), patient.id, anotherContext)

        videoConferenceService.userIsAlreadyPresentInOwnRoom('clinician', 'clinicianPassword') >> true

        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'abc')
        PendingConference.build(patient: patient, clinician: clinician, roomKey:'def')
        PendingConference.build(patient: Patient.build(), clinician: Clinician.build(), roomKey:'ghi')

        when:
        service.update(at('2013/09/18 13:05:00'))

        then:
        writer.toString() == "{roomKey: 'def', serviceUrl: 'http://serviceUrl'}"
        anotherWriter.toString() == "{roomKey: 'def', serviceUrl: 'http://serviceUrl'}"
        1 * context.complete()
        1 * anotherContext.complete()
        PendingConference.count == 3
    }

    def 'only sends parameters first time a conference occurs'() {
        setup:
        service.add(at('2013/09/18 13:00:00'), patient.id, context)

        videoConferenceService.userIsAlreadyPresentInOwnRoom('clinician', 'clinicianPassword') >> true

        PendingConference.build(patient: patient, clinician: clinician, roomKey:'def')

        when:
        service.update(at('2013/09/18 13:03:00'))
        service.update(at('2013/09/18 13:04:00'))

        then:
        writer.toString() == "{roomKey: 'def', serviceUrl: 'http://serviceUrl'}"
        1 * context.complete()
        PendingConference.count == 1
    }

    private Date at(time) {
        Date.parse('yyyy/M/d H:m:s', time)
    }
}
