package org.opentele.server.core

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

@Secured(["IS_AUTHENTICATED_FULLY","IS_AUTHENTICATED_REMEMBERED"])
class PasswordController {
    static allowedMethods = [change:'GET',  update:'POST', changed: 'GET']

    def passwordService

    def change() {
        [command: new PasswordCommand()]
    }

    def update() {
        def changePasswordCommand = new PasswordCommand()
        bindData(changePasswordCommand, params)

        passwordService.changePassword(changePasswordCommand)
        if(changePasswordCommand.hasErrors()) {
            def errors = changePasswordCommand.errors.fieldErrors.collect { [field: it.field, error: message(error: it)]}
            render([status: 'error', errors: errors] as JSON)
        } else {
            def message = message(code: "password.changed.for.user", args: [changePasswordCommand.user.username])
            render([status: 'ok', message: message] as JSON)
        }
    }

    def changed() { }
}
