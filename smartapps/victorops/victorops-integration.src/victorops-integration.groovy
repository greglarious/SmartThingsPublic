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
    appSetting "routingKey"

preferences {
	section("Alert from temperature sensors:") {
    	input "temperatures", "capability.temperatureMeasurement", multiple: true, required: false
        input "criticalHotTemperature", "number", title: "Critical Hot Temp", defaultValue: 90, range: "0..100"
        input "warningHotTemperature", "number", title: "Warning Hot Temp", defaultValue: 80, range: "0..100"
        input "warningColdTemperature", "number", title: "Warning Cold Temp", defaultValue: 60, range: "0..100"
        input "criticalColdTemperature", "number", title: "Critical Cold Temp", defaultValue: 40, range: "0..100"
    }
    section("Alert from humidity sensors:") {
    	input "humidities", "capability.relativeHumidityMeasurement", multiple: true, required: false
        input "criticalHumidity", "number", title: "Critical Humidity", defaultValue: 60, range: "0..100"
        input "warningHumidity", "number", title: "Warning Humidity", defaultValue: 50, range: "0..100"
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
        input "criticalBattery", "number", title: "Critical Battery", defaultValue: 30, range: "0..100"
        input "warningBattery", "number", title: "Warning Battery", defaultValue: 40, range: "0..100"
    }
}

def installed() {
	log.debug("vops Installed with settings: ${settings}")
	initialize()
}

def updated() {
	log.debug("vops Updated with settings: ${settings}")
	unsubscribe()
	initialize()
}

def initialize() {
  if (temperatures != null) {
    log.debug "vops subscribing to temperatures"
    for (t in temperatures) {
      log.debug("vops subscribing to temp ${t}")
    }
    subscribe(temperatures,		"temperature",  	temperatureDeviceHandler)
  }
  
  if (motions != null) { 
    log.debug("vops subscribing to motions")
    subscribe(motions,			"motion",       	motionDeviceHandler)
  }
  
  if (smokedetectors != null) { 
    log.debug("vops subscribing to smoke detectors")
    subscribe(smokedetectors,	"smokeDetector",	smokeDeviceHandler)
  }
  
  if (waterdetectors != null) { 
    log.debug("vops subscribing to water")
    subscribe(waterdetectors,	"water",			waterDeviceHandler)
  }
  
  if (batteries != null) { 
    log.debug("vops subscribing to batteries")
    subscribe(batteries,		"battery",          batteryDeviceHandler)
  }

  if (humidities != null) {
    log.debug("vops subscribing to humidities")
    subscribe(humidities,		"humidity",         humidityDeviceHandler)
  }
}

def addField(fieldName, fieldValue, escapeQuote = false, comma = true) {
  def rval = ""
  
  if (escapeQuote) { rval += '\\' }
  rval = rval + '"' + fieldName 
  if (escapeQuote) { rval += '\\' }
  rval += '": '

  if (escapeQuote) { rval += '\\' }
  rval = rval + '"' + fieldValue 
  if (escapeQuote) { rval += '\\' }
  rval += '"'

  if (comma) { rval += ',' }
  return rval
}

def valueRangeDeviceHandler(evt, curValue, sensorType, tooLowCritical, tooLowWarning, tooHighWarning, tooHighCritical) {
  log.debug("vops in ${sensorType} handler")
  def alertState = "recovery"
  if (curValue >= tooHighCritical || curValue <= tooLowCritical  ) {
    alertState = "critical"
  } else if (curValue >= tooHighWarning || curValue <= tooLowWarning) {
  	alertState = "warning"
  }

  def stateKey = sensorType + "_" + evt.device
  // always send critical and warning
  if (alertState != "recovery") {
    sendAlert(evt, alertState, sensorType)
  } else {
    // only send recovery if previously persisted state is something else
    def savedState = state[stateKey]
    if (savedState != null && savedState != "recovery") {
      log.debug("vops previous state was[${savedState}]")
 	  sendAlert(evt, "recovery", sensorType)
    }
  }
  state[stateKey] = alertState
}

def batteryDeviceHandler(evt) {
  valueRangeDeviceHandler(evt, evt.value.toInteger(), "battery", criticalBattery, warningBattery, 110, 110)
}

def smokeDeviceHandler(evt) {
  log.debug("vops in smoke handler")
  if (evt.value == "detected")	   sendAlert(evt, "critical", "smoke detector")
  else if (evt.value == "tested")  sendAlert(evt, "warning", "smoke detector")
  else if (evt.value == "clear")   ssendAlert(evt, "recovery", "smoke detector")
}

def motionDeviceHandler(evt) {
  log.debug("vops in motion handler")
  sendAlert(evt, "info", "motionDetector")
}

def waterDeviceHandler(evt) {
  log.debug("vops in water handler")
  if (evt.value == "wet") {
	  sendAlert(evt, "critical", "waterSensor")
  } else {
  	  sendAlert(evt, "recovery", "waterSensor")
  }
}

def humidityDeviceHandler(evt) {
  valueRangeDeviceHandler(evt, evt.value.toInteger(), "humiditySensor", 0, 0, warningHumidity, criticalHumidity)
}

def temperatureDeviceHandler(evt) {
  valueRangeDeviceHandler(evt, evt.value.toInteger(), "temperatureSensor", criticalColdTemperature, warningColdTemperature, warningHotTemperature, criticalHotTemperature)
}

def sendAlert(evt, messageType, deviceType) {
  log.debug("vops begin sendAlert")
  try {
      def state = '{'       
      state += addField("date", evt.date, true)
      state += addField("name", evt.name, true)
      state += addField("displayName", evt.displayName, true)
      state += addField("device", evt.device, true)
      state += addField("deviceId", evt.deviceId, true)
      state += addField("value", evt.value, true)
      state += addField("isStateChange", evt.isStateChange(), true)
      state += addField("id", evt.id, true)
      state += addField("description", evt.description, true)
      state += addField("descriptionText", evt.descriptionText, true)
      state += addField("installedSmartAppId", evt.installedSmartAppId, true)
      state += addField("isoDate", evt.isoDate, true)
      state += addField("isDigital", evt.isDigital(), true)
      state += addField("isPhysical", evt.isPhysical(), true)
      state += addField("location", evt.location, true)
      state += addField("locationId", evt.locationId, true)
      state += addField("unit", evt.unit, true)
      state += addField("source", evt.source, true, false)
      state += '}'
    
      def json = '{'
      json += addField("message_type", messageType)
      json += addField("monitoring_tool","smartthings")
      json += addField("entity_id", deviceType + "." + evt.device)
      json += addField("state_message", state, false, false)
      json += '}'
            
      log.debug("vops sending json msg ${json}")

      def params = [
          uri: "https://alert.victorops.com/integrations/generic/20131114/alert/" + app.appSettings.restAPIKey + "/" + app.appSettings.routingKey,
          body: json
      ]
        
      httpPostJson(params)
  } catch (Throwable e ) {
    log.debug("vops error ${e}")
  }
}