package org.opentele.server

import org.codehaus.groovy.grails.web.json.JSONObject

class JSONParamsMapFilters {
    // From: http://stackoverflow.com/questions/10834422/grails-command-object-how-to-load-request-json-into-it
    def filters = {
        all() {
            before = {
                def json = request.JSON
                if(json && json instanceof JSONObject) {
                    json.each { key, value ->
                        params[key] = value
                    }
                }
            }
        }
    }
}
