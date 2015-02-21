package org.opentele.server.citizen

import org.opentele.server.model.Clinician
import org.opentele.server.model.Patient
import org.opentele.server.model.PendingConference

import javax.servlet.AsyncContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handles notifications to clients of pending conference calls. Does this by storing an AsyncContext for each client
 * which has polled. Times out after a while by simply sending an empty response. When a conference call occurs, the
 * client (which in theory can have several waiting polls) is notified by sending the reply instantly.
 */
class ConferenceStateService {
    def videoConferenceService
    def grailsApplication
    private Queue<ContextAndTime> contextsAndTimes = new ConcurrentLinkedQueue<>()
    private Map<Long, List<ContextAndTime>> waitingPatients = new HashMap<>()

    /**
     * Removes old requests by sending a blank response, and sends proper responses to all clients with pending
     * conference calls.
     *
     * Before sending a reply to the client, it is verified that the clinician is still present in the meeting room.
     * Since this involves a SOAP call, it is done outside of the "synchronized" part of the method.
     */
    void update(Date now) {
        Date timeout = new Date(now.time - getTimeoutInMillis())

        List<NotificationInformation> toBeNotified
        synchronized (ConferenceStateService) {
            removeTimedOutContexts(timeout)
            toBeNotified = findAndRemoveContextsToBeNotified()
        }

        notifyContexts(toBeNotified)
    }

    /**
     * Adds a new client to the list of handled pending requests.
     */
    // PATIENT
    synchronized void add(Date now, long patientId, AsyncContext context) {
        def contextAndTime = new ContextAndTime(patientId: patientId, context: context, time: now)
        if (waitingPatients[patientId] == null) {
            waitingPatients[patientId] = []
        }
        waitingPatients[patientId] << contextAndTime
        contextsAndTimes.add(contextAndTime)
    }

    private void removeTimedOutContexts(Date timeout) {
        while (!contextsAndTimes.empty && contextsAndTimes.peek().time.before(timeout)) {
            ContextAndTime toTimeOut = contextsAndTimes.remove()
            removeFromWaitingPatients(toTimeOut)
            toTimeOut.context.complete()
        }
    }

    // Patient ?
    private List<NotificationInformation> findAndRemoveContextsToBeNotified() {
        List<NotificationInformation> result = []

        PendingConference.findAll().each { PendingConference pendingConference ->
            Patient patient = pendingConference.patient

            List<ContextAndTime> contextsAndTimesForPatient = waitingPatients[patient.id]
            if (contextsAndTimesForPatient != null) {
                contextsAndTimesForPatient.each {
                    result << new NotificationInformation(pendingConference: pendingConference, context: it.context)
                    removeFromContextsAndTimes(it)
                }
                waitingPatients.remove(patient.id)
            }
        }

        result
    }

    private void notifyContexts(List<NotificationInformation> toBeNotified) {
        toBeNotified.each {
            Clinician clinician = it.pendingConference.clinician

            if (videoConferenceService.userIsAlreadyPresentInOwnRoom(clinician.videoUser, clinician.videoPassword)) {
                it.context.response.writer << "{roomKey: '${it.pendingConference.roomKey}', serviceUrl: '${getServiceUrl()}'}"
            } else {
                it.pendingConference.delete()
            }
            it.context.complete()
        }
    }

    //Patient
    private removeFromWaitingPatients(ContextAndTime contextAndTime) {
        List<ContextAndTime> contextsAndTimesForPatient = waitingPatients[contextAndTime.patientId]
        if (contextsAndTimesForPatient == null) return

        contextsAndTimesForPatient.remove(contextAndTime)
        if (contextsAndTimesForPatient.empty) {
            waitingPatients.remove(contextAndTime.patientId)
        }
    }

    private removeFromContextsAndTimes(ContextAndTime contextAndTime) {
        contextsAndTimes.remove(contextAndTime)
    }

    String getServiceUrl() {
        grailsApplication.config.video.client.serviceURL
    }

    long getTimeoutInMillis() {
        grailsApplication.config.video.connection.timeoutMillis
    }

    private static class ContextAndTime {
        long patientId
        AsyncContext context
        Date time
    }

    private static class NotificationInformation {
        PendingConference pendingConference
        AsyncContext context
    }
}
