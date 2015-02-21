package org.opentele.server.citizen

import grails.buildtestdata.mixin.Build
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import org.opentele.builders.CompletedQuestionnaireBuilder
import org.opentele.builders.MonitoringPlanBuilder
import org.opentele.builders.PatientBuilder
import org.opentele.builders.QuestionnaireScheduleBuilder
import org.opentele.server.core.QuestionnaireNodeService
import org.opentele.server.core.QuestionnaireService
import org.opentele.server.core.model.Schedule
import org.opentele.server.core.model.Schedule.TimeOfDay
import org.opentele.server.core.model.types.Weekday
import org.opentele.server.model.MonitoringPlan
import org.opentele.server.model.Patient
import org.opentele.server.model.QuestionnaireSchedule
import org.opentele.server.model.ScheduleWindow
import org.opentele.server.model.patientquestionnaire.CompletedQuestionnaire
import org.opentele.server.model.patientquestionnaire.PatientBooleanNode
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.questionnaire.*
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(ReminderService)
@Build([QuestionnaireHeader, Questionnaire, QuestionnaireNode, QuestionnaireSchedule, PatientBooleanNode, BooleanNode, ScheduleWindow, PatientQuestionnaire, MonitoringPlan])
@Mock([CompletedQuestionnaire, Questionnaire2MeterType])
class ReminderServiceSpec extends Specification {
    Patient patient
    Date startingDate
    MonitoringPlan monitoringPlan

    def setup() {
        patient = new PatientBuilder().build()
        startingDate = Date.parse("yyyy/M/d", "2013/6/5")
        monitoringPlan = new MonitoringPlanBuilder().forPatient(patient).forStartDate(startingDate).build()

        ScheduleWindow.build(scheduleType: Schedule.ScheduleType.WEEKDAYS, windowSizeMinutes: 60)
        ScheduleWindow.build(scheduleType: Schedule.ScheduleType.WEEKDAYS_ONCE, windowSizeMinutes: 24 * 60)
        ScheduleWindow.build(scheduleType: Schedule.ScheduleType.MONTHLY, windowSizeMinutes: 60)
        ScheduleWindow.build(scheduleType: Schedule.ScheduleType.EVERY_NTH_DAY, windowSizeMinutes: 60)
        ScheduleWindow.build(scheduleType: Schedule.ScheduleType.SPECIFIC_DATE, windowSizeMinutes: 60)

       // service.questionnaireNodeService = Mock(QuestionnaireNodeService)
        service.questionnaireService = new QuestionnaireService()
    }

    def "no reminders if there is no questionnaire"() {
        setup:
        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 16:00:02").toCalendar()

        when:
        def nextReminder = service.getNextReminders(patient, requestDate)

        then:
        nextReminder == []
    }

    def "no reminders if there is a questionnaire that is not scheduled"() {
        setup:
        new QuestionnaireScheduleBuilder().
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.UNSCHEDULED).
                build()

        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 15:29:59").toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)

