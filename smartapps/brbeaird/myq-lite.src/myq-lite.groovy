/**
 *  MyQ Lite
 *
 *  Copyright 2015 Jason Mok/Brian Beaird
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
 *  Last Updated : 05/30/2016
 *
 */
definition(
	name: "MyQ Lite",
	namespace: "brbeaird",
	author: "Jason Mok/Brian Beaird",
	description: "Integrate MyQ with Smartthings",
	category: "SmartThings Labs",
	iconUrl:   "http://smartthings.copyninja.net/icons/MyQ@1x.png",
	iconX2Url: "http://smartthings.copyninja.net/icons/MyQ@2x.png",
	iconX3Url: "http://smartthings.copyninja.net/icons/MyQ@3x.png"
)

preferences {
	page(name: "prefLogIn", title: "MyQ")    
	page(name: "prefListDevices", title: "MyQ")
    page(name: "prefSensor1", title: "MyQ")
    page(name: "prefSensor2", title: "MyQ")
    page(name: "prefSensor3", title: "MyQ")
    page(name: "prefSensor4", title: "MyQ")
}

/* Preferences */
def prefLogIn() {
	def showUninstall = username != null && password != null 
	return dynamicPage(name: "prefLogIn", title: "Connect to MyQ", nextPage:"prefListDevices", uninstall:showUninstall, install: false) {
		section("Login Credentials"){
			input("username", "email", title: "Username", description: "MyQ Username (email address)")
			input("password", "password", title: "Password", description: "MyQ password")
		}
		section("Gateway Brand"){
			input(name: "brand", title: "Gateway Brand", type: "enum",  metadata:[values:["Liftmaster","Chamberlain","Craftsman"]] )
		}
	}
}

def prefListDevices() {
	if (forceLogin()) {
		def doorList = getDoorList()		
		if ((doorList)) {
			return dynamicPage(name: "prefListDevices",  title: "Devices", nextPage:"prefSensor1", install:false, uninstall:true) {
				if (doorList) {
					section("Select which garage door/gate to use"){
						input(name: "doors", type: "enum", required:false, multiple:true, metadata:[values:doorList])
					}
				} 
			}
		} else {
			def devList = getDeviceList()
			return dynamicPage(name: "prefListDevices",  title: "Error!", install:false, uninstall:true) {
				section(""){
					paragraph "Could not find any supported device(s). Please report to author about these devices: " +  devList
				}
			}
		}  
	} else {
		return dynamicPage(name: "prefListDevices",  title: "Error!", install:false, uninstall:true) {
			section(""){
				paragraph "The username or password you entered is incorrect. Try again. " 
			}
		}  
	}
}


def prefSensor1() {
	log.debug "Doors chosen: " + doors
    
    //Set defaults
    def nextPage = ""
    def showInstall = true
    def titleText = ""
    
    //Determine if we have multiple doors and need to send to another page
    if (doors instanceof String){ //simulator seems to just make a single door a string. For that reason we have this weird check.
    	log.debug "Single door detected (string)."
        titleText = "Select Contact Sensor for Door 1 (" + state.data[doors].name + ")"
    }
    else if (doors.size() == 1){
    	log.debug "Single door detected (array)."
        titleText = "Select Contact Sensor for Door 1 (" + state.data[doors[0]].name + ")"
    }
    else{
    	log.debug "Multiple doors detected."
        nextPage = "prefSensor2"
        titleText = "Select Contact Sensor for Door 1 (" + state.data[doors[0]].name + ")"
        showInstall = false;
    }    
    
    return dynamicPage(name: "prefSensor1",  title: "Sensors", nextPage:nextPage, install:showInstall, uninstall:true) {        
        section(titleText){			
			input(name: "door1Sensor", title: "Contact Sensor", type: "capability.contactSensor", required: "true", multiple: "false")			
		}
    }
}

def prefSensor2() {	   
    def nextPage = ""
    def showInstall = true
    def titleText = "Contact Sensor for Door 2 (" + state.data[doors[1]].name + ")"
     
    if (doors.size() > 2){
    	nextPage = "prefSensor3"
        showInstall = false;
    }
    
    return dynamicPage(name: "prefSensor2",  title: "Sensors", nextPage:nextPage, install:showInstall, uninstall:true) {
        section(titleText){			
			input(name: "door2Sensor", title: "Contact Sensor", type: "capability.contactSensor", required: "true", multiple: "false")			
		}
    }
}

def prefSensor3() {	   
    def nextPage = ""
    def showInstall = true
    def titleText = "Contact Sensor for Door 3 (" + state.data[doors[2]].name + ")"
     
    if (doors.size() > 3){
    	nextPage = "prefSensor4"
        showInstall = false;
    }
    
    return dynamicPage(name: "prefSensor3",  title: "Sensors", nextPage:nextPage, install:showInstall, uninstall:true) {
        section(titleText){			
			input(name: "door3Sensor", title: "Contact Sensor", type: "capability.contactSensor", required: "true", multiple: "false")			
		}
    }
}

