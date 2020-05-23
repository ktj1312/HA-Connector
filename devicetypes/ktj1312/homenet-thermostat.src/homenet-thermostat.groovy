/**
 *  HomeNet Thermostat (v.0.0.1)
 *
 *  Authors
 *   - ktj1312@naver.com
 *  Copyright 2018
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

import groovy.json.JsonSlurper

metadata {
    definition (
            name: "HomeNet Thermostat",
            namespace: "ktj1312",
            author: "ktj1312") {

        capability "Actuator"
        capability "Thermostat"
        capability "Temperature Measurement"
        capability "Sensor"
        capability "Refresh"

        command "lowerHeatingSetpoint"
        command "raiseHeatingSetpoint"
        command "switchMode"

        attribute "thermostat", "string"
        attribute "maxHeatingSetpoint", "number"
        attribute "minHeatingSetpoint", "number"
        attribute "deviceTemperatureUnit", "string"
    }

    simulator {
    }

    preferences {
    }

    tiles {
        multiAttributeTile(name:"temperature", type: "generic", width: 6, height: 4){
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label:'${currentValue}°', icon: "st.alarm.temperature.normal",
                        backgroundColors:[
                                // Celsius
                                [value: 0, color: "#153591"],
                                [value: 7, color: "#1e9cbb"],
                                [value: 15, color: "#90d2a7"],
                                [value: 23, color: "#44b621"],
                                [value: 28, color: "#f1d801"],
                                [value: 35, color: "#d04e00"],
                                [value: 37, color: "#bc2323"],
                                // Fahrenheit
                                [value: 40, color: "#153591"],
                                [value: 44, color: "#1e9cbb"],
                                [value: 59, color: "#90d2a7"],
                                [value: 74, color: "#44b621"],
                                [value: 84, color: "#f1d801"],
                                [value: 95, color: "#d04e00"],
                                [value: 96, color: "#bc2323"]
                        ]
                )
            }
        }

        standardTile("lowerHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", action:"lowerHeatingSetpoint", icon:"st.thermostat.thermostat-left"
        }
        valueTile("heatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", label:'${currentValue}° heat', backgroundColor:"#ffffff"
        }
        standardTile("raiseHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", action:"raiseHeatingSetpoint", icon:"st.thermostat.thermostat-right"
        }
        standardTile("mode", "device.thermostatMode", width:2, height:2, inactiveLabel: flase , decoration: "flat") {
            state "off", action:"switchMode", nextState: "updating", icon: "st.thermostat.heating-cooling-off"
            state "away", action:"switchMode",  nextState: "updating", icon: "http://cdn.onlinewebfonts.com/svg/img_458653.png"
            state "auto", action:"switchMode",  nextState: "updating", icon: "st.thermostat.auto"
            state "updating", label:"Updating...", icon: "st.secondary.secondary"
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
        }
        valueTile("ha_url", "device.ha_url", width: 3, height: 1) {
            state "val", label:'${currentValue}', defaultState: true
        }
        valueTile("entity_id", "device.entity_id", width: 3, height: 1) {
            state "val", label:'${currentValue}', defaultState: true
        }

        main "temperature"
    }
}

def off() { setThermostatMode("off") }

def away() { setThermostatMode("away") }

def auto() { setThermostatMode("auto") }

def switchMode() {
    def currentMode = device.currentValue("thermostatMode")
    def supportedModes = ["off","auto","away"]

    def next = { supportedModes[supportedModes.indexOf(it) + 1] ?: supportedModes[0] }
    def nextMode = next(currentMode)

    log.debug "now ${currentMode} change to ${nextMode}"

    def request = [
            "command_type":"mode",
            "state":nextMode
    ]
    commandToHA(request)
}

def generateModeEvent(mode) {
    sendEvent(
            name: "thermostatMode",
            value: mode,
            data:["off","auto","away"],
            isStateChange: true,
            displayed: true,
            descriptionText: "$device.displayName is in ${mode} mode")

}

def switchToMode(String mode) {
    log.debug "setThermostatMode($mode)"
    generateModeEvent(mode)
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def setStatus(value){
    if(state.entity_id == null){
        return
    }
    log.debug "Status[${state.entity_id}] >> ${value}"

    switchToMode(value.state)
    sendEvent(name: "heatingSetpoint", value: value.set_temp, displayed: true)
    sendEvent(name: "temperature", value: value.current_temp , displayed: true)
}

def setHASetting(url, deviceId){
    state.app_url = url
    state.entity_id = deviceId

    sendEvent(name: "ha_url", value: state.app_url, displayed: false)
    sendEvent(name: "entity_id", value: state.entity_id, displayed: false)
}

def raiseHeatingSetpoint() {
    int newSetpoint = device.currentValue("heatingSetpoint") + 1
    log.debug "Setting heat set point up to: ${newSetpoint}"
    setHeatingSetpoint(newSetpoint)
}

def lowerHeatingSetpoint() {
    int newSetpoint = device.currentValue("heatingSetpoint") - 1
    log.debug "Setting heat set point down to: ${newSetpoint}"
    setHeatingSetpoint(newSetpoint)
}

def setHeatingSetpoint(temp) {
    def request = [
            "command_type":"temperature",
            "state":"off" ,
            "current_temp": device.currentValue("temperature"),
            "set_temp": temp
    ]
    commandToHA(request)
}

def refresh(){
    log.debug "Refresh"
    def options = [
            "method": "GET",
            "path": "/api/states/${state.entity_id}",
            "headers": [
                    "HOST": state.app_url,
                    "Content-Type": "application/json"
            ]
    ]
    sendCommand(options, callback)
}

def commandToHA(command){
    log.debug "Command[${state.entity_id}] >> ${command}"

    def request_body = [
            "entity_id":"${state.entity_id}",
            "device_id":"THERMOSTAT"
    ]

    request_body << command

    def options = [
            "method": "POST",
            "path": "/api/states/" + state.entity_id,
            "headers": [
                    "HOST": state.app_url,
                    "Content-Type": "application/json"
            ],
            "body":request_body
    ]
    sendCommand(options, null)
}

def callback(physicalgraph.device.HubResponse hubResponse){
    def msg
    try {
        msg = parseLanMessage(hubResponse.description)
        def jsonObj = new JsonSlurper().parseText(msg.body)
        setStatus(jsonObj)
    } catch (e) {
        log.error "Exception caught while parsing data: "+e;
    }
}

def updated() {
}

def sendCommand(options, _callback){
    def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
    sendHubCommand(myhubAction)
}
