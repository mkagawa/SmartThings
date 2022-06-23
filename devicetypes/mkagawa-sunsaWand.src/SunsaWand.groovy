/**
 *  Sunsa Wand Driver
 *
 *  Copyright 2022 Masa Kagawa
 *
 *  Version 1.0.0 - Initial release 6/15/2022
 * 
 *  Uses code from SmartThings
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
metadata {
        definition (name: "Sunsa Wand Driver", namespace: "mkagawa", author: "SmartThings") {
        capability "Switch"
        capability "Refresh"
        capability "Switch Level"
        capability "Actuator"	//included to give compatibility with ActionTiles
        capability "Sensor"		//included to give compatibility with ActionTiles
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
    			attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn"
		      	attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821", nextState: "turningOff"
				attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn"
		      	attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#79b821", nextState: "turningOff"
        	}
        		tileAttribute("device.level", key: "SLIDER_CONTROL") {
            		attributeState "level", action:"switch level.setLevel"
        		}
        		tileAttribute("level", key: "SECONDARY_CONTROL") {
              		attributeState "level", label: 'Light dimmed to ${currentValue}%'
        		}    
		}
        valueTile("lValue", "device.level", inactiveLabel: true, height:2, width:2, decoration: "flat") {  
			state "levelValue", label:'${currentValue}%', unit:"", backgroundColor: "#53a7c0"  
        }  
    
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "switch"
		details(["switch","lValue","refresh"])

	}
}

def parse(String description) {
    log.info "parse is called ${description}"
}

def on() {
	sendEvent(name: "switch", value: "on")
    log.info "Open/On"
    apiPut(100)
}

def off() {
	sendEvent(name: "switch", value: "off")
    log.info "Close/Off"
    apiPut(0)
}

import java.net.URI
def myParseDeviceAddress() {
      URI u = device.deviceNetworkId.toURI()
      def matched = (u.path =~ "(/api/public/\\d+/devices)/(\\d+)")[0]
      def devId = matched[2].toString()
      def getPath = matched[1]
      [
        host: "${u.scheme}//:${u.host}",
        getUri: "${u.scheme}://${u.host}${getPath}?${u.query}",
        putUri: "${u.scheme}://${u.host}${u.path}?${u.query}",
        devId: matched[2].toString(),
        uri: u
      ]
}

def apiGet() {
    try {
      def parsed = myParseDeviceAddress()
      def params = [ uri: parsed.getUri ]
      log.info(params)
      httpGet(params) { resp ->
          log.debug "devId = ${parsed.devId} response data: ${resp.data}"
          if(resp.data.deviceCount > 0) {
             resp.data.devices.findAll { 
                 it.idDevice.toString() == parsed.devId 
             }
          }
      }
    } catch (e) {
        log.error "something went wrong: $e"
    }    
}
def apiPut(int position) {
    try {
      def parsed = myParseDeviceAddress()
      int level = 100 - position
      def params = [
         uri: parsed.putUri,
         contentType: "application/json",
         body:["Position":level]
      ]
      log.info(params)
      httpPutJson(params) { resp ->
          if(resp.data.device.idDevice) {
              level = 100 - resp.data.device.position
              log.warn "${resp.data.device.name} => level = ${level}"
              sendEvent(name:"level", value:level)
              resp
          }
      }
    } catch (e) {
        log.error "something went wrong httpPut: $e"
    }    
}

def setLevel(int val){
    log.info "setLevel $val"
    def devInfo = apiGet()
    log.info("dev: ${devInfo}")
    // make sure we don't drive switches past allowed values (command will hang device waiting for it to
    // execute. Never commes back)
    if (val < 0){ val = 0 }
    if( val > 100){ val = 100 }
    
    if (val == 0) {
        if( device.getDataValue("level") > 0 ) {
    	  off()
        }
    }
    else if(val == 100)
    {
        if( device.getDataValue("level") < 100 ) {
    	  on()
        }
    } 
    else 
    {
 		apiPut(val)
    }
}

def refresh() {
    log.info "refresh ${device.deviceNetworkId}"
    def devInfo = apiGet()
    log.info("device = $devInfo")
    sendEvent(name:"level", value:level)

}   
