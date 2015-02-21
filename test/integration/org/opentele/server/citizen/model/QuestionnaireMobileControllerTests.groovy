package org.opentele.server.citizen.model

import grails.test.mixin.TestMixin
import grails.test.mixin.integration.IntegrationTestMixin
import org.junit.Test
import org.opentele.server.core.test.AbstractControllerIntegrationTest
import org.opentele.server.model.BloodPressureThreshold
import org.opentele.server.model.Measurement
import org.opentele.server.model.MeasurementType
import org.opentele.server.model.NumericThreshold
import org.opentele.server.model.Patient
import org.opentele.server.model.UrineThreshold
import org.opentele.server.model.patientquestionnaire.CompletedQuestionnaire
import org.opentele.server.model.patientquestionnaire.PatientQuestionnaire
import org.opentele.server.model.questionnaire.Questionnaire
import org.opentele.server.model.questionnaire.QuestionnaireHeader
import org.opentele.server.core.model.types.MeasurementTypeName
import org.opentele.server.core.model.types.ProteinValue
import org.opentele.server.core.model.types.Severity

import static org.junit.Assert.*

@TestMixin(IntegrationTestMixin)
class QuestionnaireMobileControllerTests extends AbstractControllerIntegrationTest {
    def daoAuthenticationProvider
    def grailsApplication
    QuestionnaireMobileController controller

    void setUp() {
        // Avoid conflicts with objects in session created earlier. E.g. in bootstrap
        grailsApplication.mainContext.sessionFactory.currentSession.clear()
        
        authenticate 'NancyAnn','abcd1234'
        controller = new QuestionnaireMobileController()
    }

    @Test
    void testCanDownloadAllQuestionnaires() {
        def patientQuestionnaires = PatientQuestionnaire.list()
        assert !patientQuestionnaires.empty

        patientQuestionnaires.each { pq ->
            controller.response.reset()

            controller.params.id = pq.id
            controller.download()

            assert controller.response.json != null
        }
    }

    @Test
    void  testUploadBPWithNullsAndEmptyCancelVar() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("Blodtryk")

        Patient p = Patient.findByCpr("2512484916")
        p.setThreshold(BloodPressureThreshold.build(type: MeasurementType.findByName(MeasurementTypeName.BLOOD_PRESSURE), systolicAlertHigh: 80, systolicAlertLow: 40, systolicWarningLow: 50, systolicWarningHigh: 70, diastolicAlertLow: 40, diastolicWarningLow: 50, diastolicAlertHigh: 80, diastolicWarningHigh: 70))
        p.save(failOnError: true, flush: true)


        controller.params.putAll([id:origPq.id])
        controller.download()
        

        def questionnaireId = controller.response.json.id
        
        assertNotNull(questionnaireId)
        
        println "BP questionnaire1: " + controller.response.json.toString()
        
        def diaId
        def sysId
        def pulseId

        boolean foundNode = false
        controller.response.json.nodes.each() { node ->
            if (node.has("BloodPressureDeviceNode")) {
                def bpNode = node.get("BloodPressureDeviceNode")
                foundNode = true
                
                assertNotNull bpNode.diastolic
                assertNotNull bpNode.systolic
                assertNotNull bpNode.pulse
                
                diaId = bpNode.diastolic.name
                sysId = bpNode.systolic.name
                pulseId = bpNode.pulse.name
            }
        }

        def tokens = diaId.tokenize("#")
        def varName = "${tokens[0]}##CANCEL"
        
        if (!foundNode) {
            fail("Expected bloodpressure questionnaire to include BloodPressureDeviceNode, but it did not :-(")
        }
    
        // Build upload json message, containing results..
        def testData = [ "QuestionnaireId":questionnaireId,
            "date": new Date(),
            "output":[
                ["name":diaId, "type":"Float", "value": "150.1"],
                ["name":sysId, "type":"Float", "value": "91.0"],
                ["name":pulseId, "type":"Float"],
                ["name":varName, "type":"Boolean", "value":null ]
                ]
            ]

        controller.response.reset()
        
        QuestionnaireMobileController replyController = new QuestionnaireMobileController()
        
        replyController.request.json = testData
        replyController.upload()
        
        println "BPResponse: " + replyController.response.json.toString()