        then:
        result == []
    }

    def "no reminders if there is a questionnaire that is not active"() {
        setup:
        def questionnaireSchedule = new QuestionnaireScheduleBuilder().
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()
        questionnaireSchedule.questionnaireHeader.activeQuestionnaire = null

        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 15:29:59").toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)

        then:
        result == []
    }

    @Unroll
    def "reminder if there is a questionnaire that is not answered yet"() {
        setup:
        new QuestionnaireScheduleBuilder().
                forQuestionnaireName('q1').
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", requestDate).toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, requestDate)

        then:
        questionnaireNamesAndReminderDates == expectedQuestionnaireIdsAndReminderDates

        where:
        requestDate           | expectedQuestionnaireIdsAndReminderDates
        "2013-06-05 15:29:59" | [['q1', '2013-06-05 15:30:00', '2013-06-05 15:45:00']]
        "2013-06-05 15:30:00" | [['q1', '2013-06-05 15:45:00']]
        "2013-06-05 15:30:01" | [['q1', '2013-06-05 15:45:00']]
        "2013-06-05 15:45:01" | []
        "2013-06-05 16:00:01" | []
    }

    @Unroll
    def "gives first reminders for both questionnaires when there are two questionnaires with reminders"() {
        setup:
        new QuestionnaireScheduleBuilder().
                forQuestionnaireName('q1').
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        new QuestionnaireScheduleBuilder().
                forQuestionnaireName('q2').
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 10)]).
                forReminderStartMinutes(30).
                build()

        requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", requestDate).toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, requestDate)

        then:
        questionnaireNamesAndReminderDates == expectedQuestionnaireNamesAndReminderDates

        where:
        requestDate           | expectedQuestionnaireNamesAndReminderDates
        '2013-06-05 15:29:59' | [['q1', '2013-06-05 15:30:00', '2013-06-05 15:45:00'], ['q2', '2013-06-05 15:40:00', '2013-06-05 15:55:00']]
        '2013-06-05 15:30:01' | [['q1', '2013-06-05 15:45:00'], ['q2', '2013-06-05 15:40:00', '2013-06-05 15:55:00']]
        '2013-06-05 15:40:01' | [['q1', '2013-06-05 15:45:00'], ['q2', '2013-06-05 15:55:00']]
        '2013-06-05 15:45:01' | [['q2', '2013-06-05 15:55:00']]
    }

    def 'gives reminder even if other, irrelevant patient has uploaded within the grace period'() {
        setup:
        Patient otherPatient = new PatientBuilder().build()
        MonitoringPlan otherMonitoringPlan = new MonitoringPlanBuilder().forPatient(patient).forStartDate(startingDate).build()

        // Schedule and completed questionnaire for other patient
        Date receivedDate = Date.parse('yyyy-MM-dd HH:mm:ss', '2013-06-05 15:29:58')
        def completedQuestionnaire = new CompletedQuestionnaireBuilder(receivedDate: receivedDate).forName('q1').forPatient(otherPatient).build()
        new QuestionnaireScheduleBuilder(questionnaireHeader: completedQuestionnaire.questionnaireHeader).
                forMonitoringPlan(otherMonitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        // Schedule for our patient, with no completed questionnaires
        new QuestionnaireScheduleBuilder(questionnaireHeader: completedQuestionnaire.questionnaireHeader).
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 15:29:59").toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, requestDate)

        then:
        questionnaireNamesAndReminderDates == [['q1', '2013-06-05 15:30:00', '2013-06-05 15:45:00']]
    }

    @Unroll
    def "no reminder if data is uploaded within the grace period"() {
        setup:
        receivedDate = Date.parse("yyyy-MM-dd HH:mm:ss", receivedDate)
        def completedQuestionnaire = new CompletedQuestionnaireBuilder(receivedDate: receivedDate).forName('q1').forPatient(patient).build()
        new QuestionnaireScheduleBuilder(questionnaireHeader: completedQuestionnaire.questionnaireHeader).
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.SPECIFIC_DATE).
                forSpecificDate(Date.parse("yyyy-MM-dd", "2013-06-05")).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 15:29:59").toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, requestDate)

        then:
        questionnaireNamesAndReminderDates == expectedQuestionnaireNamesAndReminderDates

        where:
        receivedDate          | expectedQuestionnaireNamesAndReminderDates
        '2013-06-05 14:59:59' | [['q1', '2013-06-05 15:30:00', '2013-06-05 15:45:00']]
        '2013-06-05 15:29:58' | []
    }

    @Unroll
    def "reminder moves to the next deadline with a repeating schedule once an upload is made"() {
        setup:

        def completedQuestionnaire = null
        if (receivedDate) {
            receivedDate = Date.parse("yyyy-MM-dd HH:mm:ss", receivedDate)
            completedQuestionnaire = new CompletedQuestionnaireBuilder(receivedDate: receivedDate).forName('q1').forPatient(patient).build()
        }

        new QuestionnaireScheduleBuilder(questionnaireHeader: completedQuestionnaire?.questionnaireHeader).
                forQuestionnaireName('q1').
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.WEEKDAYS).
                forWeekdays(Weekday.values().collect()).
                forTimesOfDay([new Schedule.TimeOfDay(hour: 16, minute: 0)]).
                forReminderStartMinutes(30).
                build()

        def requestDate = Date.parse("yyyy-MM-dd HH:mm:ss", "2013-06-05 15:29:59").toCalendar()

        when:
        def result = service.getNextReminders(patient, requestDate)
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, requestDate)

        then:
        questionnaireNamesAndReminderDates == expectedQuestionnaireNamesAndReminderDates

        where:
        receivedDate          | expectedQuestionnaireNamesAndReminderDates
        null                  | [['q1', '2013-06-05 15:30:00', '2013-06-05 15:45:00']]
        '2013-06-05 15:29:58' | [['q1', '2013-06-06 15:30:00', '2013-06-06 15:45:00']]
    }

    @Unroll
    def "reminder moves to the next deadline with a repeating schedule weekly once an upload is made"() {
        setup:

        def completedQuestionnaire = null
        if (receivedDate) {
            receivedDate = Date.parse("yyyy-MM-dd HH:mm:ss", receivedDate)
            completedQuestionnaire = new CompletedQuestionnaireBuilder(receivedDate: receivedDate).forName('q1').forPatient(patient).build()
        }

        new QuestionnaireScheduleBuilder(questionnaireHeader: completedQuestionnaire?.questionnaireHeader).
                forQuestionnaireName('q1').
                forMonitoringPlan(monitoringPlan).
                forScheduleType(Schedule.ScheduleType.WEEKDAYS_ONCE).
                forWeekdaysOnce(Weekday.values().collect(), [Weekday.WEDNESDAY, Weekday.SUNDAY]).
                forReminderTime(TimeOfDay.toTimeOfDay('10:00')).
                forBlueAlarmTime(TimeOfDay.toTimeOfDay('11:30')).
                build()

        when:
        def result = service.getNextReminders(patient, toCalendar(requestDate))
        def questionnaireNamesAndReminderDates = getQuestionnaireNamesAndReminderDates(result, toCalendar(requestDate))

        then:
        questionnaireNamesAndReminderDates == expectedQuestionnaireNamesAndReminderDates

        where:
        requestDate          | receivedDate          | expectedQuestionnaireNamesAndReminderDates
        "2013-06-05 08:31:59" | null                  | [['q1', '2013-06-05 10:00:00', '2013-06-05 10:15:00', '2013-06-05 10:30:00', '2013-06-05 10:45:00', '2013-06-05 11:00:00', '2013-06-05 11:15:00']]
        "2013-06-05 10:50:59" | '2013-06-05 10:31:58' | [['q1', '2013-06-06 10:00:00', '2013-06-06 10:15:00', '2013-06-06 10:30:00', '2013-06-06 10:45:00', '2013-06-06 11:00:00', '2013-06-06 11:15:00']]
        "2013-06-06 06:31:59" | '2013-06-05 11:28:58' | [['q1', '2013-06-06 10:00:00', '2013-06-06 10:15:00', '2013-06-06 10:30:00', '2013-06-06 10:45:00', '2013-06-06 11:00:00', '2013-06-06 11:15:00']]
    }

    private toCalendar(String requestDate) {
        Date.parse("yyyy-MM-dd HH:mm:ss", requestDate).toCalendar()
    }

    private def getQuestionnaireNamesAndReminderDates(def serviceResult, Calendar requestTime) {
        serviceResult.sort {
            it.questionnaireId
        }.collect {
            def questionnaireNameAndReminderDates = [it.questionnaireName]
            it.alarms.each {
                Calendar reminderDate = requestTime.clone()
                reminderDate.add(Calendar.SECOND, it)
                questionnaireNameAndReminderDates << reminderDate.format('yyyy-MM-dd HH:mm:ss')
            }
            questionnaireNameAndReminderDates
        }
    }
}
