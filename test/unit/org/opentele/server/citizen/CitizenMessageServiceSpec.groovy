package org.opentele.server.citizen

import grails.buildtestdata.mixin.Build
import grails.test.mixin.*
import org.junit.*
import org.opentele.builders.PatientBuilder
import org.opentele.server.model.Department
import org.opentele.server.model.Message
import org.opentele.server.model.Patient
import org.opentele.server.model.Patient2PatientGroup
import org.opentele.server.model.PatientGroup
import org.opentele.server.model.User
import spock.lang.Specification

@TestFor(CitizenMessageService)
@Build([Patient, User, PatientGroup, Patient2PatientGroup, Department, Message])
class CitizenMessageServiceSpec extends Specification {
    Patient patient

    def setup() {
        patient = new PatientBuilder().build()
        patient.user = new User()
    }

    void "can get list of possible message recipients for patient"() {
        setup:
        setupPatientGroupForDepartment(false)

        when:
        def recipients = service.possibleRecipientsFor(patient)

        then:
        recipients.size() == 1
    }

    void "message recipient not included in possible recipients when messaging is disabled"() {
        setup:
        setupPatientGroupForDepartment(true)

        when:
        def recipients = service.possibleRecipientsFor(patient)

        then:
        recipients.size() == 0
    }


    def "When a department only contains at least one patientGroup that allows messages it should appear in recipients list"() {
        setup:
        Department departmentWithMixedPatientGroups = Department.build(name: "ShouldAcceptMessages")

        PatientGroup disallowsMessagesPatientGroup = PatientGroup.build(disableMessaging: true, department: departmentWithMixedPatientGroups)
        Patient2PatientGroup patient2DisallowsMessagesPatientGroup = Patient2PatientGroup.build(patientGroup: disallowsMessagesPatientGroup)

        PatientGroup allowsMessagesPatientGroup = PatientGroup.build(disableMessaging: false, department: departmentWithMixedPatientGroups)
        Patient2PatientGroup patient2AllowsMessagesPatientGroup = Patient2PatientGroup.build(patientGroup: allowsMessagesPatientGroup)

        patient.patient2PatientGroups = [patient2DisallowsMessagesPatientGroup, patient2AllowsMessagesPatientGroup]

        patient2DisallowsMessagesPatientGroup.patient = patient
        patient2DisallowsMessagesPatientGroup.save(validate: false)

        patient2AllowsMessagesPatientGroup.patient = patient
        patient2AllowsMessagesPatientGroup.save(validate: false)

        when:
        def recipients = service.possibleRecipientsFor(patient)

        then:
        recipients.size() == 1
    }

        void "when messaging is disabled for all patient groups, patient does not have access to messages"() {
        setup:
        setupPatientGroupForDepartment(true)

        when:
        def available = service.isMessagesAvailableTo(patient)

        then:
        available == false
    }

    void "when messaging is enabled for at least one patient groups, patient have access to messages"() {
        setup:
        setupPatientGroupForDepartment(false)
        setupPatientGroupForDepartment(true, "SomeOtherDepartmentMessagesDisabled")

        when:
        def available = service.isMessagesAvailableTo(patient)

        then:
        available == true
    }

    def setupPatientGroupForDepartment(messagingDisabled, name = "SomeDepartment") {
        Department department = Department.build(name: name)

        PatientGroup patientGroup = PatientGroup.build(disableMessaging: messagingDisabled, department:department)
        Patient2PatientGroup patient2PatientGroup = Patient2PatientGroup.build(patientGroup: patientGroup)

        patient.patient2PatientGroups = [patient2PatientGroup]

        patient2PatientGroup.patient = patient
        patient2PatientGroup.save(validate: false)
    }
}
