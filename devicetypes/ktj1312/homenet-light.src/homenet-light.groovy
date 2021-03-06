/**
 *  HomeNet Light (v.0.0.1)
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
    definition (name: "HomeNet Light", namespace: "ktj1312", author: "ktj1312") {
        capability "Switch"						//"on", "off"
        capability "Refresh"
        capability "Light"

        attribute "lastCheckin", "Date"

        command "setStatus"
    }

    simulator {
    }

    preferences {
        input name: "baseValue", title:"HA On Value" , type: "string", required: true, defaultValue: "on"
    }

    tiles {
        multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"

                attributeState "turningOn", label:'${name}', action:"off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
            }
        }

        valueTile("lastOn_label", "", decoration: "flat") {
            state "default", label:'Last\nOn'
        }
        valueTile("lastOn", "device.lastOn", decoration: "flat", width: 3, height: 1) {
            state "default", label:'${currentValue}'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
        }
        valueTile("lastOff_label", "", decoration: "flat") {
            state "default", label:'Last\nOff'
        }
        valueTile("lastOff", "device.lastOff", decoration: "flat", width: 3, height: 1) {
            state "default", label:'${currentValue}'
        }

        valueTile("ha_url", "device.ha_url", width: 3, height: 1) {
            state "val", label:'${currentValue}', defaultState: true
        }

        valueTile("entity_id", "device.entity_id", width: 3, height: 1) {
            state "val", label:'${currentValue}', defaultState: true
        }
    }
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

    def switchBaseValue = "on"
    if(baseValue){
        switchBaseValue = baseValue
    }

    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    def _value = (switchBaseValue == value.state ? "on" : "off")

    if(device.currentValue("switch") != _value){
        sendEvent(name: (_value == "on" ? "lastOn" : "lastOff"), value: now, displayed: true )
    }
    sendEvent(name: "switch", value:_value)
    sendEvent(name: "lastCheckin", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: false)
    sendEvent(name: "entity_id", value: state.entity_id, displayed: false)
}

def setHASetting(url, deviceId){
    state.app_url = url
    state.entity_id = deviceId

    sendEvent(name: "ha_url", value: state.app_url, displayed: false)
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

def on(){
    commandToHA("on")
}

def off(){
    commandToHA("off")
}

def commandToHA(cmd){
    log.debug "Command[${state.entity_id}] >> ${cmd}"
    def temp = state.entity_id.split("\\.")
    def options = [
            "method": "POST",
            "path": "/api/states/" + state.entity_id,
            "headers": [
                    "HOST": state.app_url,
                    "Content-Type": "application/json"
            ],
            "body":[
                    "entity_id":"${state.entity_id}",
                    "device_id":"LIGHT",
                    "state":cmd
            ]
    ]
    sendCommand(options, null)
}

def callback(physicalgraph.device.HubResponse hubResponse){
    def msg
    try {
        msg = parseLanMessage(hubResponse.description)
        def jsonObj = new JsonSlurper().parseText(msg.body)
        setStatus(jsonObj.state)
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