def prefSensor4() {	   
	def titleText = "Contact Sensor for Door 4 (" + state.data[doors[3]].name + ")"
    return dynamicPage(name: "prefSensor4",  title: "Sensors", install:true, uninstall:true) {
        section(titleText){			
			input(name: "door4Sensor", title: "Contact Sensor", type: "capability.contactSensor", required: "true", multiple: "false")			
		}
    }
}



/* Initialization */
def installed() { initialize() }

def updated() { 
	unsubscribe()
	initialize()
}

def uninstalled() {}	

def initialize() {    
	login()
    state.sensorMap = [:]
    
    // Get initial device status in state.data
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]
    
	// Create selected devices
	def doorsList = getDoorList()
	//def lightsList = getLightList()
	def selectedDevices = [] + getSelectedDevices("doors")
  
	selectedDevices.each { 
    	log.debug "Creating child device: " + it
		if (!getChildDevice(it)) {
			if (it.contains("GarageDoorOpener")) { addChildDevice("brbeaird", "MyQ Garage Door Opener", it, null, ["name": "MyQLite: " + doorsList[it]]) }			
		} 
	}

	// Remove unselected devices
	def deleteDevices = (selectedDevices) ? (getChildDevices().findAll { !selectedDevices.contains(it.deviceNetworkId) }) : getAllChildDevices()
	deleteDevices.each { deleteChildDevice(it.deviceNetworkId) }    
    
    
    //Create subscriptions
    if (door1Sensor)
        subscribe(door1Sensor, "contact", sensorHandler)    
    if (door2Sensor)    	
        subscribe(door2Sensor, "contact", sensorHandler)
    if (door3Sensor)        
        subscribe(door3Sensor, "contact", sensorHandler)        
    if (door4Sensor)    	
        subscribe(door4Sensor, "contact", sensorHandler)
        
	//Subscribe to mode changes and sunrise/sunset to help keep doors in sync
	subscribe(location, "sunrise", sensorHandler)
	subscribe(location, "sunset", sensorHandler)
	subscribe(location, "mode", sensorHandler)
	subscribe(location, "sunriseTime", sensorHandler)
	subscribe(location, "sunsetTime", sensorHandler)
    
    //Set initial values
    syncDoorsWithSensors()    

}

def syncDoorsWithSensors(child){	
    def firstDoor = doors[0]
        
    //Handle single door (sometimes it's just a dumb string thanks to the simulator)
    if (doors instanceof String)
    firstDoor = doors
    
    if (door1Sensor)
    	updateDoorStatus(firstDoor, door1Sensor, child)
    if (door2Sensor)
    	updateDoorStatus(doors[1], door2Sensor, child)
    if (door3Sensor)
    	updateDoorStatus(doors[2], door2Sensor, child)
    if (door4Sensor)
        updateDoorStatus(doors[3], door2Sensor, child)
}


def updateDoorStatus(doorDNI, sensor, child){
	
    //Get latest activity timestamp for the sensor
    def eventsSinceYesterday = sensor.eventsSince(new Date() - 1)    
    def latestEvent = eventsSinceYesterday[0]?.date
    def value = sensor.currentcontact
    
    //Write to child log if this was initiated from one of the doors
    if (child)
    	child.log("Updating door " + doorDNI + " with " + value + " last activity: " + latestEvent)
    
    log.debug "Updating door " + doorDNI + " with " + value + " last activity: " + latestEvent
    def doorToUpdate = getChildDevice(doorDNI)
    doorToUpdate.updateDeviceStatus(value)
	doorToUpdate.updateDeviceLastActivity(latestEvent)    
}

def refresh(child){	
    def door = child.device.deviceNetworkId
    child.log("refresh called from " + door)
    syncDoorsWithSensors(child)
}

def sensorHandler(evt) {    
    log.debug "Sensor change detected: Event name  " + evt.name + " value: " + evt.value    
    syncDoorsWithSensors()    
}

def getSelectedDevices( settingsName ) { 
	def selectedDevices = [] 
	(!settings.get(settingsName))?:((settings.get(settingsName)?.getAt(0)?.size() > 1)  ? settings.get(settingsName)?.each { selectedDevices.add(it) } : selectedDevices.add(settings.get(settingsName))) 
	return selectedDevices 
} 

/* Access Management */
private forceLogin() {
	//Reset token and expiry
	state.session = [ brandID: 0, brandName: settings.brand, securityToken: null, expiration: 0 ]
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]
	return doLogin()
}

private login() { return (!(state.session.expiration > now())) ? doLogin() : true }

private doLogin() { 
	apiGet("/api/user/validate", [username: settings.username, password: settings.password] ) { response ->
		log.debug "got login response: " + response
        if (response.status == 200) {
			if (response.data.SecurityToken != null) {
				state.session.brandID = response.data.BrandId
				state.session.brandName = response.data.BrandName
				state.session.securityToken = response.data.SecurityToken
				state.session.expiration = now() + 150000
				return true
			} else {
				return false
			}
		} else {
			return false
		}
	} 	
}

