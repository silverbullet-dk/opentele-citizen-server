package org.opentele.server.citizen

import org.opentele.server.core.model.patientquestionnaire.PatientQuestionnaireNodeVisitor
import org.opentele.server.model.patientquestionnaire.*
import org.opentele.server.model.questionnaire.MeasurementNode
import org.opentele.server.core.model.types.DataType
import org.opentele.server.core.model.types.MeterTypeName
import org.opentele.server.core.model.types.Operation
import org.opentele.server.core.model.types.Severity
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

class QuestionnaireOutputBuilder implements PatientQuestionnaireNodeVisitor {
    final List nodes = []
    final Map<String,String> outputVariables = new HashMap<String,String>()
    final MessageSource messageSource

    public QuestionnaireOutputBuilder(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    def build(PatientQuestionnaire questionnaire) {
        questionnaire.getNodes().each {
            it.visit(this)
        }
    }

    @Override
    void visitEndNode(PatientQuestionnaireNode n) {
        nodes << [
            EndNode: [
                nodeName: n.id as String
            ]
        ]
    }

    @Override
    void visitBooleanNode(PatientQuestionnaireNode n) {
        addBooleanAssignmentNode(nodeName: "${n.id}", nextNodeId: n.defaultNext.id, variableName: n.variableName, variableValue: n.value)
    }

    @Override
    void visitChoiceNode(PatientQuestionnaireNode n) {
        def severityDefined = n.defaultSeverity || n.alternativeSeverity

        nodes << [
            DecisionNode: [
                nodeName: n.id as String,
                // drop assignment nodes if no severity defined
                next: (severityDefined ? "AN_${n.defaultNext.id}_T${n.id}" : n.defaultNext.id as String),
                nextFalse: (severityDefined ? "AN_${n.alternativeNext.id}_F${n.id}" : n.alternativeNext.id  as String),
                expression: patientChoiceNodeExpression(n)
            ]
        ]

        addBooleanAssignmentNode(nodeName: "AN_${n.defaultNext.id}_T${n.id}", nextNodeId: "ANSEV_${n.defaultNext.id}_T${n.id}", variableName: "${n.id}.DECISION", variableValue: true)
        addBooleanAssignmentNode(nodeName: "AN_${n.alternativeNext.id}_F${n.id}", nextNodeId: "ANSEV_${n.alternativeNext.id}_F${n.id}", variableName: "${n.id}.DECISION", variableValue: false)

        // Haandter ChoiceNode severity w/assignment nodes..
        if (severityDefined) {
            String defaultValue = n.defaultSeverity?.value() ?: Severity.GREEN.value()
            String alternativeValue = n.alternativeSeverity?.value() ?: Severity.GREEN.value()
            addStringAssignmentNode(nodeName: "ANSEV_${n.defaultNext.id}_T${n.id}", nextNodeId: n.defaultNext.id,
                    variableName: "${n.id}.DECISION#SEVERITY", variableValue: defaultValue)
            addStringAssignmentNode(nodeName: "ANSEV_${n.alternativeNext.id}_F${n.id}", nextNodeId: n.alternativeNext.id,
                    variableName: "${n.id}.DECISION#SEVERITY", variableValue: alternativeValue)
        }
    }

    @Override
    void visitInputNode(PatientQuestionnaireNode n) {
        def severityDefined = n.defaultSeverity || n.alternativeSeverity

        if (n.inputType == DataType.BOOLEAN) {
            String no = messageSource.getMessage('default.yesno.no', new String[0], LocaleContextHolder.locale)
            String yes = messageSource.getMessage('default.yesno.yes', new String[0], LocaleContextHolder.locale)

            if (n.helpInfo) {
            addIoNode(nodeName: n.id, elements: [
                    textViewElement(text: n.text),
                        helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id),
                    twoButtonElement(leftText: no, leftNextNodeId: "AN_${n.alternativeNext.id}_L${n.id}",
                        rightText: yes, rightNextNodeId: "AN_${n.defaultNext.id}_R${n.id}")
                ]
            )
            } else {
                addIoNode(nodeName: n.id, elements: [
                        textViewElement(text: n.text),
                        twoButtonElement(leftText: no, leftNextNodeId: "AN_${n.alternativeNext.id}_L${n.id}",
                                rightText: yes, rightNextNodeId: "AN_${n.defaultNext.id}_R${n.id}")
                ]
                )
            }
            // "Yes" choice
            def yesChoiceNext = severityDefined ? "ANSEV_${n.defaultNext.id}_R${n.id}" : "${n.defaultNext.id}"
            addBooleanAssignmentNode(nodeName: "AN_${n.defaultNext.id}_R${n.id}", nextNodeId: yesChoiceNext, variableName: "${n.id}.FIELD", variableValue: true)
            // "No" choice
            def noChoiceNext = severityDefined ? "ANSEV_${n.alternativeNext.id}_L${n.id}" : n.alternativeNext.id as String
            addBooleanAssignmentNode(nodeName: "AN_${n.alternativeNext.id}_L${n.id}", nextNodeId: noChoiceNext, variableName: "${n.id}.FIELD", variableValue: false)
            // Haandter severity w/assignment nodes..

            // Left severity
            if (severityDefined) {
                // "Yes"
                def yesValue = n.defaultSeverity?.value() ?: Severity.GREEN.value()
                addStringAssignmentNode(nodeName: "ANSEV_${n.defaultNext.id}_R${n.id}", nextNodeId: n.defaultNext.id,
                        variableName: "${n.id}.FIELD#SEVERITY", variableValue: yesValue)
                // "No"
                def noValue = n.alternativeSeverity?.value() ?: Severity.GREEN.value()
                addStringAssignmentNode(nodeName: "ANSEV_${n.alternativeNext.id}_L${n.id}", nextNodeId: n.alternativeNext.id,
                        variableName: "${n.id}.FIELD#SEVERITY", variableValue: noValue)
            }
        } else {
            // Patient input node.. but not boolean
            def elements = []

            if (n.inputType == DataType.STRING) {
                elements << textViewElement(text: n.text)
                if (n.helpInfo)
                    elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
                elements << editStringElement(variableName: "${n.id}.FIELD")
                elements << buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id)
            } else if (n.inputType == DataType.INTEGER) {
                elements << textViewElement(text: n.text)
                if (n.helpInfo)
                    elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
                elements << editIntegerElement(variableName: "${n.id}.FIELD")
                elements << buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id)
            } else if (n.inputType == DataType.FLOAT) {
                elements << textViewElement(text: n.text)
                if (n.helpInfo)
                    elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
                elements << editFloatElement(variableName: "${n.id}.FIELD")
                elements << buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id)
            } else if (n.inputType == DataType.RADIOCHOICE) {
                def choices = n.choices.collect { choice(text: it.label, value: it.value) }

                elements << textViewElement(text: n.text, header: false)
                if (n.helpInfo)
                    elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
                elements << radioButtonElement(choices: choices, variableName: "${n.id}.FIELD")
                elements << buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id, skipValidation: false)
            } else {
                throw new UnsupportedOperationException("Handling of inputtype: ${n.inputType} is not yet implemented.")
            }

            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        }
    }

    @Override
    void visitDelayNode(PatientQuestionnaireNode n) {
        addDelayNode(nodeName: n.id, nextNodeId: n.defaultNext.id,
                elements: [
                ],
                countTime: n.countTime,
                countUp: n.countUp,
                displayTextString: n.text
        )
    }

    @Override
    void visitNoteInputNode(PatientQuestionnaireNode n) {
        addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: [
                textViewElement(text: n.text),
                noteTextElement(parent: "${n.id}.FIELD"),
                buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id)
            ]
        )
    }

    @Override
    void visitTextNode(PatientQuestionnaireNode n) {
        // TextNode maps to simple IONode, with a textfield and a "next" button..
        def elements = []
        if (n.headline != null && n.headline != '') {
            elements << textViewElement(text: n.headline)
        }
        elements << textViewElement(text: n.text)
        if (n.helpInfo)
            elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
        elements << buttonElement(text: 'Næste', nextNodeId: n.defaultNext.id)

        addIoNode(nodeName: n.id, elements: elements)
    }

    @Override
    void visitMeasurementNode(PatientQuestionnaireNode n) {

        switch (n.meterType.name) {
            case MeterTypeName.CTG.value():
                patientMeasurementNodeForCtg(n)
                break
            case MeterTypeName.WEIGHT:
                patientMeasurementNodeForWeight(n)
                break
            case MeterTypeName.TEMPERATURE:
                patientMeasurementNodeForTemperature(n)
                break
            case MeterTypeName.BLOOD_PRESSURE_PULSE.value():
                patientMeasurementNodeForBloodPressureAndPulse(n)
                break
            case MeterTypeName.SATURATION.value():
                patientMeasurementNodeForSaturation(n)
                break
            case MeterTypeName.SATURATION_W_OUT_PULSE.value():
                patientMeasurementNodeForSaturationWithoutPulse(n)
                break
            case MeterTypeName.URINE.value():
                patientMeasurementNodeForUrine(n)
                break
            case MeterTypeName.URINE_GLUCOSE:
                patientMeasurementNodeForGlucoseInUrine(n)
                break
            case MeterTypeName.CRP:
                patientMeasurementNodeForCrp(n)
                break
            case MeterTypeName.HEMOGLOBIN:
                patientMeasurementNodeForHemoglobin(n)
                break
            case MeterTypeName.BLOODSUGAR:
                patientMeasurementNodeForBloodSugar(n)
                break
            case MeterTypeName.LUNG_FUNCTION:
                patientMeasurementNodeForLungFunction(n)
                break
            case MeterTypeName.CONTINUOUS_BLOOD_SUGAR_MEASUREMENT:
                patientMeasurementNodeForContinuousBloodSugarMeasurement(n)
                break
            default:
                throw new RuntimeException("Unsupported datatype ${n.meterType.name}")
        }
    }

    private def patientMeasurementNodeForCtg(n) {

        def deviceIdOutputVariableName = "${n.id}.CTG#${MeasurementNode.DEVICE_ID_VAR}"

        outputVariables["${n.id}.CTG#${MeasurementNode.FHR_VAR}"] = DataType.FLOAT_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.MHR_VAR}"] = DataType.FLOAT_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.QFHR_VAR}"] = DataType.INTEGER_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.TOCO_VAR}"] = DataType.FLOAT_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.SIGNAL_VAR}"] = DataType.STRING_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.SIGNAL_TO_NOISE_VAR}"] = DataType.INTEGER_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.FETAL_HEIGHT_VAR}"] = DataType.INTEGER_ARRAY.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.VOLTAGESTART_VAR}"] = DataType.FLOAT.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.VOLTAGEEND_VAR}"] = DataType.FLOAT.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.STARTTIME_VAR}"] = DataType.STRING.value()
        outputVariables["${n.id}.CTG#${MeasurementNode.ENDTIME_VAR}"] = DataType.STRING.value()
        outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()

        def deviceNodeContents = [
            nodeName: n.id as String,
            next: defaultNextSeverityNodeName(n) as String,
            nextFail: cancelNodeName(n),

            helpText: n?.helpInfo?.text,
            helpImage: n?.helpInfo?.helpImage?.id,

            fhr: [
                name: "${n.id}.CTG#${MeasurementNode.FHR_VAR}",
                type: DataType.FLOAT_ARRAY.value()
            ],
            mhr: [
                name: "${n.id}.CTG#${MeasurementNode.MHR_VAR}",
                type: DataType.FLOAT_ARRAY.value()
            ],
            qfhr: [
                name: "${n.id}.CTG#${MeasurementNode.QFHR_VAR}",
                type: DataType.INTEGER_ARRAY.value()
            ],
            toco: [
                name: "${n.id}.CTG#${MeasurementNode.TOCO_VAR}",
                type: DataType.FLOAT_ARRAY.value()
            ],
            signal: [
                name: "${n.id}.CTG#${MeasurementNode.SIGNAL_VAR}",
                type: DataType.STRING_ARRAY.value()
            ],
            signalToNoise: [
                name: "${n.id}.CTG#${MeasurementNode.SIGNAL_TO_NOISE_VAR}",
                type: DataType.INTEGER_ARRAY.value()
            ],
            fetalHeight: [
                name: "${n.id}.CTG#${MeasurementNode.FETAL_HEIGHT_VAR}",
                type: DataType.INTEGER_ARRAY.value()
            ],
            voltageStart: [
                name: "${n.id}.CTG#${MeasurementNode.VOLTAGESTART_VAR}",
                type: DataType.FLOAT.value()
            ],
            voltageEnd: [
                name: "${n.id}.CTG#${MeasurementNode.VOLTAGEEND_VAR}",
                type: DataType.FLOAT.value()
            ],
            startTime: [
                name: "${n.id}.CTG#${MeasurementNode.STARTTIME_VAR}",
                type: DataType.STRING.value()
            ],
            endTime: [
                name: "${n.id}.CTG#${MeasurementNode.ENDTIME_VAR}",
                type: DataType.STRING.value()
            ],
            deviceId: [
                    name: deviceIdOutputVariableName,
                    type: DataType.STRING.value()
            ]
        ]

        def shouldIncludeMeasuringTime = inputVariableNameForMeasurementTime(n) != null
        if (shouldIncludeMeasuringTime) {
            deviceNodeContents['measuringTime'] = [
                type: 'name',
                value: inputVariableNameForMeasurementTime(n)
            ]
        }

        nodes << (n.simulate ? [MonicaTestDeviceNode: deviceNodeContents] : [MonicaDeviceNode: deviceNodeContents])

        addCancelAndSeverityAssignments(n, "${n.id}.CTG##CANCEL", "${n.id}.CTG##SEVERITY")
    }

    private def patientMeasurementNodeForWeight(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.WEIGHT_VAR}"
        def deviceIdOutputVariableName = "${n.id}.${MeasurementNode.WEIGHT_VAR}#${MeasurementNode.DEVICE_ID_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << editFloatElement(variableName: outputVariableName)
            elements << buttonsToSkipInputForNode(leftNext: cancelNodeName(n), rightNext: defaultNextSeverityNodeName(n))

            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        } else {
            outputVariables[outputVariableName] = DataType.FLOAT.value()
            outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()
            def nodeContents = [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                text: n.text,
                weight: [
                    name: outputVariableName,
                    type: DataType.FLOAT.value()
                ],
                deviceId: [
                        name: deviceIdOutputVariableName,
                        type: DataType.STRING.value()
                ],
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id
            ]
            nodes << (n.simulate ? [WeightTestDeviceNode: nodeContents] : [WeightDeviceNode: nodeContents])
        }
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.WEIGHT_VAR}#CANCEL", "${n.id}.${MeasurementNode.WEIGHT_VAR}#SEVERITY")
    }

    private def patientMeasurementNodeForTemperature(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.TEMPERATURE_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << editFloatElement(variableName: outputVariableName)
            elements << buttonsToSkipInputForNode(leftNext: cancelNodeName(n), rightNext: defaultNextSeverityNodeName(n))
            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        } else { // No support for simulated nodes for now.. Since all temp measurements are manual

            outputVariables[outputVariableName] = DataType.FLOAT.value()
            nodes << [
                TemperatureDeviceNode: [
                    nodeName: n.id as String,
                    next: defaultNextSeverityNodeName(n) as String,
                    nextFail: cancelNodeName(n),
                    text: n.text,
                    helpText: n?.helpInfo?.text,
                    helpImage: n?.helpInfo?.helpImage?.id,
                    temperature: [
                        name: outputVariableName,
                        type: DataType.FLOAT.value()
                    ]
                ]
            ]
        }
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.TEMPERATURE_VAR}#CANCEL", "${n.id}.${MeasurementNode.TEMPERATURE_VAR}#SEVERITY")
    }

    def patientMeasurementNodeForBloodPressureAndPulse(n) {

        def systolicOutputVariableName = "${n.id}.BP#${MeasurementNode.SYSTOLIC_VAR}"
        def diastolicOutputVariableName = "${n.id}.BP#${MeasurementNode.DIASTOLIC_VAR}"
        def meanArterialPressureOutputVariableName = "${n.id}.BP#${MeasurementNode.MEAN_ARTERIAL_PRESSURE_VAR}"
        def pulseOutputVariableName = "${n.id}.BP#${MeasurementNode.PULSE_VAR}"
        def deviceIdOutputVariableName = "${n.id}.BP#${MeasurementNode.DEVICE_ID_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << textViewElement(text: 'Systolisk blodtryk')
            elements << editIntegerElement(variableName: systolicOutputVariableName)
            elements << textViewElement(text: 'Diastolisk blodtryk')
            elements << editIntegerElement(variableName: diastolicOutputVariableName)
            elements << textViewElement(text: 'Puls')
            elements << editIntegerElement(variableName: pulseOutputVariableName)
            elements << buttonsToSkipInputForNode(leftNext: cancelNodeName(n), rightNext: defaultNextSeverityNodeName(n))

            addIoNode(nodeName: n.id, nextNodeId: defaultNextSeverityNodeName(n), elements: elements)
        } else {

            outputVariables[systolicOutputVariableName] = DataType.INTEGER.value()
            outputVariables[diastolicOutputVariableName] = DataType.INTEGER.value()
            outputVariables[meanArterialPressureOutputVariableName] = DataType.INTEGER.value()
            outputVariables[pulseOutputVariableName] = DataType.INTEGER.value()
            outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()

            def nodeContents = [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n),
                nextFail: cancelNodeName(n),
                text: n.text,
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                diastolic: [
                    name: diastolicOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                systolic: [
                    name: systolicOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                meanArterialPressure: [
                    name: meanArterialPressureOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                pulse: [
                    name: pulseOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                deviceId: [
                    name: deviceIdOutputVariableName,
                    type: DataType.STRING.value()
                ]
            ]
            nodes << (n.simulate ? [BloodPressureTestDeviceNode: nodeContents] : [BloodPressureDeviceNode: nodeContents])
        }
        addCancelAndSeverityAssignments(n, "${n.id}.BP##CANCEL", "${n.id}.BP##SEVERITY")
    }

    private def patientMeasurementNodeForSaturation(n) {

        def saturationOutputVariableName = "${n.id}.SAT#${MeasurementNode.SATURATION_VAR}"
        def pulseOutputVariableName = "${n.id}.SAT#${MeasurementNode.PULSE_VAR}"
        def deviceIdOutputVariableName = "${n.id}.SAT#${MeasurementNode.DEVICE_ID_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << textViewElement(text: 'Iltmætning')
            elements << editIntegerElement(variableName: saturationOutputVariableName)
            elements << textViewElement(text: 'Puls')
            elements << editIntegerElement(variableName: pulseOutputVariableName)
            elements << buttonsToSkipInputForNode(rightNext: defaultNextSeverityNodeName(n), leftNext: cancelNodeName(n))

            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        } else {

            outputVariables[saturationOutputVariableName] = DataType.INTEGER.value()
            outputVariables[pulseOutputVariableName] = DataType.INTEGER.value()
            outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()

            def nodeContents = [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                text: n.text,
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                saturation: [
                    name: saturationOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                pulse: [
                    name: pulseOutputVariableName,
                    type: DataType.INTEGER.value()
                ],
                deviceId: [
                    name: deviceIdOutputVariableName,
                    type: DataType.STRING.value()
                ]
            ]
            nodes << (n.simulate ? [SaturationTestDeviceNode: nodeContents] : [SaturationDeviceNode: nodeContents])
        }

        addCancelAndSeverityAssignments(n, "${n.id}.SAT##CANCEL", "${n.id}.SAT##SEVERITY")
    }

    private def patientMeasurementNodeForSaturationWithoutPulse(n) {

        def saturationOutputVariableName = "${n.id}.SAT#${MeasurementNode.SATURATION_VAR}"
        def deviceIdOutputVariableName = "${n.id}.SAT#${MeasurementNode.DEVICE_ID_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                elements << helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << textViewElement(text: 'Iltmætning')
            elements << editIntegerElement(variableName: saturationOutputVariableName)
            elements << buttonsToSkipInputForNode(leftNext: cancelNodeName(n), rightNext: defaultNextSeverityNodeName(n))

            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        } else {

            outputVariables[saturationOutputVariableName] = DataType.INTEGER.value()
            outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()

            def nodeContents = [
                    nodeName: n.id as String,
                    next: defaultNextSeverityNodeName(n) as String,
                    nextFail: cancelNodeName(n),
                    text: n.text,
                    helpText: n?.helpInfo?.text,
                    helpImage: n?.helpInfo?.helpImage?.id,
                    saturation: [
                            name: saturationOutputVariableName,
                            type: DataType.INTEGER.value()
                    ],
                    deviceId: [
                            name: deviceIdOutputVariableName,
                            type: DataType.STRING.value()
                    ]
            ]
            nodes << (n.simulate ? [SaturationWithoutPulseTestDeviceNode: nodeContents] : [SaturationWithoutPulseDeviceNode: nodeContents])
        }
        addCancelAndSeverityAssignments(n, "${n.id}.SAT##CANCEL", "${n.id}.SAT##SEVERITY")
    }

    def patientMeasurementNodeForBloodSugar(n) {

        def deviceIdOutputVariableName = "${n.id}.BS#${MeasurementNode.DEVICE_ID_VAR}"
        def bloodSugarMeasurementsOutputVariableName = "${n.id}.BS#${MeasurementNode.BLOODSUGAR_VAR}"

        outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()
        outputVariables[bloodSugarMeasurementsOutputVariableName] = DataType.INTEGER.value()

        def nodeContents = [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                text: n.text,
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                bloodSugarMeasurements: [
                        name: bloodSugarMeasurementsOutputVariableName,
                        type: DataType.INTEGER.value()
                ],
                deviceId: [
                        name: deviceIdOutputVariableName,
                        type: DataType.STRING.value()
                ]
        ]

        if (n.mapToInputFields) {
            nodes << [BloodSugarManualDeviceNode: nodeContents]
        } else if (n.simulate) {
            nodes << [BloodSugarTestDeviceNode: nodeContents]
        } else {
            nodes << [BloodSugarDeviceNode: nodeContents]
        }
        addCancelAndSeverityAssignments(n, "${n.id}.BS##CANCEL", "${n.id}.BS##SEVERITY")
    }

    private def patientMeasurementNodeForContinuousBloodSugarMeasurement(n) {
        def deviceIdOutputVariableName = "${n.id}.CGM#${MeasurementNode.DEVICE_ID_VAR}"
        def bloodSugarMeasurementsOutputVariableName = "${n.id}.CGM#${MeasurementNode.CONTINUOUS_BLOOD_SUGAR_MEASUREMENTS_VAR}"

        outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()
        outputVariables[bloodSugarMeasurementsOutputVariableName] = DataType.STRING_ARRAY.value()

        def nodeContents = [
            nodeName: n.id as String,
            next: defaultNextSeverityNodeName(n) as String,
            nextFail: cancelNodeName(n),
            text: n.text,
            helpText: n?.helpInfo?.text,
            helpImage: n?.helpInfo?.helpImage?.id,
            events: [
                name: bloodSugarMeasurementsOutputVariableName,
                type: DataType.STRING_ARRAY.value()
            ],
            deviceId: [
                name: deviceIdOutputVariableName,
                type: DataType.STRING.value()
            ],
        ]

        if (n.mapToInputFields) {
            // Makes no sense at all
        } else if (n.simulate) {
            nodes << [ContinuousBloodSugarTestDeviceNode: nodeContents]
        } else {
            nodes << [ContinuousBloodSugarDeviceNode: nodeContents]
        }
        addCancelAndSeverityAssignments(n, "${n.id}.CGM##CANCEL", "${n.id}.CGM##SEVERITY")
    }

    def patientMeasurementNodeForLungFunction(n) {

        def deviceIdOutputVariableName = "${n.id}.LF#${MeasurementNode.DEVICE_ID_VAR}"
        def fev1OutputVariableName = "${n.id}.LF#${MeasurementNode.FEV1_VAR}"
        def fev6OutputVariableName = "${n.id}.LF#${MeasurementNode.FEV6_VAR}"
        def fev1Fev6RatioOutputVariableName = "${n.id}.LF#${MeasurementNode.FEV1_FEV6_RATIO_VAR}"
        def fef2575OutputVariableName = "${n.id}.LF#${MeasurementNode.FEF2575_VAR}"
        def goodTestOutputVariableName = "${n.id}.LF#${MeasurementNode.FEV_GOOD_TEST_VAR}"
        def softwareVersionOutputVariableName = "${n.id}.LF#${MeasurementNode.FEV_SOFTWARE_VERSION}"

        outputVariables[deviceIdOutputVariableName] = DataType.STRING.value()
        outputVariables[fev1OutputVariableName] = DataType.FLOAT.value()
        outputVariables[fev6OutputVariableName] = DataType.FLOAT.value()
        outputVariables[fev1Fev6RatioOutputVariableName] = DataType.FLOAT.value()
        outputVariables[fef2575OutputVariableName] = DataType.FLOAT.value()
        outputVariables[goodTestOutputVariableName] = DataType.BOOLEAN.value()
        outputVariables[softwareVersionOutputVariableName] = DataType.INTEGER.value()

        def nodeContents = [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                text: n.text,
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                fev1: [
                        name: fev1OutputVariableName,
                        type: DataType.FLOAT.value()
                ],
                fev6: [
                        name: fev6OutputVariableName,
                        type: DataType.FLOAT.value()
                ],
                fev1Fev6Ratio: [
                        name: fev1Fev6RatioOutputVariableName,
                        type: DataType.FLOAT.value()
                ],
                fef2575: [
                        name: fef2575OutputVariableName,
                        type: DataType.FLOAT.value()
                ],
                goodTest: [
                        name: goodTestOutputVariableName,
                        type: DataType.BOOLEAN.value()
                ],
                softwareVersion: [
                        name: softwareVersionOutputVariableName,
                        type: DataType.INTEGER.value()
                ],
                deviceId: [
                        name: deviceIdOutputVariableName,
                        type: DataType.STRING.value()
                ]
        ]

        // No support for "n.mapToInputFields"
        if (n.simulate) {
            nodes << [LungMonitorTestDeviceNode: nodeContents]
        } else {
            nodes << [LungMonitorDeviceNode: nodeContents]
        }
        addCancelAndSeverityAssignments(n, "${n.id}.LF##CANCEL", "${n.id}.LF##SEVERITY")
    }

    private def patientMeasurementNodeForUrine(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.URINE_VAR}"
        outputVariables[outputVariableName] = DataType.INTEGER.value()

        nodes << [
            UrineDeviceNode: [
                nodeName: n.id as String,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                text: n.text,
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                urine : [
                    name: outputVariableName,
                    type: DataType.INTEGER.value()
                ]
            ]
        ]
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.URINE_VAR}#CANCEL", "${n.id}.${MeasurementNode.URINE_VAR}#SEVERITY")
    }

    private def patientMeasurementNodeForGlucoseInUrine(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.GLUCOSE_URINE_VAR}"
        outputVariables[outputVariableName] = DataType.INTEGER.value()

        nodes << [
                GlucoseUrineDeviceNode: [
                        nodeName: n.id as String,
                        next: defaultNextSeverityNodeName(n) as String,
                        nextFail: cancelNodeName(n),
                        text: n.text,
                        helpText: n?.helpInfo?.text,
                        helpImage: n?.helpInfo?.helpImage?.id,
                        glucoseUrine : [
                                name: outputVariableName,
                                type: DataType.INTEGER.value()
                        ]
                ]
        ]
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.GLUCOSE_URINE_VAR}#CANCEL", "${n.id}.${MeasurementNode.GLUCOSE_URINE_VAR}#SEVERITY")
    }

    private def patientMeasurementNodeForCrp(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.CRP_VAR}"
        outputVariables[outputVariableName] =  DataType.INTEGER.value()

        nodes << [
            CRPNode: [
                nodeName: n.id as String,
                text: n.text,
                next: defaultNextSeverityNodeName(n) as String,
                nextFail: cancelNodeName(n),
                helpText: n?.helpInfo?.text,
                helpImage: n?.helpInfo?.helpImage?.id,
                CRP : [
                    name: outputVariableName,
                    type: DataType.INTEGER.value()
                ]
            ]
        ]
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.CRP_VAR}#CANCEL", "${n.id}.${MeasurementNode.CRP_VAR}#SEVERITY")
    }

    private def patientMeasurementNodeForHemoglobin(n) {

        def outputVariableName = "${n.id}.${MeasurementNode.HEMOGLOBIN_VAR}"

        if (n.mapToInputFields) {
            def elements = []
            elements << textViewElement(text: n.text)
            if (n.helpInfo)
                helpTextElement(text: n.helpInfo?.text, imageFile: n.helpInfo?.helpImage?.id)
            elements << editFloatElement(variableName: outputVariableName)
            elements << buttonsToSkipInputForNode(leftNext: cancelNodeName(n), rightNext: defaultNextSeverityNodeName(n))

            addIoNode(nodeName: n.id, nextNodeId: n.defaultNext.id, elements: elements)
        } else {
            outputVariables[outputVariableName] = DataType.FLOAT.value()
            nodes << [
                    HaemoglobinDeviceNode: [
                            nodeName: n.id as String,
                            next: defaultNextSeverityNodeName(n) as String,
                            nextFail: cancelNodeName(n),
                            text: n.text,
                            helpText: n?.helpInfo?.text,
                            helpImage: n?.helpInfo?.helpImage?.id,
                            haemoglobinValue: [
                                    name: outputVariableName,
                                    type: DataType.FLOAT.value()
                            ]
                    ]
            ]
        }
        addCancelAndSeverityAssignments(n, "${n.id}.${MeasurementNode.HEMOGLOBIN_VAR}#CANCEL", "${n.id}.${MeasurementNode.HEMOGLOBIN_VAR}#SEVERITY")
    }

    private def addCancelAndSeverityAssignments(node, cancelVariableName, severityVariableName) {

        addBooleanAssignmentNode(nodeName: cancelNodeName(node), nextNodeId: nextFailSeverityNodeName(node), variableName: cancelVariableName, variableValue: true)

        def failPathValue = node.nextFailSeverity?.value() ?: Severity.GREEN.value()

        addStringAssignmentNode(nodeName: nextFailSeverityNodeName(node),
                nextNodeId: node.nextFail.id,
                variableName: severityVariableName,
                variableValue: failPathValue)

        def defaultPathValue = node.defaultSeverity?.value() ?: Severity.GREEN.value()

        addStringAssignmentNode(nodeName: defaultNextSeverityNodeName(node),
                nextNodeId: node.defaultNext.id,
                variableName: severityVariableName,
                variableValue: defaultPathValue)
    }

    private def patientChoiceNodeExpression(n) {
        def variableName = inputVariableNameForDecisionNode(n)
        def options = [
            left: [
                type:n.dataType.value(),
                value: n.nodeValue
            ],
            right: [
                type: 'name',
                value: variableName
            ]
        ]

        if (n.operation == Operation.GREATER_THAN) {
            [ gt: options ]
        } else if (n.operation == Operation.LESS_THAN) {
            [ lt: options ]
        } else if (n.operation == Operation.EQUALS) {
            [ eq: options ]
        } else {
            throw new RuntimeException("Unsupported operation: ${n.operation}")
        }
    }

    private String cancelNodeName(n) {
        "AN_${n.id}_CANCEL"
    }

    private String defaultNextSeverityNodeName(node) {
        "ANSEV_${node.defaultNext.id}_D${node.id}"
    }

    private String nextFailSeverityNodeName(node) {
        "ANSEV_${node.nextFail.id}_F${node.id}"
    }

    private String inputVariableNameForMeasurementTime(PatientMeasurementNode dn) {
        if (dn.monicaMeasuringTimeInputNode) {
            "${dn.monicaMeasuringTimeInputNode.id}.${dn.monicaMeasuringTimeInputVar}"
        } else {
            null
        }
    }

    private String inputVariableNameForDecisionNode(PatientChoiceNode dn) {
        if (dn.inputNode.instanceOf(PatientBooleanNode)) {
            dn.inputVar
        } else if (!dn.inputNode.instanceOf(PatientMeasurementNode)) {
            "${dn.inputNode.id}.${dn.inputVar}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.CTG.value())) {
            "${dn.inputNode.id}.CTG#${dn.inputVar}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.BLOOD_PRESSURE_PULSE)) {
            "${dn.inputNode.id}.BP#${dn.inputVar}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.SATURATION)) {
            "${dn.inputNode.id}.SAT#${dn.inputVar}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.WEIGHT)) {
            "${dn.inputNode.id}.${MeasurementNode.WEIGHT_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.URINE)) {
            "${dn.inputNode.id}.${MeasurementNode.URINE_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.URINE_GLUCOSE)) {
            "${dn.inputNode.id}.${MeasurementNode.GLUCOSE_URINE_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.TEMPERATURE)) {
            "${dn.inputNode.id}.${MeasurementNode.TEMPERATURE_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.HEMOGLOBIN)) {
            "${dn.inputNode.id}.${MeasurementNode.HEMOGLOBIN_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.CRP)) {
            "${dn.inputNode.id}.${MeasurementNode.CRP_VAR}"
        } else if (dn.inputNode.meterType.name.equals(MeterTypeName.BLOODSUGAR)) {
            "${dn.inputNode.id}.BS#${dn.inputVar}"
        } else {
            'ERROR_unsupported'
        }
    }

    private void addBooleanAssignmentNode(parameters) {
        outputVariables[parameters.variableName] = DataType.BOOLEAN.value()

        nodes << [
            AssignmentNode: [
                nodeName: parameters.nodeName,
                next: parameters.nextNodeId as String,
                variable: [
                    name: parameters.variableName,
                    type: DataType.BOOLEAN.value()
                ],
                expression: [
                    type: DataType.BOOLEAN.value(),
                    value: parameters.variableValue
                ]
            ]
        ]
    }

    private void addStringAssignmentNode(parameters) {
        outputVariables[parameters.variableName] = DataType.STRING.value()

        nodes << [
            AssignmentNode: [
                nodeName: parameters.nodeName,
                next: parameters.nextNodeId as String,
                variable: [
                    name: parameters.variableName,
                    type: DataType.STRING.value()
                ],
                expression: [
                    type: DataType.STRING.value(),
                    value: parameters.variableValue
                ]
            ]
        ]
    }

    private void addIoNode(parameters) {
        def contents = [
            nodeName: parameters.nodeName as String,
            elements: parameters.elements
        ]
        if (parameters.nextNodeId) {
            contents['next'] = parameters.nextNodeId as String
        }

        nodes << [
            IONode: contents
        ]
    }

    private void addDelayNode(parameters) {
        def contents = [
                nodeName: parameters.nodeName as String,
                elements: parameters.elements,
                countTime: parameters.countTime,
                countUp: parameters.countUp,
                displayTextString: parameters.displayTextString
        ]
        if (parameters.nextNodeId) {
            contents['next'] = parameters.nextNodeId as String
        }

        nodes << [
                DelayNode: contents
        ]
    }

    private def textViewElement(parameters) {
        def contents = [text: parameters.text]
        if (parameters.header != null) {
            contents['header'] = parameters.header
        }

        [
            TextViewElement: contents
        ]
    }

    private def helpTextElement(parameters) {
        def contents
        if (parameters.text) {
            contents = [text: parameters.text]
        }
        if (parameters.imageFile) {
            if (contents) {
                contents['imageFile'] = parameters.imageFile
            } else {
                contents = [imageFile: parameters.imageFile]
            }
        }

        [
                HelpTextElement: contents
        ]
    }

    private def buttonsToSkipInputForNode(parameters) {
        [
                TwoButtonElement: [
                        leftText: "Undlad",
                        leftNext: parameters.leftNext as String,
                        leftSkipValidation: true,

                        rightText: "Næste",
                        rightNext: parameters.rightNext as String,
                        rightSkipValidation: false
                ]
        ]
    }

    private def buttonElement(parameters) {
        def contents = [
            text: parameters.text,
            gravity: 'center',
            next: parameters.nextNodeId as String
        ]

        if (parameters.skipValidation != null) {
            contents['skipValidation'] = parameters.skipValidation
        }

        [
            ButtonElement: contents
        ]
    }

    private def twoButtonElement(parameters) {
        [
            TwoButtonElement: [
                leftText: parameters.leftText,
                leftNext: parameters.leftNextNodeId,
                rightText: parameters.rightText,
                rightNext: parameters.rightNextNodeId
            ]
        ]
    }

    private def choice(parameters) {
        [
            value: [
                type: DataType.STRING.value(),
                value: parameters.value
            ],
            text: parameters.text
        ]
    }

    private def radioButtonElement(parameters) {
        outputVariables[parameters.variableName] = DataType.STRING.value()

        [
            RadioButtonElement: [
                choices: parameters.choices,
                outputVariable: [
                    name: parameters.variableName,
                    type: DataType.STRING.value()
                ]
            ]
        ]
    }

    private def editStringElement(parameters) {
        outputVariables[parameters.variableName] = DataType.STRING.value()

        [
            EditTextElement: [
                outputVariable: [
                    name: parameters.variableName,
                    type: DataType.STRING.value()
                ]
            ]
        ]
    }

    private def editIntegerElement(parameters) {
        outputVariables[parameters.variableName] = DataType.INTEGER.value()

        [
            EditTextElement: [
                outputVariable: [
                    name: parameters.variableName,
                    type: DataType.INTEGER.value()
                ]
            ]
        ]
    }

    private def editFloatElement(parameters) {
        outputVariables[parameters.variableName] = DataType.FLOAT.value()

        [
            EditTextElement: [
                outputVariable: [
                    name: parameters.variableName,
                    type: DataType.FLOAT.value()
                ]
            ]
        ]
    }

    private def noteTextElement(parameters) {
        outputVariables[parameters.parent] = DataType.STRING.value()

        [
            NoteTextElement: [
                note: [
                    parent: parameters.parent,
                    type: DataType.STRING.value()
                ]
            ]
        ]
    }
}
