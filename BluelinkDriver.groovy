/**
 *  Hyundai Bluelink Driver
 *
 *  Author: Eric Will (corinuss)
 *
 *  Copyright (c) 2021 tyuhl
 *  Copyright (c) 2025,2026 Eric Will
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  History:
 *  12/31/25 - (corinuss) Started cleanup work for personal use.  See app for details.
 */

metadata {
	definition(
			name: "Bluelink Communicator Vehicle",
			namespace: "Bluelink",
			description: "Vehicle driver represented in the Hyundai Bluelink web services",
			importUrl: "https://raw.githubusercontent.com/corinuss/Hyundai-Bluelink/dev/BluelinkDriver.groovy",
			author: "corinuss")
			{
				capability "Initialize"
				capability "Actuator"
				capability "Sensor"
				capability "Refresh"
				capability "Lock"

				attribute "NickName", "string"
				attribute "VIN", "string"
				attribute "Model", "string"
				attribute "Trim", "string"
				attribute "RegId", "string"
				attribute "Odometer", "string"
				attribute "vehicleGeneration", "string"
				attribute "brandIndicator", "string"
				attribute "Engine", "string"
				attribute "lock", "string"
				attribute "Hood", "string"
				attribute "Trunk", "string"
				attribute "LastRefreshTime", "string"
				attribute "locLatitude", "string"
				attribute "locLongitude", "string"
				attribute "Range", "string"
				attribute "isEV", "string"
				attribute "BatterySoC", "string"
				attribute "locUpdateTime", "string"
				attribute "EVBattery", "string"
				attribute "EVBatteryCharging", "string"
				attribute "EVBatteryPluggedIn", "string"
				attribute "EVRange", "string"
				attribute "TirePressureWarning", "string"
				attribute "statusHtml", "string"
				attribute "EVstatusHtml", "string"

				command "refresh", [[name: "force", type: "ENUM", description: "Force a query on the car rather than use the Bluelink server cache.  Excessive use WILL drain your 12V battery.", constraints: TrueFalseValue.collect {k,v -> k}] ]]
				command "start", [[name: "profile", type: "ENUM", description: "Profile to set options", constraints: ["Summer", "Winter", "Profile3"] ]]
				command "stop"
				command "location"
			}

	preferences {
		section("Driver Options") {
			input("htmlStatusAttributes", "bool",
					title: "Generate HTML status attributes",
					description: "These can be used for dashboard displays to summarize multiple states in one attribute.",
					defaultValue: false)
		}
	}
}

/**
 * Boilerplate callback methods called by the framework
 */

void installed()
{
	log.trace("installed() called")
}

void updated()
{
	log.trace("updated() called")
	initialize()
}

void parse(String message)
{
	log.trace("parse called with message: ${message}")
}

/* End of built-in callbacks */

///
// Commands
///
void initialize() {
	log.trace("initialize() called")
	refresh()
}

void refresh(String force)
{
	log.trace("refresh called")
	
	// Not all vehicles have "odometer" in vehicleStatus, so need to call this to update that for now.
	// Probably not a bad idea to allow this to refresh the other vehicle information too...
	parent.getVehicles()
	
	
	parent.getVehicleStatus(device, TrueFalseValue[forceRefresh] ?: false, false)
	updateHtml()
}

void lock()
{
	log.trace("Lock called")
	parent.Lock(device)
}

void unlock()
{
	log.trace("Unlock called")
	parent.Unlock(device)
}

void start(String theProfile)
{
	log.trace("Start(profile) called with profile = ${theProfile}")
	parent.Start(device, theProfile)
}

void stop()
{
	log.trace("Stop called")
	parent.Stop(device)
}

void location()
{
	log.trace("Location called")
	parent.getLocation(device)
}

///
// Data managed by the App but stored in the device
///
void setClimateCapabilities(Map climate_capabilities)
{
	atomicState.climateCapabilities = climate_capabilities
}

Map getClimateCapabilities()
{
	return atomicState.climateCapabilities
}

void setClimateProfiles(Map profiles)
{
	atomicState.climateProfiles = profiles
}

Map getClimateProfiles()
{
	return atomicState.climateProfiles
}

///
// Supporting helpers
///
private void updateHtml()
{
	if (settings?.htmlStatusAttributes ?: false)
	{
		Boolean isEV = device.currentValue("isEV") == "true"

		def builder = new StringBuilder()
		builder << "<table class=\"bldr-tbl\">"
		String statDoors = device.currentValue("DoorLocks")
		builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Doors:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statDoors + "</td></tr>"
		String statHood = device.currentValue("Hood")
		builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Hood:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statHood + "</td></tr>"
		String statTrunk = device.currentValue("Trunk")
		builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Trunk:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statTrunk + "</td></tr>"
		String statEngine = device.currentValue("Engine")
		builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Engine:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statEngine + "</td></tr>"
		String statRange = device.currentValue("Range")
		builder << "<tr><td class=\"bldr-label\" style=\"text-align:left;\">" + "Range:" + "</td><td class=\"bldr-text\" style=\"text-align:left;padding-left:5px\">" + statRange + " miles</td></tr>"
		builder << "</table>"
		String newHtml = builder.toString()
		sendEvent(name:"statusHtml", value: newHtml)

		if (isEV) 
		{
			def builder2 = new StringBuilder()
			builder2 << "<table class=\"bldr2-tbl\">"
			String evBattery = device.currentValue("EVBattery")
			builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + evBattery + " %" + "</td></tr>"
			String statPluggedIn = (device.currentValue("EVBatteryPluggedIn") == "true") ? "Yes" : "No"
			builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery Plugged In:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statPluggedIn + "</td></tr>"
			String statCharging = (device.currentValue("EVBatteryCharging") == "true") ? "Yes" : "No"
			builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + "Battery Charging:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statCharging + "</td></tr>"
			String statEVRange = device.currentValue("EVRange")
			builder2 << "<tr><td class=\"bldr2-label\" style=\"text-align:left;\">" + " EV Range:" + "</td><td class=\"bldr2-text\" style=\"text-align:left;padding-left:5px\">" + statEVRange + " miles</td></tr>"
			builder2 << "</table>"
			String EVHtml = builder2.toString()
			sendEvent(name:"EVstatusHtml", value: EVHtml)
		}
	}
	else
	{
		device.deleteCurrentState("statusHtml")
		device.deleteCurrentState("EVstatusHtml")
	}
}

@Field Map TrueFalseValue =
[
    "false": false,
    "true": true
]
