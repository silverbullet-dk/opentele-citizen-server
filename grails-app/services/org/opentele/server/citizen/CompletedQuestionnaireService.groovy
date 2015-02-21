package org.opentele.server.citizen

import org.opentele.server.model.*
import org.opentele.server.model.patientquestionnaire.*
import org.opentele.server.model.questionnaire.MeasurementNode
import org.opentele.server.core.model.types.*
import org.opentele.server.provider.util.AboveAlertThresholdPredicate
import org.opentele.server.provider.util.AboveWarningThresholdPredicate
import org.opentele.server.core.util.ISO8601DateParser

import java.text.ParseException


/**
 * Service for handling results received from patients
 */
class CompletedQuestionnaireService {

    def springSecurityService
    def patientOverviewMaintenanceService
    def continuousBloodSugarMeasurementService

    static transactional = false

    // For use when testing
    static final def SHOULD_FAIL_ON_ERROR = true
    
    def handleResults(Patient patient, def patientQuestionnaireId, Date date, def jsonOutputs) {
        Map resultHolder = [:]
        
        CompletedQuestionnaire.withTransaction { status ->
            
            resultHolder.completedQuestionnaireCount = 0
            resultHolder.resultCount = 0
            resultHolder.measurementCount = 0
            resultHolder.outputVarCount = 0
            resultHolder.results = [["success"]]
            resultHolder.errors = []
            resultHolder.hasErrors = false
    
            PatientQuestionnaire patientQuestionnaire = PatientQuestionnaire.get(patientQuestionnaireId)
            if (!patientQuestionnaire) {
                appendErrors(resultHolder, "Questionnaire with id: ${patientQuestionnaireId} not found.")
            } else if (!patient) {
                appendErrors(resultHolder, "Missing patient argument.")
            } else if (!date) {
                appendErrors(resultHolder, "Missing date argument.")
            } else {
                parseAndHandleResults(patient, resultHolder, patientQuestionnaireId, date, patientQuestionnaire, jsonOutputs);
            }
            
            if (resultHolder.hasErrors) {
                resultHolder.results = [["failure"], resultHolder.errors]

                // Rollback transaction
                status.setRollbackOnly()
            } else {
                resultHolder.results << ["Received ${resultHolder.outputVarCount} variables. Stored ${resultHolder.completedQuestionnaireCount} questionnaire(s), ${resultHolder.resultCount} result(s) and ${resultHolder.measurementCount} measurement(s)."]
            }

            patientOverviewMaintenanceService.updateOverviewFor(patient);
        }
        resultHolder.results
    }
    
    def appendErrors(Map resultHolder, def errs) {
        resultHolder.hasErrors = true
        resultHolder.errors <<  errs
    }
    