        assert 'success' == replyController.response.json[0][0].toString()
    }
    
    @Test
    void testUploadBPCancelledResult() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("Blodtryk")
        
        controller.params.putAll([id:origPq.id])
        controller.download()

        def questionnaireId = controller.response.json.id

        assertNotNull(questionnaireId)

        println "BP questionnaire1: " + controller.response.json.toString()
        
        def diaId

        boolean foundNode = false
        controller.response.json.nodes.each() { node ->
            if (node.has("BloodPressureDeviceNode")) {
                def bpNode = node.get("BloodPressureDeviceNode")
                foundNode = true
                
                assertNotNull bpNode.diastolic
                assertNotNull bpNode.systolic
                assertNotNull bpNode.pulse
                
                diaId = bpNode.diastolic.name
            }
        }
        
        def tokens = diaId.tokenize("#")
        def varName = "${tokens[0]}##CANCEL"
        
        if (!foundNode) {
            fail("Expected bloodpressure questionnaire to include BloodPressureDeviceNode, but it did not :-(")
        }
    
        // Build upload json message, containing results..
        def testData = [ "QuestionnaireId":questionnaireId,
            "date": new Date()-2,
            "output":[
                ["name":varName, "type":"Boolean", "value":true]
                ]
            ]

        controller.response.reset()
        
        QuestionnaireMobileController replyController = new QuestionnaireMobileController()
        
        replyController.request.json = testData
        replyController.upload()
        
        println "BPResponse: " + replyController.response.json.toString()

        assert 'success' == replyController.response.json[0][0].toString()
    }

	@Test
	void testUploadResultWithNote() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("Proteinindhold i urin");

        Patient pt = Patient.findByCpr("2512484916")
        pt.setThreshold(UrineThreshold.build(type: MeasurementType.findByName(MeasurementTypeName.URINE), alertHigh: ProteinValue.PLUS_FOUR, alertLow: ProteinValue.NEGATIVE, warningLow: ProteinValue.PLUSMINUS, warningHigh: ProteinValue.PLUS_THREE))
		pt.save(failOnError:true, flush: true)
		
		controller.params.putAll([id:origPq.id])
		controller.download()

		def questionnaireId = controller.response.json.id

		assertNotNull(questionnaireId)
		
		def urineId
		boolean foundNode = false
		controller.response.json.nodes.each() { node ->
			if (node.has("UrineDeviceNode")) {
				def urineNode = node.get("UrineDeviceNode")
				foundNode = true
				
				assertNotNull urineNode.urine
				
				urineId = urineNode.urine.name
			}
		}
		
		if (!foundNode) {
			fail("Expected urine questionnaire to include UrineDeviceNode, but it did not :-/")
		}
		
		// Build upload json message, containing results..
		def testData = [
			"QuestionnaireId":questionnaireId,
			"date": new Date(),
			"output":[
				["name":urineId, "type":"Integer", "value": ProteinValue.PLUS_TWO.ordinal()],
				["name":urineId+"#NOTE", "type":"String", "value": "This is a test note"]
				]
			]

		controller.response.reset()
		
		QuestionnaireMobileController replyController = new QuestionnaireMobileController()
		
		replyController.request.json = testData
		replyController.upload()
		
		println "Response: " + replyController.response.json.toString()

		assert 'success' == replyController.response.json[0][0].toString()
		
		// Find completed questionnaire, and check that status is Severity.ABOVE_THRESHOLD
		
		origPq = getPatientQuestionnaire("Proteinindhold i urin")
		
		CompletedQuestionnaire cq = CompletedQuestionnaire.findByPatientAndPatientQuestionnaire(pt, origPq)
		assert cq.severity == Severity.GREEN

		cq.getCompletedQuestions().each { //we only put we value/result, assume one iteration
			assert it.note == "This is a test note"
		}
	}

    @Test
    void testUploadCRPResult() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("C-reaktivt Protein (CRP)");

        Patient p = Patient.findByCpr("2512484916")
        p.setThreshold(NumericThreshold.build(type: MeasurementType.findByName(MeasurementTypeName.CRP), alertHigh: 10, alertLow: 0, warningHigh: 8, warningLow: 2))
        p.save(failOnError: true, flush: true)

        controller.params.putAll([id:origPq.id])
        controller.download()

        def questionnaireId = controller.response.json.id

        
        assertNotNull(questionnaireId)


        
        def crpId
        boolean foundNode = false
        controller.response.json.nodes.each { node ->
            if (node.has("CRPNode")) {
                def crpNode = node.get("CRPNode")
                foundNode = true
                crpId = crpNode.CRP.name
            }
        }
        
        if (!foundNode) {
            fail("Expected CRP questionnaire to include CRP, but it did not :-/")
        }
        
        // Build upload json message, containing results..
        def testData = [
            "QuestionnaireId":questionnaireId,
            "date": new Date(), 
            "output":[
                ["name":crpId, "type":"Integer", "value": 15.6]
                ]
            ]

        controller.response.reset()
        
        QuestionnaireMobileController replyController = new QuestionnaireMobileController()
        
        replyController.request.json = testData
        replyController.upload()
        
        println "Response: " + replyController.response.json.toString()

        assert 'success' == replyController.response.json[0][0].toString()
        
        assert "Received 1 variables. Stored 1 questionnaire(s), 1 result(s) and 1 measurement(s)." == controller.response.json[1][0].toString()
    }
    
	@Test
	void testUploadHemoglobinResult() {
		PatientQuestionnaire origPq = getPatientQuestionnaire("Hæmoglobin indhold i blod");

        Patient pt = Patient.findByCpr("2512484916")
        pt.setThreshold(NumericThreshold.build(type: MeasurementType.findByName(MeasurementTypeName.TEMPERATURE), alertHigh: 18, alertLow: 13.8, warningHigh: 17, warningLow: 15))
		pt.save(failOnError:true, flush: true)
		
		controller.params.putAll([id:origPq.id])
		controller.download()
		

		def questionnaireId = controller.response.json.id

		assertNotNull(questionnaireId)

		
		def hemoId
		boolean foundNode = false
		controller.response.json.nodes.each() { node ->
			if (node.has("HaemoglobinDeviceNode")) {
				def hemoNode = node.get("HaemoglobinDeviceNode")
				foundNode = true
				
				assertNotNull hemoNode.text
				
				hemoId = hemoNode.nodeName
			}
		}
		
		if (!foundNode) {
			fail("Expected temperature questionnaire to include HemoglobinDeviceNode, but it did not :-/")
		}
		
		// Build upload json message, containing results..
		def testData = [
			"QuestionnaireId":questionnaireId,
			"date": new Date(),
			"output":[
				["name":hemoId, "type":"Float", "value": 15.6]
            ]
        ]

		controller.response.reset()
		
		QuestionnaireMobileController replyController = new QuestionnaireMobileController()
		
		replyController.request.json = testData
		replyController.upload()
		
		println "Response: " + replyController.response.json.toString()

		assert 'success' == replyController.response.json[0][0].toString()
		
		// Find completed questionnaire, and check that status is Severity.ABOVE_THRESHOLD
		
		origPq = getPatientQuestionnaire("Hæmoglobin indhold i blod")
		
		CompletedQuestionnaire cq = CompletedQuestionnaire.findByPatientAndPatientQuestionnaire(pt, origPq)
		assert cq.severity == Severity.GREEN
	}

    @Test
    void testUploadWeightResultWithOmittedVars() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("Vejning")
        
        controller.params.putAll([id:origPq.id])
        controller.download()
        
        def questionnaireId = controller.response.json.id

        assertNotNull(questionnaireId)

        println "W questionnaire: " + controller.response.json.toString()
        
        def wId

        boolean foundNode = false
        controller.response.json.nodes.each() { node ->
            if (node.has("WeightDeviceNode")) {
                def wNode = node.get("WeightDeviceNode")
                foundNode = true
                
                assertNotNull wNode.weight
                
                wId = wNode.weight.name
            }
        }

        if (!foundNode) {
            fail("Expected weight questionnaire to include WeightDeviceNode, but it did not :-(")
        }
    
        // Build upload json message, containing results..
        def testData = [
            "QuestionnaireId":questionnaireId,
            "date": new Date(),
            "output":[
                ["name":wId, "type":"Float"],
                ["name":"${wId}#CANCEL", "type":"String", "value":true],
                ["name":"${wId}#SEVERITY", "type":"String"]
                ]
            ]

        controller.response.reset()
        
        QuestionnaireMobileController replyController = new QuestionnaireMobileController()
        
        replyController.request.json = testData
        replyController.upload()
        
        println "WResponse: " + replyController.response.json.toString()

        assert 'success' == replyController.response.json[0][0].toString()
    }
    
    @Test
    void testUploadCtgResult() {
        PatientQuestionnaire origPq = getPatientQuestionnaire("CTG")
        
        controller.params.putAll([id: origPq.id]) // Hmmm
        controller.download()

        // [id:5, cpr:2512484916, nodes:[[EndNode:[nodeName:44]], [MonicaDeviceNode:[startTime:45.BP:startTime, toco:45.BP:toco, voltageEnd:45.BP:voltageEnd, voltageStart:45.BP:voltageStart, fhr:45.BP:fhr, mhr:45.BP:mhr, next:44, nodeName:45, signal:45.BP:signal, endTime:45.BP:endTime, qfhr:45.BP:qfhr]], [IONode:[nodeName:46, elements:[[TextViewElement:[text:Så skal der måles CTG!]], [ButtonElement:[text:Næste, next:45, gravity:center]]]]]], name:CTG, startNode:46, output:[[name:45.M:fhr, type:Float[]], [name:45.M:qfhr, type:Integer[]], [name:45.M:startTime, type:String], [name:45.M:signal, type:Integer[]], [name:45.M:toco, type:Float[]], [name:45.M:voltageStart, type:Float], [name:45.M:endTime, type:String], [name:45.M:mhr, type:Float[]], [name:45.M:voltageEnd, type:Float]], endNode:44, version:0.2]

        def questionnaireId = controller.response.json.id
        
        assertNotNull(questionnaireId)

        def startTimeId
        def endTimeId
        def voltageStartId
        def voltageEndId
        def tocoId
        def fhrId
        def mhrId
        def signalId
        def signalToNoiseId
        def fetalHeightId
        def qfhrId
        def deviceId

        boolean foundMonica = false
        controller.response.json.nodes.each() { node ->
            if (node.has("MonicaDeviceNode")) {
                def monicaNode = node.get("MonicaDeviceNode")
                foundMonica = true

                assertNotNull monicaNode.startTime
                assertNotNull monicaNode.endTime
                assertNotNull monicaNode.voltageStart
                assertNotNull monicaNode.voltageEnd
                assertNotNull monicaNode.toco
                assertNotNull monicaNode.fhr
                assertNotNull monicaNode.mhr
                assertNotNull monicaNode.signal
                assertNotNull monicaNode.qfhr
                assertNotNull monicaNode.signalToNoise
                assertNotNull monicaNode.fetalHeight
                assertNotNull monicaNode.deviceId

                startTimeId = monicaNode.startTime.name
                endTimeId = monicaNode.endTime.name
                voltageStartId = monicaNode.voltageStart.name
                voltageEndId = monicaNode.voltageEnd.name
                tocoId = monicaNode.toco.name
                fhrId = monicaNode.fhr.name
                mhrId = monicaNode.mhr.name
                qfhrId = monicaNode.qfhr.name
                signalId = monicaNode.signal.name
                signalToNoiseId = monicaNode.signalToNoise.name
                fetalHeightId = monicaNode.fetalHeight.name
                deviceId = monicaNode.deviceId.name
            }
        }

        if (!foundMonica) {
            fail("Expected ctq questionnaire to include MonicaDeviceNode, but it did not :-(")
        }

        float[] toco = [1.0, 2.0, 3.8, 4.9, 10.0]
        float[] fhr = [120.0, 122.0, 123.8, 124.9, 120.0]
        float[] mhr = [130.0, 139.0, 140.8, 145.9, 142.0]
        int[] qfhr = [0,0,3,3,3]
        int[] signal = [10,30]
        int[] signalToNoise = [1, 2, 3]
        int[] fetalHeight = [10, 11, 10]

        // Build upload json message, containing results..
        def testData = [
            "QuestionnaireId":questionnaireId,
            "date": new Date(),
            "output":[
                ["name":startTimeId, "type":"String", "value": "2012-05-07T17:14:17Z"],
                ["name":endTimeId, "type":"String", "value": "2012-05-07T17:15:17Z"], // Ét minuts målinger..
                ["name":voltageStartId, "type":"Float", "value": 220.0],
                ["name":voltageEndId, "type":"Float", "value": 240.0],
                ["name":tocoId, "type":"Float[]", "value": toco],
                ["name":fhrId, "type":"Float[]", "value": fhr],
                ["name":mhrId, "type":"Float[]", "value": mhr],
                ["name":qfhrId, "type":"Float[]", "value": qfhr],
                ["name":signalId, "type":"Integer[]", "value": signal],
                ["name":signalToNoiseId, type:"Integer[]", "value": signalToNoise],
                ["name":fetalHeightId, type:"Integer[]", "value": fetalHeight],
                ["name":deviceId, type:"String", "value": "1234"],
            ]
        ]

        controller.response.reset()

        QuestionnaireMobileController replyController = new QuestionnaireMobileController()

        replyController.request.json = testData
        replyController.upload()

        println "XXResponse: " + replyController.response.json.toString()

        assert 'success' == replyController.response.json[0][0].toString() // Missing cpr

        Measurement newestMeasurement = Measurement.all.max { it.id }
        assert newestMeasurement.signals == '[10,30]'
        assert newestMeasurement.fetalHeight == '[10,11,10]'
        assert newestMeasurement.signalToNoise == '[1,2,3]'
        assert newestMeasurement.deviceIdentification == "1234"
    }

    @Test
    void testUploadValidation() {
        def p = Patient.findByCpr("2512484916")

        PatientQuestionnaire pq = getPatientQuestionnaire("JSON test")

        controller.response.reset()
        
        def testData = [
                         "PatientId":p.id,
                         "QuestionnaireId":pq.id,
                         "date": new Date(),
                         "results":[
                             ["name":"2test", "type":"Float", "value": "123.1"],
                             ["name":"2.test#severity", "type":"String", "value": "RED"],
                             ["name":"3.test", "type":String, "value": ""],
                             ["name":"3.test#CANCEL", "type":Boolean, "value": true],
                             ]
                         ]
        
        controller.request.json = testData
        controller.upload()
        
        println "XXResponse: " + controller.response.json.toString()
        println "Response: " + controller.response.json[0][0].toString()

        assert 'failure' == controller.response.json[0][0].toString() // Missing cpr
    }
    
    
    @Test
    void testUploadYesNoResult() {
        def p = Patient.findByCpr("2512484916")
        
        PatientQuestionnaire pq = getPatientQuestionnaire("JaNej")
        
        // Download schema
        controller.params.putAll([id: pq.id]) 
        controller.download()

        def assignmentNodeName
        def assignmentNodeSeverityName
                
        println "Got resp: " + controller.response.json
        controller.response.json.nodes.each() { node ->
            
            if (node.has("AssignmentNode")) {
                def assignmentNode = node.get("AssignmentNode")
                
                def field = assignmentNode.variable.name
                if (field.contains("SEVERITY")) {
                    assignmentNodeSeverityName = assignmentNode.variable.name
                } else {
                    assignmentNodeName = assignmentNode.variable.name
                }
            }
        }
        
        def testData = [
                         "PatientId":p.id,
                         "QuestionnaireId":pq.id,
                         "date": new Date(),
                         "output":[
                             ["name":assignmentNodeName, "type":"Boolean", "value": false],
                             ["name":assignmentNodeSeverityName, "type":"String", "value": "RED"],
                             ]
                         ]
        controller.response.reset()
        controller.request.json = testData
        controller.upload()
        
        println "XXResponse: " + controller.response.json.toString()
        println "Response: " + controller.response.json[0][0].toString()

        assert 'success' == controller.response.json[0][0].toString() 
        assert "Received 2 variables. Stored 1 questionnaire(s), 1 result(s) and 0 measurement(s)." == controller.response.json[1][0].toString()
    }

    @Test
    void testUploadError() {
        PatientQuestionnaire pq = getPatientQuestionnaire("JSON test")
        
        def testData = [ "QuestionnaireId":pq.id,
                         "date": new Date(),
                         "results":[
                             ["name":"3.test#CANCEL", "type":Boolean, "value": true],
                             ]
                         ]
        
        controller.request.json = testData
        controller.upload()
        
        println "XXResponse: " + controller.response.json.toString()
        println "Response: " + controller.response.json[0][0].toString()

        assert 'failure' == controller.response.json[0][0].toString()
       // assert controller.response.json.toString() == """[["failure"],["A required parameter is missing. Received: cpr:2512484916, QuestionnaireId:1,version:null."]]"""
    }

    PatientQuestionnaire getPatientQuestionnaire(String name) {

        QuestionnaireHeader questionnaireHeader = QuestionnaireHeader.findByName(name)
        Questionnaire questionnaire = questionnaireHeader.activeQuestionnaire
        PatientQuestionnaire pq = PatientQuestionnaire.findByTemplateQuestionnaire(questionnaire)
        pq.refresh()
        pq
    }
}
