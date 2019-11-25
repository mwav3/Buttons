// vim: set filetype=groovy tabstop=2 shiftwidth=2 softtabstop=2 expandtab smarttab :
/**
 *  Leviton Scene or Zone Controller
 * 
 *  Leviton VRCZ4 Vizia RF + 4-Button Zone Controller
 *  https://products.z-wavealliance.org/products/374
 *
 *  Leviton VRCS4 Vizia RF + 4-Button Remote Scene Controller
 *  https://products.z-wavealliance.org/products/318
 *
 *  Copyright 2019 Brian Aker <brian@tangent.org>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
 
import physicalgraph.*

String getDriverVersion() {
  return "v1.0.3"
}
 
metadata {
	definition (name: "Leviton Scene or Zone Controller", namespace: "tangentorgthings", author: "Brian Aker", cstHandler: true, ocfDeviceType: "x.com.st.d.remotecontroller", vid: "generic-button-4") {
		capability "Button"
		capability "Configuration"
        capability "Sensor"

		fingerprint mfr: "001D", prod: "0261", model: "0702", deviceJoinName: "Leviton Zone Controller"
		fingerprint mfr: "001D", prod: "0261", model: "0802", deviceJoinName: "Leviton Scene Controller"
	}

	simulator {
		// TODO: define status and reply messages here
	}
    
    preferences {
        input name: "buttonOneEnable", type: "bool", title: "Enable Button One", description: "Enable button one events for the hub ", required: false,  defaultValue: true
        input name: "buttonTwoEnable", type: "bool", title: "Enable Button Two", description: "Enable button two events for the hub ", required: false,  defaultValue: true
        input name: "buttonThreeEnable", type: "bool", title: "Enable Button Three", description: "Enable button three events for the hub ", required: false,  defaultValue: true
        input name: "buttonFourEnable", type: "bool", title: "Enable Button Four", description: "Enable button four events for the hub ", required: false,  defaultValue: true
    }

	tiles {
        standardTile("button", "device.button", width: 2, height: 2) {
            state "default", label: " ", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
            state "button 1", label: "1", icon: "st.Weather.weather14", backgroundColor: "#79b821"
            state "button 2", label: "2", icon: "st.Weather.weather14", backgroundColor: "#79b821"
            state "button 3", label: "3", icon: "st.Weather.weather14", backgroundColor: "#79b821"
            state "button 4", label: "4", icon: "st.Weather.weather14", backgroundColor: "#79b821"
		}

        main "button"
        details(["button"])	
	}
}

def getCommandClassVersions() {
  [
    0x20: 1,  // Basic
    0x2D: 1,  // Scene Controller Conf
    0x72: 1,  // Manufacturer Specific
    0x73: 1,  // Powerlevel
    // 0x77: 1,  // Node Naming
    0x82: 1,  // Hail
    0x85: 2,  // Association  0x85  V1 V2
    0x86: 1,  // Version
    0x91: 1,  // Man Prop
    // Note: Controlled
    0x2B: 1,  // SceneActivation
    0x2C: 1,  // Scene Actuator Conf
    0x25: 1,  //
    0x22: 1,  // Application Status
    0x7C: 1,  // Remote Association Activate
    //    0x91: 1, // Manufacturer Proprietary
    // Stray commands that show up
    0x54: 1,  // Network Management Primary
  ]
}

// parse events into attributes
def parse(String description) {
	def result = []
    
	log.debug "Parsing '${description}'"
	// TODO: handle 'button' attribute
	// TODO: handle 'numberOfButtons' attribute
	// TODO: handle 'supportedButtonValues' attribute
    

    if (description && description.startsWith("Err")) {
        log.error "parse error: ${description}"
	} else if (! description) {
        log.error "parse() passed NULL description"
    } else if (description != "updated") {
        def cmd = zwave.parse(description, getCommandClassVersions())
        if (cmd) {
            zwaveEvent(cmd, result)
        } else {
            cmd = zwave.parse(description)
            zwaveEvent(cmd, result)
        }
    }

    return result
}

def buttonEvent(Integer button_pressed, isHeld = "pushed") {
    log.debug "buttonEvent: $button_pressed  held: $isHeld"

    if (isOn(button_pressed)) {
        sendEvent(name: "button", value: "$isHeld", data: [buttonNumber: button_pressed], descriptionText: "$device.displayName $exec_cmd button $button_pressed was pushed", isStateChange: true, type: "physical")
    } else {
        sendEvent(name: "button", value: "default", descriptionText: "$device.displayName $exec_cmd button released", isStateChange: true, type: "physical")
    }
}

def zwaveEvent(zwave.commands.basicv1.BasicSet cmd, result) {
    log.debug("$device.displayName $cmd")
    
    if (value == 0) {
        state.lastScene = 0
        state.dimmingDuration = 0xff
        state.repeatCount = 0
        state.repeatStart = now()

    	buttonEvent(0)
    }
}

def zwaveEvent(zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd, result) {	
    log.debug("$device.displayName $cmd")
}

def zwaveEvent(zwave.commands.switchmultilevelv3.SwitchMultilevelStopLevelChange cmd, result) {	
    log.debug("$device.displayName $cmd")
}

def zwaveEvent(zwave.commands.sceneactivationv1.SceneActivationSet cmd, result) {
    log.debug("$device.displayName $cmd")

	// The controller will send a unicast and a multicast message if it is close enough to the hub
	if (state.lastScene == cmd.sceneId && (state.repeatCount < 4) && (now() - state.repeatStart < 2000)) {
        state.repeatCount = state.repeatCount + 1
        createEvent([:])
    } else {
    	// If the button was really pressed, store the new scene and handle the button press
        state.lastScene = cmd.sceneId
        state.dimmingDuration = cmd.dimmingDuration
        state.repeatCount = 0
        state.repeatStart = now()

        buttonEvent(cmd.sceneId)
    }
}

def zwaveEvent(zwave.commands.sceneactuatorconfv1.SceneActuatorConfGet cmd, result) {
    log.debug("$device.displayName $cmd")
    log.debug("$device.displayName lastScene: $state.lastScene")

    if ( cmd.sceneId == 0 ) {
        result << delayBetween([
            zwave.sceneActuatorConfV1.sceneActuatorConfReport(
                dimmingDuration: 0xff,
                level: isOn(state.lastScene) ? 0x63 : 0, // If the scene one through four then assume 99 
                sceneId: isOn(state.lastScene) ? state.lastScene : 0)
        ])
    } else if ( cmd.sceneId ) {
        // This is undefined behavior at the moment
        result << delayBetween([
            zwave.sceneActuatorConfV1.sceneActuatorConfReport(
                dimmingDuration: 0xff,
                level: 0,
                sceneId: cmd.sceneId)
        ])
    }
}


def zwaveEvent(zwave.commands.scenecontrollerconfv1.SceneControllerConfReport cmd, result) {
    log.debug("$device.displayName $cmd")
    
    if (cmd.groupId != cmd.sceneId) {
        result << response(delayBetween([
            zwave.sceneControllerConfV1.sceneControllerConfSet(dimmingDuration: 0xff, groupId: cmd.groupId, sceneId: cmd.groupId).format(),
            zwave.sceneControllerConfV1.sceneControllerConfGet(groupId: cmd.groupId).format(),
        ]))

        updateDataValue("Button #${cmd.groupId}", "mis-configured")
        return
    }

    updateDataValue("Button #${cmd.groupId}", "configured")
}

def zwaveEvent(zwave.commands.associationv2.AssociationGroupingsReport cmd, result) {
    log.debug("$device.displayName $cmd")
    
    def commands = []

    result << createEvent(name: "numberOfButtons", value: cmd.supportedGroupings, isStateChange: true, displayed: false)

    if (cmd.supportedGroupings) {
        for (def x = 1; x <= cmd.supportedGroupings; x++) {
            commands << zwave.associationV1.associationGet(groupingIdentifier: x).format();
        }
        
        result << response( delayBetween(commands, 2000) )
    }
}

def zwaveEvent(zwave.commands.associationv2.AssociationReport cmd, result) {
    log.debug("$device.displayName $cmd")

    String string_of_assoc = ""
    if (cmd.nodeId) {
        string_of_assoc = cmd.nodeId.join(",")
    }
    String final_string = "${string_of_assoc}"
    
    String group_association_name =  "Group #${cmd.groupingIdentifier}"
    updateDataValue("$group_association_name", "${final_string}");
}

def zwaveEvent(zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd, result) {
    log.debug("$device.displayName $cmd")

    String manufacturerCode = String.format("%04X", cmd.manufacturerId)
    String productTypeCode = String.format("%04X", cmd.productTypeId)
    String productCode = String.format("%04X", cmd.productId)
    
    if ( cmd.manufacturerName ) {
        updateDataValue("manufacturer", "${cmd.manufacturerName}")
    }

    updateDataValue("Manufacturer ID", manufacturerCode)
    updateDataValue("Product Type", productTypeCode)
    updateDataValue("Product Code", productCode)
    
    if ( cmd.manufacturerId == 29 && cmd.productId == 609 ) {
    	result << configure_device()
    }
}

def zwaveEvent(zwave.commands.versionv1.VersionReport cmd, result) {
    log.debug("$device.displayName $cmd")

    def zWaveProtocolVersion = "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
    def zWaveFirmwareVersion = cmd.applicationVersion+'.'+cmd.applicationSubVersion
    
    updateDataValue("Firmware Version", "${zWaveFirmwareVersion}")
    updateDataValue("Z-Wave Protocol Version", "${zWaveProtocolVersion}")
}

def zwaveEvent(zwave.commands.powerlevelv1.PowerlevelReport cmd, result) {
    log.debug("$device.displayName $cmd")
    
    String device_power_level = (cmd.powerLevel > 0) ? "minus${cmd.powerLevel}dBm" : "NormalPower"
    updateDataValue("Powerlevel Report", "Power: ${device_power_level}, Timeout: ${cmd.timeout}")
}

def zwaveEvent(zwave.Command cmd, result) {
    log.warn "$device.displayName no implementation of $cmd"
}

private configure_device() {
    log.debug "$device.displayName configure_device()"
    
    sendEvent(name: "numberOfButtons", value: 4, displayed: false)
    
    def commands = []
    
    for (def x = 1; x <= 4; x++) {
        Boolean enable_button = true
        
        updateDataValue("Button #${x}", "unknown")
        
        switch (x) {
            case 1:
                if (settings.buttonOneEnable == false) {
                    enable_button = false
                }
            break
            case 2:
                if (settings.buttonTwoEnable == false) {
                    enable_button = false
                }
            break
            case 3:
                if (settings.buttonThreeEnable == false) {
                    enable_button = false
                }
            break
            case 4:
                if (settings.buttonFourEnable == false) {
                    enable_button = false
                }
            break
        }
        
        if (enable_button) {
            log.debug "$device.displayName enabling button ${x}"
            commands << zwave.associationV1.associationSet(groupingIdentifier: x, nodeId: zwaveHubNodeId).format()
        } else {
            commands << zwave.associationV1.associationRemove(groupingIdentifier: x, nodeId: zwaveHubNodeId).format()
        }
	
        commands << zwave.associationV1.associationGet(groupingIdentifier: x).format()
    }
    
    for (def x = 1; x <= 8; x++) {
        commands << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId: x).format()
    }
    
    response(delayBetween(commands, 1000))
}

def configure() {
	log.debug "$device.displayName configure()"
    def commands = []
    
    commands << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
    // commands << zwave.versionV1.versionGet().format()
    
    response(delayBetween(commands, 1000))
}

def updated() {
	log.debug "$device.displayName updated()"
    configure()
}

private isOn(Integer scene_id) {
    if (scene_id >= 1 && scene_id <= 4) {
        return true
    }
    
    return false
}
