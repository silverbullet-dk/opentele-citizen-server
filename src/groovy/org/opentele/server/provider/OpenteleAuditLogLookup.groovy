package org.opentele.server.provider

import dk.silverbullet.kih.api.auditlog.AuditLogLookup
import org.slf4j.LoggerFactory

/**
 * User: lch, pu
 * Created: 11/16/12
 * Version: 09/12/14
 */
class OpenteleAuditLogLookup implements AuditLogLookup {

    def log = LoggerFactory.getLogger(OpenteleAuditLogLookup.class)

    @Override
    Map retrieve() {
        return [:]
    }
}
