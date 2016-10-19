import groovy.json.JsonOutput

/**
 *  VictorOps Integration
 *
 *  Copyright 2016 Greg Frank
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "VictorOps Integration",
    namespace: "victorops",
    author: "Greg Frank",
    description: "Forward events to VictorOps",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")
    appSetting "restAPIKey"

preferences {
	section("Alert from temperature sensors:") {
    	input "temperatures", "capability.temperatureMeasurement", multiple: true, required: false
    }
    section("Alert from humidity sensors:") {
    	input "humidities", "capability.relativeHumidityMeasurement", multiple: true, required: false
    }
    section("Alert from smoke detectors:") {
    	input "smokedetectors", "capability.smokeDetector", multiple: true, required: false
    }
    section("Alert from water detectors:") {
    	input "waterdetectors", "capability.waterSensor", multiple: true, required: false
    }
    section("Alert from motion sensors:") {
    	input "motions", "capability.motionSensor", multiple: true, required: false
    }
    section("Alert from batteries:") {
        input "batteries", "capability.battery", multiple: true, required: false
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	if (temperatures != null) {
	    subscribe(temperatures,		"temperature",  			deviceHandler)
    }
	if (motions != null) { 
      subscribe(motions,			"motion",       			deviceHandler)
    }
	if (smokedetectors != null) { 
	  subscribe(smokedetectors,	"smokeDetector",			deviceHandler)
    }
	if (waterdetectors != null) { 
	  subscribe(waterdetectors,	"water",					deviceHandler)
    }
  	if (batteries != null) { 
      subscribe(batteries,		"battery",                  deviceHandler)
    }
  	if (humidities != null) {
     subscribe(humidities,		"humidity",                 deviceHandler)
    }
}

def addField(fieldName, fieldValue, escapeQuote = false, comma = true) {
  def base = ""

  if (escapeQuote) base += '\\'
  base += '"' + fieldName 
  if (escapeQuote) base += '\\'
  base += '"'
  if (comma) base += ','
  return base
}

def deviceHandler(evt) {
   log.debug "victorops begin device handler"    
    try {
        def state = '{'       
            state += addField("date", evt.date)
            state += addField("name", evt.name)
            state += addField("displayName", evt.displayName)
            state += addField("device", evt.device)
            state += addField("deviceId", evt.deviceId)
            state += addField("value", evt.value)
            state += addField("isStateChange", evt.isStateChange())
            state += addField("id", evt.id)
            state += addField("description", evt.description)
            state += addField("descriptionText", evt.descriptionText)
            state += addField("installedSmartAppId", evt.installedSmartAppId)
            state += addField("isoDate", evt.isoDate)
            state += addField("isDigital", evt.isDigital())
            state += addField("isPhysical", evt.isPhysical())
            state += addField("location", evt.location)
            state += addField("locationId", evt.locationId)
            state += addField("unit", evt.unit)
            state += addField("source", evt.source, false, false)
            state += '}'
    
        def json = '{'
            json += addField("message_type", "critical", true)
            json += addField("monitoring_tool","smartthings", true)
            json += addField("entity_id", evt.device, true)
            json += addField("state_message", state, true, false)
            json += '}'
        log.debug "victorops sending json msg ${json}"

        def params = [
            uri: "https://alert.victorops.com/integrations/generic/20131114/alert/" + app.appSettings.restAPIKey + "/smartthings",
            body: json
        ]
        
        httpPostJson(params)
    } catch (Throwable e ) {
       	log.debug "victorops error ${e}"
    }
}