    private def parseAndHandleResults(Patient patient, Map resultHolder, def questionnaireId, Date date, PatientQuestionnaire thePq, def jsonOutputs) {
        // Create completed questionnaire
        CompletedQuestionnaire completed = new CompletedQuestionnaire(createdBy: "WS", modifiedBy: "WS", createdDate: new Date(), modifiedDate: new Date())
        completed.setReceivedDate(new Date())
        completed.setPatientQuestionnaire(thePq)
        completed.setQuestionnaireHeader(thePq.getTemplateQuestionnaire().getQuestionnaireHeader())
        completed.setPatient(patient)
        completed.setUploadDate(date)

        // Set to the default severity. The severity is later updated if some of the nodes are severe.
        def defaultSeverity = completed.questionnaireHeader.requiresManualInspection ? Severity.ORANGE : Severity.GREEN
        completed.setSeverity(defaultSeverity)

        completed.save(failOnError: SHOULD_FAIL_ON_ERROR)
        if (completed.hasErrors()) {
            appendErrors(resultHolder, completed.errors)
        }

        if (!jsonOutputs) {
            appendErrors(resultHolder, "Received reply not containing any results..")
        } else {
            def outputMap = parseJsonOutputs(jsonOutputs)
            outputMap.each { op ->
                resultHolder.outputVarCount += op.value?.size()
            }

            for (def output : outputMap) {
                def resultName = output.key
                def resultList = output.value

                def nameTokens = resultName.tokenize(".")
                def id = nameTokens[0]
                def restString = nameTokens[1]
                def varName = restString?.tokenize("#")?.get(0)
                
                if (varName) {
                    PatientQuestionnaireNode node = PatientQuestionnaireNode.get(new Long(id))
                    
                    if (!validateNode(node, resultHolder, questionnaireId)) {
                        break;
                    }
                    node.refresh() // Apparently necessary to get proper subclass
                    
                    if (node.instanceOf(PatientChoiceNode)) {
                        handlePatientChoiceNode(varName, resultList, resultHolder, completed, node, date)
                    } else if (node.instanceOf(PatientInputNode)) {
                        handlePatientInputNode(varName, resultList, resultHolder, completed, node, date)
                    } else if (node.instanceOf(PatientMeasurementNode)) {
                        handlePatientMeasurementNode(varName, resultList, resultHolder, completed, node, date, patient)
                    } else {
                        log.error("Variable with name ${resultName} does not match a valid inputnode. ")
                        appendErrors(resultHolder, "Variable with name ${resultName} does not match a valid inputnode. ")
                    }
                }
            }
            completed.refresh()
            
            completed.completedQuestions.each () { res ->
                if (res.severity && res.severity > completed.severity) {
                    completed.severity = res.severity
                }
            }
            completed.save(failOnError: SHOULD_FAIL_ON_ERROR)
            if (completed.hasErrors()) {
                appendErrors(resultHolder, completed.errors)
            } else {
                resultHolder.completedQuestionnaireCount++
            }
        }
    }

	private parseJsonOutputs(jsonOutputs) {
		
        def outputs = [:]
        
        jsonOutputs.each { result ->
			if (result?.value != null) {
				String rezName = result.name

				// parse result.name string
				def tokens = rezName.tokenize("#")

				def name = tokens[0]
				def resultList
				if (!outputs.get(name)) {
					resultList = []
					resultList.add(result)
					outputs.put(name, resultList)
				} else {
					resultList = outputs.get(name)
					resultList.add(result)
				}
			}
		}
        outputs
	}

