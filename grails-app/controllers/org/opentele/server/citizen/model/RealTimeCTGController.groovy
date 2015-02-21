package org.opentele.server.citizen.model

import grails.plugin.springsecurity.annotation.Secured
import grails.validation.ValidationException
import org.opentele.server.core.model.types.PermissionName

@Secured(PermissionName.NONE)
class RealTimeCTGController {

    def springSecurityService
    def realTimeCTGService

    static allowedMethods = [save: "POST"]

    @Secured(PermissionName.REALTIME_CTG_SAVE)
    def save() {
        try {
            realTimeCTGService.save(params)
        } catch (ValidationException ex) {
            response.sendError(400)
            return;
        }

        response.setStatus(201)
        render ''
    }
}
