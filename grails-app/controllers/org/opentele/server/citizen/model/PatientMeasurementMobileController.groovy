package org.opentele.server.citizen.model

import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.core.model.types.PermissionName
import org.opentele.server.core.util.TimeFilter
import org.opentele.server.model.Patient

@Secured(PermissionName.NONE)
class PatientMeasurementMobileController {
    def citizenMeasurementService
    def springSecurityService

    @Secured(PermissionName.PATIENT_LOGIN)
    def index() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        def tables = citizenMeasurementService.dataForTables(patient, TimeFilter.all())
	def nonCTGTables = tables.findAll{it.type.toString() != 'CTG'} //We cannot meaningfully show CTG data

        [tables:nonCTGTables]
    }

    @Secured(PermissionName.PATIENT_LOGIN)
    def measurement() {
        def user = springSecurityService.currentUser
        def patient = Patient.findByUser(user)

        // Check for filter in params, otherwise set week.
        def timeFilter = TimeFilter.fromParams(params.filter ? params : [filter: "WEEK"])

        def (tables, bloodsugarData) = citizenMeasurementService.dataForTablesAndBloodsugar(patient, timeFilter)
        def tableData = null

        if (params.type != "BLOODSUGAR") {
            def table = tables.find { it.type.toString() == params.type }
            tableData = table ? table.measurements*.measurement : []
        }
        def graphData = citizenMeasurementService.dataForGraph(patient, timeFilter, params.type)

        [patientInstance:patient, type: params.type, tableData: tableData, bloodSugarData: bloodsugarData, graphData: graphData, hasCgmGraphs: !patient.cgmGraphs.empty]
    }
}