// Listing all the garage doors you have in MyQ
private getDoorList() { 	    
	def deviceList = [:]
	apiGet("/api/v4/userdevicedetails/get", []) { response ->
		if (response.status == 200) {
        //log.debug "response data: " + response.data.Devices
        //sendAlert("response data: " + response.data.Devices)
			response.data.Devices.each { device ->
				// 2 = garage door, 5 = gate, 7 = MyQGarage(no gateway), 17 = Garage Door Opener WGDO
				if (device.MyQDeviceTypeId == 2||device.MyQDeviceTypeId == 5||device.MyQDeviceTypeId == 7||device.MyQDeviceTypeId == 17) {
					log.debug "Found door: " + device.MyQDeviceId
                    def dni = [ app.id, "GarageDoorOpener", device.MyQDeviceId ].join('|')
					def description = ''
                    def doorState = ''
                    def updatedTime = ''
                    device.Attributes.each { 
						
                        if (it.AttributeDisplayName=="desc")	//deviceList[dni] = it.Value                        	
                        {
                        	description = it.Value
                        }
                        	
						if (it.AttributeDisplayName=="doorstate") { 
                        	doorState = it.Value
                            updatedTime = it.UpdatedTime							
						}
					}
                    
                    //Ignore any doors with blank descriptions
                    if (description != ''){
                        log.debug "adding door: " + description + "type: " + device.MyQDeviceTypeId + " status: " + doorState
                        deviceList[dni] = description
                        state.data[dni] = [ status: doorState, lastAction: updatedTime, name: description ]
                    }
				}
			}
		}
	}    
	return deviceList
}

private getDeviceList() { 	    
	def deviceList = []
	apiGet("/api/v4/userdevicedetails/get", []) { response ->
		if (response.status == 200) {
			response.data.Devices.each { device ->
				log.debug "MyQDeviceTypeId : " + device.MyQDeviceTypeId.toString()
				if (!(device.MyQDeviceTypeId == 1||device.MyQDeviceTypeId == 2||device.MyQDeviceTypeId == 3||device.MyQDeviceTypeId == 5||device.MyQDeviceTypeId == 7)) {					
                    device.Attributes.each { 
						def description = ''
                        def doorState = ''
                        def updatedTime = ''
                        if (it.AttributeDisplayName=="desc")	//deviceList[dni] = it.Value
                        	description = it.Value
						
                        //Ignore any doors with blank descriptions
                        if (description != ''){
                        	log.debug "found device: " + description
                        	deviceList.add( device.MyQDeviceTypeId.toString() + "|" + device.TypeID )
                        }
					}
				}
			}
		}
	}    
	return deviceList
}

/* api connection */
// get URL 
private getApiURL() {
	if (settings.brand == "Craftsman") {
		return "https://craftexternal.myqdevice.com"
	} else {
		return "https://myqexternal.myqdevice.com"
	}
}

private getApiAppID() {
	if (settings.brand == "Craftsman") {
		return "QH5AzY8MurrilYsbcG1f6eMTffMCm3cIEyZaSdK/TD/8SvlKAWUAmodIqa5VqVAs"
	} else {
		return "JVM/G9Nwih5BwKgNCjLxiFUQxQijAebyyg8QUHr7JOrP+tuPb8iHfRHKwTmDzHOu"
	}
}
	
// HTTP GET call
private apiGet(apiPath, apiQuery = [], callback = {}) {	
	// set up query
	apiQuery = [ appId: getApiAppID() ] + apiQuery
	if (state.session.securityToken) { apiQuery = apiQuery + [SecurityToken: state.session.securityToken ] }
       
	try {
		httpGet([ uri: getApiURL(), path: apiPath, query: apiQuery ]) { response -> callback(response) }
	}	catch (SocketException e)	{
		//sendAlert("API Error: $e")
        log.debug "API Error: $e"
	}
}

// HTTP PUT call
private apiPut(apiPath, apiBody = [], callback = {}) {    
	// set up body
	apiBody = [ ApplicationId: getApiAppID() ] + apiBody
	if (state.session.securityToken) { apiBody = apiBody + [SecurityToken: state.session.securityToken ] }
    
	// set up query
	def apiQuery = [ appId: getApiAppID() ]
	if (state.session.securityToken) { apiQuery = apiQuery + [SecurityToken: state.session.securityToken ] }
    
	try {
		httpPut([ uri: getApiURL(), path: apiPath, contentType: "application/json; charset=utf-8", body: apiBody, query: apiQuery ]) { response -> callback(response) }
	} catch (SocketException e)	{
		log.debug "API Error: $e"
	}
}

// Get Device ID
def getChildDeviceID(child) {
	return child.device.deviceNetworkId.split("\\|")[2]
}

// Get single device status
def getDeviceStatus(child) {
	return state.data[child.device.deviceNetworkId].status
}

// Get single device last activity
def getDeviceLastActivity(child) {
	return state.data[child.device.deviceNetworkId].lastAction.toLong()
}

// Send command to start or stop
def sendCommand(child, attributeName, attributeValue) {
	if (login()) {	    	
		//Send command
		apiPut("/api/v4/deviceattribute/putdeviceattribute", [ MyQDeviceId: getChildDeviceID(child), AttributeName: attributeName, AttributeValue: attributeValue ]) 	
		return true
	} 
}