    private void handlePatientMeasurementNode(def variableName, def resultList, Map resultHolder, CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Patient patient) {
        if (MeterTypeName.WEIGHT == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "WEIGHT", resultHolder)) {
                return
            }
            handleWeightNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.TEMPERATURE == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "TEMPERATURE", resultHolder)) {
                return
            }
            handleTemperatureNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.BLOOD_PRESSURE_PULSE == node.meterType.getName()) {
            if (!variableName.startsWith("BP")) {
                log.error("Identifier for bloodpressure node should start with BP, but it did not. Identifier was: ${variableName}")
                appendErrors(resultHolder, "Identifier for bloodpressure node should start with BP, but it did not. Identifier was: ${variableName}")
                return
            }
            handleBloodPressurePulseNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.SATURATION == node.meterType.getName() || MeterTypeName.SATURATION_W_OUT_PULSE == node.meterType.getName()) {
            if (!variableName.startsWith("SAT")) {
                log.error("Identifier for saturation node should start with SAT, but it did not. Identifier was: ${variableName}")
                appendErrors(resultHolder, "Identifier for saturation node should start with SAT, but it did not. Identifier was: ${variableName}")
                return
            }
            handleSaturationNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.URINE == node.meterType.getName()) {
            if(!isValidVariableName(variableName, "URINE", resultHolder)) {
                return
            }
            handleUrineNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.URINE_GLUCOSE == node.meterType.getName()) {
            if(!isValidVariableName(variableName, "URINE_GLUCOSE", resultHolder)) {
                return
            }
            handleUrineGlucoseNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.CTG == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "CTG", resultHolder)) {
                return
            }
            handleCtgNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.HEMOGLOBIN == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "HEMOGLOBIN", resultHolder)) {
                return
            }
            handleHemoglobinNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.CRP == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "CRP", resultHolder)) {
                return
            }
            handleCrpNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.BLOODSUGAR == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "BS", resultHolder)) {
                return
            }
            handleBloodSugarNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "CGM", resultHolder)) {
                return
            }
            handleContinuousBloodSugarMeasurementNode(completed, node, date, resultHolder, resultList, patient)
        } else if (MeterTypeName.LUNG_FUNCTION == node.meterType.getName()) {
            if (!isValidVariableName(variableName, "LF", resultHolder)) {
                return
            }
            handleLungFunctionNode(completed, node, date, resultHolder, resultList, patient)
        } else {
            appendErrors(resultHolder, "Unsupported measurement type ${node.meterType.getName()} for variable ${variableName}.")
        }
    }

    private void handleCtgNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.CTG, completed, node, date, resultHolder)
        Measurement ctg = null
                    
        // Handle different result objects
        for (def result : resultList) {
            if (result.name.count('#') < 2) { // one of several possible primary result objects
                
                if (!ctg) { // First loop
                    ctg = createMeasurement(patient, MeasurementTypeName.CTG, measurementNodeResult, Unit.CTG, date)
                }
                
                if (result.value != null) {
                    if (containsString(result.name, MeasurementNode.VOLTAGESTART_VAR)) {
                        ctg.setVoltageStart(getDoubleVal(result.value))
                    } else if (containsString(result.name, MeasurementNode.VOLTAGEEND_VAR)) {
                        ctg.setVoltageEnd(getDoubleVal(result.value))
                    } else if (containsString(result.name, MeasurementNode.STARTTIME_VAR)) {
                        try {
                            Date d = ISO8601DateParser.parse(result.value)
                            ctg.setStartTime(d)
                        } catch (ParseException pax) {
                             log.error("Could not parse CTG starttime: ${result.value}")
                             appendErrors(resultHolder, "Could not parse CTG starttime: ${result.value}")
                             break
                        }
                    } else if (containsString(result.name, MeasurementNode.ENDTIME_VAR)) {
                        try {
                            Date d = ISO8601DateParser.parse(result.value)
                            ctg.setEndTime(d)
                        } catch (ParseException pax) {
                             log.error("Could not parse CTG endtime: ${result.value}")
                             appendErrors(resultHolder, "Could not parse CTG endtime: ${result.value}")
                             break
                        }
                    } else if (containsString(result.name, MeasurementNode.MHR_VAR)) {
                        ctg.setMhr(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.FHR_VAR)) {
                        ctg.setFhr(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.QFHR_VAR)) {
                        ctg.setQfhr(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.TOCO_VAR)) {
                        ctg.setToco(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.FETAL_HEIGHT_VAR)) {
                        ctg.setFetalHeight(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.SIGNAL_TO_NOISE_VAR)) {
                        ctg.setSignalToNoise(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.SIGNAL_VAR)) {
                        // Since "SIGNAL" is a substring of "SIGNAL_TO_NOISE", this case must be after the SIGNAL_TO_NOISE_VAR
                        // case above. (There's a test for that, but it's not that elegant...)
                        ctg.setSignals(result.value.toString())
                    } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR)) {
                        ctg.setDeviceIdentification(result.value.toString())
                    } else {
                        log.error("Received unknown CTG variable: ${result.name}")
                        appendErrors(resultHolder, "Received unknown CTG variable: ${result.name}")
                        break
                    }
                }
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }
        
        if (!measurementNodeResult.wasOmitted) {
            // Create measurement
            if (ctg) {
                ctg.save(failOnError: SHOULD_FAIL_ON_ERROR)
                if (ctg.hasErrors()) {
                    appendErrors(resultHolder, ctg.errors)
                } else {
                    resultHolder.measurementCount++
                }
            }
        }
    }

	private void handleUrineNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
		MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.URINE, completed, node, date, resultHolder)
		Measurement measurement = null
		
		for (def result : results) {
			if (isPrimaryResultObject(result)) {
				measurement = createMeasurement(patient, MeasurementTypeName.URINE, measurementNodeResult, Unit.PROTEIN, date)
				if (!ProteinValue.hasOrdinal(result.value)) {
                    log.error("Received unknown UrineMeasurement value: ${result.value}")
                    appendErrors(resultHolder, "Received unknown UrineMeasurement value: ${result.value}")
                    resultHolder.hasErrors = true
                    return
                }

                measurement.setProtein(ProteinValue.fromOrdinal(result.value))
			} else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
		}

        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
	}

    private void handleUrineGlucoseNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.URINE_GLUCOSE, completed, node, date, resultHolder)
        Measurement measurement = null

        for (def result : results) {
            if (isPrimaryResultObject(result)) {
                measurement = createMeasurement(patient, MeasurementTypeName.URINE_GLUCOSE, measurementNodeResult, Unit.GLUCOSE, date)
                if (!GlucoseInUrineValue.hasOrdinal(result.value)) {
                    log.error("Received unknown Glucose in urine value: ${result.value}")
                    appendErrors(resultHolder, "Received unknown Glusose in urine value: ${result.value}")
                    resultHolder.hasErrors = true
                    return
                }

                measurement.setGlucoseInUrine(GlucoseInUrineValue.fromOrdinal(result.value))
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }

        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
    }

	
	private void handleHemoglobinNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
		MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.HEMOGLOBIN, completed, node, date, resultHolder)
		Measurement measurement = null
					
		for (def result : results) {
			if (isPrimaryResultObject(result)) {
				measurement = createMeasurement(patient, MeasurementTypeName.HEMOGLOBIN, measurementNodeResult, Unit.GRAM_DL, date)
				if (result.value != null) {
					measurement.setValue(getDoubleVal(result.value))
				}
			} else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
			}
		}

        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
	}
	
    private void handleCrpNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.CRP, completed, node, date, resultHolder)
        Measurement measurement = null
                    
        for (def result : results) {
            if (isPrimaryResultObject(result)) {
                measurement = createMeasurement(patient, MeasurementTypeName.CRP, measurementNodeResult, Unit.MGRAM_L, date)
                if (result.value != null) {
                    measurement.setValue(getDoubleVal(result.value))
                }
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }

        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
    }
    
	private void handleTemperatureNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
		MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.TEMPERATURE, completed, node, date, resultHolder)
		Measurement measurement = null
					
		for (def result : results) {
			if (isPrimaryResultObject(result)) {
				measurement = createMeasurement(patient, MeasurementTypeName.TEMPERATURE, measurementNodeResult, Unit.DEGREES_CELSIUS, date)
				if (result.value != null) {
					measurement.setValue(getDoubleVal(result.value))
				}
			} else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
		}

        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
	}
	
    private void handleWeightNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def results, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.WEIGHT, completed, node, date, resultHolder)
        Measurement measurement = null

        String deviceId = null

        for (def result : results) {
            if (isPrimaryResultObject(result)) {
                measurement = createMeasurement(patient, MeasurementTypeName.WEIGHT, measurementNodeResult, Unit.KILO, date)

                if (result.value != null) {
                    measurement.setValue(getDoubleVal(result.value))
                }
            } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR) && result.value != null) {
                deviceId = result.value

            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }
        if (!measurementNodeResult.wasOmitted) {
            if (measurement != null) {
                measurement.setDeviceIdentification(deviceId)
            }
        }
        setSeverityAndOmission(measurementNodeResult, measurement, resultHolder)
    }

    private void handleBloodPressurePulseNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.BLOOD_PRESSURE, completed, node, date, resultHolder)
        Measurement bp = null
        Measurement pulse = null
        String deviceId = null

        // Handle different result objects
        for (def result : resultList) {
            // If only one occurrence
            if (result.name.count('#') < 2) { // one of several possible primary (systolic,diastolic,pulse) result objects
                
                if (containsString(result.name, MeasurementNode.DIASTOLIC_VAR) || containsString(result.name, MeasurementNode.SYSTOLIC_VAR) || containsString(result.name, MeasurementNode.MEAN_ARTERIAL_PRESSURE_VAR)) {
                    if (!bp) { // First loop
                        bp = createMeasurement(patient, MeasurementTypeName.BLOOD_PRESSURE, measurementNodeResult, Unit.MMHG, date)
                    }
                    if (result.value != null) {
                        if (containsString(result.name, MeasurementNode.DIASTOLIC_VAR)) {
                            bp.setDiastolic(getDoubleVal(result.value))
                        }
                        if (containsString(result.name, MeasurementNode.SYSTOLIC_VAR)) {
                            bp.setSystolic(getDoubleVal(result.value))
                        }
                        if (containsString(result.name, MeasurementNode.MEAN_ARTERIAL_PRESSURE_VAR)) {
                            bp.setMeanArterialPressure(getDoubleVal(result.value))
                        }
                    }
                } else if (containsString(result.name, MeasurementNode.PULSE_VAR)) {
                    pulse = createMeasurement(patient, MeasurementTypeName.PULSE, measurementNodeResult, Unit.BPM, date)
                    if (result.value != null) {
                        if (containsString(result.name, MeasurementNode.PULSE_VAR)) {
                            pulse.setValue(getDoubleVal(result.value))
                        }
                    }
                } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR) && result.value != null) {
                    deviceId = result.value
                }
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }
        
        if (!measurementNodeResult.wasOmitted) {
            if (bp != null) {
                bp.setDeviceIdentification(deviceId)
                setSeverityOn(measurementNodeResult, bp)
                saveResultAndMeasurement(measurementNodeResult, bp, resultHolder)
            }

            if (pulse != null) {
                pulse.setDeviceIdentification(deviceId)
                setSeverityOn(measurementNodeResult, pulse)
                saveResultAndMeasurement(measurementNodeResult, pulse, resultHolder)
            }
        }
    }
    
    private void handleSaturationNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.SATURATION, completed, node, date, resultHolder)
        Measurement saturation = null
        Measurement pulse = null

        String deviceId = null

        // Handle different result objects
        for (def result : resultList) { // Necessary for sat?
            // If only one occurence
            if (result.name.count('#') < 2) { // one of several possible primary (sat,pulse) result objects
                if (containsString(result.name, MeasurementNode.SATURATION_VAR)) {
                    saturation = createMeasurement(patient, MeasurementTypeName.SATURATION, measurementNodeResult, Unit.PERCENTAGE, date)
                    if (result.value != null && containsString(result.name, MeasurementNode.SATURATION_VAR)) {
                        saturation.setValue(getDoubleVal(result.value))
                    }
                } else if (containsString(result.name, MeasurementNode.PULSE_VAR)) {
                    pulse = createMeasurement(patient, MeasurementTypeName.PULSE, measurementNodeResult, Unit.BPM, date)
                    if (result.value != null && containsString(result.name, MeasurementNode.PULSE_VAR)) {
                        pulse.setValue(getDoubleVal(result.value))
                    }
                } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR) && result.value != null) {
                    deviceId = result.value
                }
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }
        
        if (!measurementNodeResult.wasOmitted) {
            if (saturation) {
                saturation.setDeviceIdentification(deviceId)
                setSeverityOn(measurementNodeResult, saturation)
                saveResultAndMeasurement(measurementNodeResult, saturation, resultHolder)
            }
            if (pulse) {
                pulse.setDeviceIdentification(deviceId)
                setSeverityOn(measurementNodeResult, pulse)
                saveResultAndMeasurement(measurementNodeResult, pulse, resultHolder)
            }
        }
    }

    private void handleBloodSugarNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.BLOODSUGAR, completed, node, date, resultHolder)
        def bloodSugarMeasurements = []

        // Handle different result objects
        for (def result : resultList) {
            def tokens = result.name.tokenize("#")
            def objectType = tokens?.last()

            if (result.type == "BloodSugarMeasurements") {
                for (def measurementFromDevice: result.value.measurements) {
                    def measurementTime = ISO8601DateParser.parse(measurementFromDevice.timeOfMeasurement)

                    if (Measurement.findByPatientAndMeasurementTypeAndTime(patient, MeasurementType.findByName(MeasurementTypeName.BLOODSUGAR.value()),  measurementTime)) {
                        log.debug("Skipped!")
                        continue; //Measurement already created, don't recreate.
                    }

                    def measurement = createMeasurement(patient, MeasurementTypeName.BLOODSUGAR, measurementNodeResult, Unit.MMOL_L, measurementTime)

                    measurement.setValue(measurementFromDevice.result)
                    measurement.setOtherInformation(measurementFromDevice.otherInformation)
                    measurement.setIsBeforeMeal(measurementFromDevice.isBeforeMeal)
                    measurement.setHasTemperatureWarning(measurementFromDevice.hasTemperatureWarning)
                    measurement.setIsControlMeasurement(measurementFromDevice.isControlMeasurement)
                    measurement.setIsAfterMeal(measurementFromDevice.isAfterMeal)
                    measurement.setIsOutOfBounds(measurementFromDevice.isOutOfBounds)
                    measurement.setTime(measurementTime)

                    bloodSugarMeasurements.add(measurement)
                }

                bloodSugarMeasurements.each {Measurement measurement ->
                    measurement.deviceIdentification = result.value.serialNumber
                }
            } else if (objectType.equalsIgnoreCase("DEVICE_ID")) {
                //Ignored
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }

        if (!measurementNodeResult.wasOmitted) {
            for (Measurement measurement: bloodSugarMeasurements) {
                setSeverityOn(measurementNodeResult, measurement)
                saveResultAndMeasurement(measurementNodeResult, measurement, resultHolder)
            }
        }
    }

    private void handleContinuousBloodSugarMeasurementNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT, completed, node, date, resultHolder)
        Measurement measurement

        // Handle different result objects
        for (def result : resultList) {
            if (result.type == "ContinuousBloodSugarEvents") {
                def transferTime = ISO8601DateParser.parse(result.value.transferTime)
                measurement = createMeasurement(patient, MeasurementTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT, measurementNodeResult, Unit.MMOL_L, transferTime)
                def events = continuousBloodSugarMeasurementService.parseEvents(result.value.events)

                events.each {
                    measurement.addToContinuousBloodSugarEvents(it)
                }


            } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR) && result.value != null) {
                measurement.deviceIdentification = result.value
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }

        if (!measurementNodeResult.wasOmitted && measurement) {
            saveResultAndMeasurement(measurementNodeResult, measurement, resultHolder)
        }
    }

    private void handleLungFunctionNode(CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder, def resultList, Patient patient) {
        MeasurementNodeResult measurementNodeResult = createMeasurementNodeResult(MeasurementTypeName.SATURATION, completed, node, date, resultHolder)
        Measurement measurement = createMeasurement(patient, MeasurementTypeName.LUNG_FUNCTION, measurementNodeResult, Unit.LITER, new Date()) //Fetch date from result before

        // Handle different result objects
        for (def result : resultList) {

            if (result.name.count('#') < 2) { // one of several possible primary result objects
                if (containsString(result.name, MeasurementNode.FEV1_FEV6_RATIO_VAR) && result.value != null) {
                    measurement.setFev1Fev6Ratio(getDoubleVal(result.value))
                } else if (containsString(result.name, MeasurementNode.FEV1_VAR) && result.value != null) {
                    measurement.setValue(getDoubleVal(result.value))
                } else if (containsString(result.name, MeasurementNode.FEV6_VAR) && result.value != null) {
                    measurement.setFev6(getDoubleVal(result.value))
                } else if (containsString(result.name, MeasurementNode.FEF2575_VAR) && result.value != null) {
                    measurement.setFef2575(getDoubleVal(result.value))
                } else if (containsString(result.name, MeasurementNode.FEV_SOFTWARE_VERSION) && result.value != null) {
                    measurement.setFevSoftwareVersion(result.value as int)
                } else if (containsString(result.name, MeasurementNode.FEV_GOOD_TEST_VAR) && result.value != null) {
                    measurement.setIsGoodTest(result.value as boolean)
                } else if (containsString(result.name, MeasurementNode.DEVICE_ID_VAR) && result.value != null) {
                    measurement.setDeviceIdentification(result.value)
                }
            } else if (handleSecondaryFields(result, measurementNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                break
            }
        }

        if (!measurementNodeResult.wasOmitted) {
            if (measurement) {
                setSeverityOn(measurementNodeResult, measurement)
                saveResultAndMeasurement(measurementNodeResult, measurement, resultHolder)
            }
        }
    }

    private void handlePatientChoiceNode(def variableName, def results, Map resultHolder, CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date) {
        if (!isValidVariableName(variableName, "DECISION", resultHolder)) {
            return
        }

        ChoiceNodeResult choiceNodeResult = new ChoiceNodeResult(createdBy: "WS", modifiedBy: "WS", createdDate: new Date(), modifiedDate: new Date())
        choiceNodeResult.setCompletedQuestionnaire(completed)
        choiceNodeResult.setPatientQuestionnaireNode(node)
        choiceNodeResult.setCompletionTime(date)

        // Handle different resultobjects
        for (def result : results) {
            if (isPrimaryResultObject(result)) {
                choiceNodeResult.setResult(result.value)
                choiceNodeResult.setInput("Not impl yet")
            } else if (handleSecondaryFields(result, choiceNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                return
            }
        }
        saveNodeResult(choiceNodeResult, resultHolder)
    }

    private void handlePatientInputNode(def variableName, def results, Map resultHolder, CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date) {
        if (!isValidVariableName(variableName, "FIELD", resultHolder)) {
            return
        }

        InputNodeResult inputNodeResult = new InputNodeResult(createdBy: "WS", modifiedBy: "WS", createdDate: new Date(), modifiedDate: new Date())

        inputNodeResult.setCompletedQuestionnaire(completed)
        inputNodeResult.setPatientQuestionnaireNode(node)
        inputNodeResult.setCompletionTime(date)

        // Handle different resultobjects
        for (def result : results) {
            if (isPrimaryResultObject(result)) {
                inputNodeResult.setResult(result.value)
            } else if (handleSecondaryFields(result, inputNodeResult, resultHolder.errors)) {
                resultHolder.hasErrors = true
                return
            }
        }
        saveNodeResult(inputNodeResult, resultHolder)
    }

    private void setSeverityAndOmission(MeasurementNodeResult measurementNodeResult, Measurement measurement, Map resultHolder) {
        if (!measurementNodeResult.wasOmitted) {
            setSeverityOn(measurementNodeResult, measurement)
            saveResultAndMeasurement(measurementNodeResult, measurement, resultHolder)
        } else if (measurement) {
            measurement.setMeasurementNodeResult(null)
            measurement.discard()
        }
    }

    private void setSeverityOn(MeasurementNodeResult measurementNodeResult, Measurement measurement) {
        if (AboveAlertThresholdPredicate.isTrueFor(measurement)) {
            measurementNodeResult.severity = Severity.RED
        } else if (measurementNodeResult.severity < Severity.YELLOW && AboveWarningThresholdPredicate.isTrueFor(measurement)) {
            measurementNodeResult.severity = Severity.YELLOW
        }
    }

    private def saveResultAndMeasurement(NodeResult nodeResult, Measurement measurement, Map resultHolder) {
        nodeResult.save(failOnError: SHOULD_FAIL_ON_ERROR)
        measurement.save(failOnError: SHOULD_FAIL_ON_ERROR)
        
        if (measurement.hasErrors() || nodeResult.hasErrors()) {
            resultHolder.hasErrors = true
            if (measurement.hasErrors()) {
                resultHolder.errors <<  measurement.errors
            }
            if (nodeResult.hasErrors()) {
                resultHolder.errors <<  nodeResult.errors
            }
        } else {
            resultHolder.measurementCount++
        }
    }
    
    private boolean containsString(def name, def token) {
        name.contains(token)
    }

    private boolean isValidVariableName(def variableName, String name, Map resultHolder) {
        if (!name.equals(variableName)) {
            log.error("Identifier for node should be ${name}, but was: ${variableName}")
            appendErrors(resultHolder, "Identifier for node should be ${name}, but was: ${variableName}")
            false
        } else {
            true
        }
    }

    private Measurement createMeasurement(Patient patient, MeasurementTypeName type, MeasurementNodeResult measurementNodeResult, Unit unit, Date date) {
        Measurement measurement = new Measurement(createdBy: "WS", modifiedBy: "WS", createdDate: new Date(), modifiedDate: new Date())
        measurement.setPatient(patient)
        measurement.setMeasurementType(MeasurementType.findByName(type.value()))
        measurement.setMeasurementNodeResult(measurementNodeResult)
        measurement.setUnit(unit)
        measurement.setTime(date)
        measurement
    }
    
    private MeasurementNodeResult createMeasurementNodeResult(MeasurementTypeName type, CompletedQuestionnaire completed, PatientQuestionnaireNode node, Date date, Map resultHolder) {
        MeasurementNodeResult measurementNodeResult = new MeasurementNodeResult(createdBy: "WS", modifiedBy: "WS", createdDate: new Date(), modifiedDate: new Date())
        measurementNodeResult.setMeasurementType(MeasurementType.findByName(type.value()))
        measurementNodeResult.setCompletedQuestionnaire(completed)
        measurementNodeResult.setPatientQuestionnaireNode(node)
        measurementNodeResult.setCompletionTime(date)
        saveNodeResult(measurementNodeResult, resultHolder)
        measurementNodeResult
    }

    boolean validateNode(PatientQuestionnaireNode node, Map resultHolder, def questionnaireId) {
        if (node.getQuestionnaire()?.id != questionnaireId) {
            log.error("Node with id: ${id} from questionnaire ${node.getQuestionnaire()?.id} does not match questionnaire with id: ${questionnaireId}")
            appendErrors(resultHolder, "Node with id: ${id} from questionnaire ${node.getQuestionnaire()?.id} does not match questionnaire with id: ${questionnaireId}")
            return false
        }
        if (!node) {
            log.error("Did not find node with nodeId: ${id}")
            appendErrors(resultHolder, "Questionnaire node with id: ${id} not found")
            return false
        }
        if (node.getQuestionnaire()?.id != questionnaireId) {
            log.error("Did not find node with id: ${id} matching questionnaire with ID: ${questionnaireId}")
            appendErrors(resultHolder, "Did not find node with id: ${id} matching questionnaire with ID: ${questionnaireId}")
            return false
        }
        true
    }
    
    def getDoubleVal(def input) {
         if (input in String) {
            if (input.isNumber()) {
                return new Double(input)
            }
         } else {
            return input
         }
         null
    }

    private void saveNodeResult(NodeResult nodeResult, def resultHolder) {
        nodeResult.save(failOnError: SHOULD_FAIL_ON_ERROR)
        if (nodeResult.hasErrors()) {
            appendErrors(resultHolder, nodeResult.errors)
        } else {
            resultHolder.resultCount++
        }
    }

    private boolean isPrimaryResultObject(def result) {
        !result.name.contains('#')
    }

    private boolean handleSecondaryFields(def variable, NodeResult nodeResult, def errors) {
        boolean hasErrors = false
        def tokens = variable.name.tokenize("#")
        def objectType = tokens?.last()
        if ("SEVERITY".equalsIgnoreCase(objectType)) {
            Severity nodeSeverity = Severity.find(variable.value)
            if (nodeSeverity) {
                // Above/below threshold is worse than anything else, so cannot be overruled..
                if (nodeResult?.severity != Severity.ABOVE_THRESHOLD && nodeResult?.severity != Severity.BELOW_THRESHOLD) {
                    nodeResult.setSeverity(nodeSeverity)
                }
            } else {
                log.error("Unknown severity ${variable.value} encountered for variable: ${variable.name}")
                hasErrors = true
                errors <<  "Unknown severity ${variable.value} encountered for variable: ${variable.name}"
            }
        } else if ("CANCEL".equalsIgnoreCase(objectType)) {
            if (variable.value != null && variable.value == true) {
                nodeResult.setWasOmitted(true)
            }
        } else if ("NOTE".equalsIgnoreCase(objectType)) {
			nodeResult.setNote(variable.value)
        } else {
            log.error("Unknown type ${objectType} encountered for variable: ${variable.name}")
            hasErrors = true
            errors <<  "Unknown type ${objectType} encountered for variable: ${variable.name}"
        }
        hasErrors
    }
}
