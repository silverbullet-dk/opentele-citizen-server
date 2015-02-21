FORMAT:1A

# Patient API
The patient api allows to build OpenTele clients for accessing OpenTele patient data, such as questionnaires, conversations with clinicians etc.

## Authentication
The **Patient API** uses [basic authentication](http://en.wikipedia.org/wiki/Basic_access_authentication) over HTTPS. All resources except the root resource requires a proper `Authorization` header to be present in the request.

## Hypermedia
Resources can include a *links* property containing absolute URLs to related resources.
This means that clients should not construct the URLs on their own, thus making the maintenance of the URL structure easier.

    "links": {
        "self": "https://<host>/patient",
        "password": "https://<host>/rest/password/update",
        "measurements": "https://<host>/patient/measurements",
        "questionnaires": "https://<host>/rest/questionnaire/listing",
        "reminders": "https://<host>/rest/reminder/next"
    }

The &lt;host&gt; in the example above will be replaced with the real value.

The link relation `self` is the URI pointing the resource being represented.

## Schema
All data is sent and received as JSON. Data sent has the `content-type` header set to `application/json`.

Blank fields are included in responses as null:

    "someEmptyField": null


Timestamps are returned in the format:

    YYYY-MM-DDThh:mm:ss.sTZD

## HTTP Verbs
The following HTPP verbs are used throughout the api:

Verb   |Description
-------|-----------------------
GET    |Used for retrieving resources
POST   |Used for creating new resources

## Client Errors
The common [HTTP Response Status Codes](http://en.wikipedia.org/wiki/List_of_HTTP_status_codes) are used.
The actual status codes used are described in the documentation for the specific resources.

# Resources

# Group Entry Point

## Patient API Root [/]
Entry point for consuming the API.

This resource offers basic information about the API version and links to patient related resources. In addition a link to this documentation is also included in the representation.

### Retrieve the Entry Point [GET]

+ Response 200 (application/json)
    + Body

            {
                "version": "1.0.0",
                "serverEnvironment": "production"
                "links": {
                    "self": "https://<host>/",
                    "patient": "https://<host>/patient",
                    "api-doc": "https://<host>/patient-api.html"
                }
            }

# Group Patient

## Patient [/patient]
The Patient resource is the central resource in the Patient API as it, in addition to information about the patient itself, provides access to the resources a patient can access.

+ Model (application/json)

    JSON representation of Patient resource. In addition to representing its state it offers links to available resources.

    + Body

            {
                "firstName": "Nancy Ann",
                "lastName": "Berggren",
                "passwordExpired": false,
                "links": {
                    "self": "https://<host>/patient",
                    "password": "https://<host>/rest/password/update",
                    "measurements": "https://<host>/patient/measurements",
                    "questionnaires": "https://<host>/rest/questionnaire/listing",
                    "reminders": "https://<host>/rest/reminder/next",
                    "messageThreads": "https://<host>/rest/message/recipients",
                    "unreadMessages": "https://<host>/rest/message/list",
                    "acknowledgements": "https://<host>/rest/questionnaire/acknowledgements"
                }
            }

### Retrieve Patient [GET]
+ Request

    + Headers

            Authorization: Basic <base64 encoded user token>

+ Response 200

    [Patient][]

# Group Measurements

## Measurement Types [/patient/measurements]
For a patient measurements performed as part of answering questionnaires can be retrieved.
The measurements are grouped according to the type of measurement, e.g. blood sugar measurements are grouped as a type.

+ Model (application/json)
    JSON representation of the list of available measurement types a client can retrieve.
    Each measurement type is represented as its own link relation.

    + Body

            {
                "measurements": [{
                    "name": "blood_pressure",
                    "links": {
                        "measurement": "https://<host>/patient/measurements/blood_pressure"
                    }
                }, {
                    "name": "pulse",
                    "links": {
                        "measurement": "https://<host>/patient/measurements/pulse"
                    }
                }, {
                    "name": "weight",
                    "links": {
                        "measurement": "https://<host>/patient/measurements/weight"
                    }
                }, {
                    "name": "bloodsugar",
                    "links": {
                        "measurement": "https://<host>/patient/measurements/bloodsugar"
                    }
                }],
                "links": {
                    "self": "http://<host>/patient/measurements"
                }
            }

### Retrieve available measurement types [GET]
+ Request

    + Headers

            Authorization: Basic <base64 encoded user token>

+ Response 200

    [Measurement Types][]

## Measurements [/patient/measurements/{id}?filter={filter}]
Measurements for a specific measurement type can be retrieved using a time filter (using the {filter} parameter), the default time filter is 'week'.

The representation of measurements differ slightly based on the measurement type requested. The representation variants shown in the table below exist:

Representation|Measurement type(s)
--------------|-------------------
Simple        |pulse, weight, temperature, urine, hemoglobin, crp, saturation, lung_function
Blood sugar   |bloodsugar
Blood pressure|blood_pressure


+ Model (application/json)
    JSON representation of a series of measurements for the requested measurement type in the requested time interval.

    + Body

            Measurement representation = 'Simple'
            {
                "type": "pulse",
                "unit": "BPM",
                "measurements": [{
                    "timestamp": "2014-10-19T00:00:00.000+02:00",
                    "measurement": 57
                }, {
                    "timestamp": "2014-09-19T00:00:00.000+02:00",
                    "measurement": 50
                }, {
                    "timestamp": "2014-08-19T00:00:00.000+02:00",
                    "measurement": 56
                }, {
                    "timestamp": "2014-02-19T00:00:00.000+01:00",
                    "measurement": 57
                }, {
                    "timestamp": "2014-01-19T00:00:00.000+01:00",
                    "measurement": 51
                }, {
                    "timestamp": "2013-12-19T00:00:00.000+01:00",
                    "measurement": 52
                }],
                "links": {
                    "self": "https://<host>/patient/measurements/pulse"
                }
            }

            Measurement representation = 'Blood sugar':
            {
                "type": "bloodsugar",
                "unit": "mmol/L",
                "measurements": [{
                    "timestamp": "2014-10-18T02:00:00.000+02:00",
                    "measurement": {
                        "value": 7.199999809265137,
                        "isAfterMeal": false,
                        "isBeforeMeal": false,
                        "isControlMeasurement": false,
                        "isOutOfBounds": null,
                        "otherInformation": false,
                        "hasTemperatureWarning": null
                    }
                }, {
                    "timestamp": "2013-04-27T15:10:00.000+02:00",
                    "measurement": {
                        "value": 8,
                        "isAfterMeal": false,
                        "isBeforeMeal": false,
                        "isControlMeasurement": false,
                        "isOutOfBounds": null,
                        "otherInformation": false,
                        "hasTemperatureWarning": null
                    }
                }, {
                    "timestamp": "2013-03-28T15:10:00.000+01:00",
                    "measurement": {
                        "value": 8,
                        "isAfterMeal": false,
                        "isBeforeMeal": false,
                        "isControlMeasurement": false,
                        "isOutOfBounds": null,
                        "otherInformation": false,
                        "hasTemperatureWarning": null
                    }
                }, {
                    "timestamp": "2013-02-26T15:10:00.000+01:00",
                    "measurement": {
                        "value": 8,
                        "isAfterMeal": false,
                        "isBeforeMeal": false,
                        "isControlMeasurement": false,
                        "isOutOfBounds": null,
                        "otherInformation": false,
                        "hasTemperatureWarning": null
                    }
                }, {
                    "timestamp": "2013-01-27T15:10:00.000+01:00",
                    "measurement": {
                        "value": 8,
                        "isAfterMeal": false,
                        "isBeforeMeal": false,
                        "isControlMeasurement": false,
                        "isOutOfBounds": null,
                        "otherInformation": false,
                        "hasTemperatureWarning": null
                    }
                }],
                "links": {
                    "self": "https://<host>/patient/measurements/bloodsugar"
                }
            }

            Measurement representation = 'Blood pressure':
            {
                "type": "blood_pressure",
                "unit": "mmHg",
                "measurements": [{
                    "timestamp": "2014-10-27T12:25:12.934+01:00",
                    "measurement": {
                        "systolic": 130,
                        "diastolic": 65
                    }
                }, {
                    "timestamp": "2014-03-19T00:00:00.000+01:00",
                    "measurement": {
                        "systolic": 123,
                        "diastolic": 80
                    }
                }, {
                    "timestamp": "2014-02-19T00:00:00.000+01:00",
                    "measurement": {
                        "systolic": 122,
                        "diastolic": 80
                    }
                }, {
                    "timestamp": "2014-01-19T00:00:00.000+01:00",
                    "measurement": {
                        "systolic": 121,
                        "diastolic": 80
                    }
                }, {
                    "timestamp": "2013-12-19T00:00:00.000+01:00",
                    "measurement": {
                        "systolic": 120,
                        "diastolic": 80
                    }
                }],
                "links": {
                    "self": "https://<host>/patient/measurements/blood_pressure"
                }
            }

### Retrieve Measurements [GET]
+ Parameters

    + filter = `week` (optional, string)

        Time filter specifying for how long back in time to get measurements.

        + Values
            + `week`
            + `month`
            + `quarter`
            + `year`
            + `all`

+ Request

    + Headers

            Authorization: Basic <base64 encoded user token>

+ Response 200

    [Measurements][]

+ Response 422

        {
            "message": "Validation failed",
            "errors": [{
                "resource": "measurements",
                "field": "filter",
                "code": "invalid"
            }]
        }
        
