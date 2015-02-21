package org.opentele.server.citizen

import org.opentele.server.core.model.Schedule
import org.opentele.server.core.model.types.PatientState
import org.opentele.server.model.Patient
import org.opentele.server.model.QuestionnaireSchedule

import static java.util.Calendar.MINUTE
import static java.util.Calendar.SECOND
import static java.util.Calendar.HOUR_OF_DAY

class ReminderService {

    def questionnaireService
    def grailsApplication

    def getNextReminders(Patient patient, Calendar requestDate)  {
        if (patient.stateWithPassiveIntervals != PatientState.ACTIVE || patient.monitoringPlan == null) {
            return []
        }

        Collection reminders = getNextRemindersForAllQuestionnaireSchedules(patient, requestDate)

        reminders.sort { it.questionnaireId }
    }

    private Collection getNextRemindersForAllQuestionnaireSchedules(patient, Calendar requestDate) {
        def questionnaireSchedules = questionnaireService.getActiveQuestionnaireHeadersForPatient(patient)
        def reminders = questionnaireSchedules.collect { getNextRemindersForQuestionnaireSchedule(it, requestDate) }

        // Remove nulls from the list.
        reminders - null
    }

    private def getNextRemindersForQuestionnaireSchedule(QuestionnaireSchedule schedule, Calendar requestDate) {
        Calendar deadline = schedule.getNextDeadlineAfter(requestDate)
        if (deadline) {
            def remindersForQuestionnaire = nextRemindersForDeadline(deadline, schedule, requestDate)
            if (remindersForQuestionnaire) {
                return remindersForQuestionnaire
            } else {
                // If no reminders were found then also check the next deadline.
                deadline = schedule.getNextDeadlineAfter(deadline)
                if (deadline) {
                    return nextRemindersForDeadline(deadline, schedule, requestDate)
                }
            }
        }
    }

    private nextRemindersForDeadline(Calendar deadline, QuestionnaireSchedule schedule, Calendar requestDate) {
        // Only generate reminders if there has not been an upload within the grace period.
        if (!questionnaireService.hasUploadWithinGracePeriod(schedule, deadline)) {
            Calendar reminder = deadline.clone()
            if (schedule.type == Schedule.ScheduleType.WEEKDAYS_ONCE) {
                reminder[HOUR_OF_DAY] = schedule.reminderTime.hour
                reminder[MINUTE] = schedule.reminderTime.minute
                reminder[SECOND] = 0
            } else {
                reminder.add(MINUTE, -schedule.reminderStartMinutes)
            }

            def reminderEveryMinutes = (grailsApplication.config.reminderEveryMinutes ?: 15) as int
            if (reminderEveryMinutes <= 0) {
                // Invalid server configuration. Return early to avoid entering an infinite loop.
                return null
            }

            def alarms = []
            while (reminder.before(deadline)) {
                // Only consider reminders in the future.
                if (reminder.after(requestDate)) {
                    def secondsToNextReminder = (int)((reminder.getTimeInMillis() - requestDate.getTimeInMillis()) / 1000)
                    alarms << secondsToNextReminder
                }

                reminder.add(MINUTE, reminderEveryMinutes)
            }

            if (alarms.empty) {
                return null
            } else {
                def questionnaireHeader = schedule.questionnaireHeader
                def questionnaireId = questionnaireHeader.activeQuestionnaire.patientQuestionnaires?.find{true}?.id
                def questionnaireName = questionnaireHeader.name
                return [questionnaireId: questionnaireId,
                        questionnaireName: questionnaireName,
                        alarms: alarms]
            }
        }
    }
}

