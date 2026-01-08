/**
 *  Bluelink Communicator App
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
 *  This is a fork of Tim Yuhl's (@WindowWasher) Hyundai Bluelink Application.  This version is being iterated on for my own
 *  personal use and is not intended for public consumption, but it should work for anyone in the US who tries to use it.  I do not
 *  intend to advertise or promote this publicly to avoid conflict with Tim's original app, but I welcome anyone who discovers it
 *  wishes to use it.  Your feedback is appreciated.
 *
 *  My goal is to clean up this app to make it easier to iterate on, to reduce some of the cruft that has built up in the interface,
 *  and to bring it closer to Hubitat standards.  I also intend to experiment with some riskier features (such as auto-update), and
 *  these will be gated by settings so you can opt-in into them (or out of them) if desired.
 *
 *  History:
 *  12/31/25 - (corinuss) Started cleanup work for personal use.  No current plans to push this out of this branch.
 *             * Moved many rarely-changing attributes to Device Data to reduce attribute clutter.
 *             * Added a device preference to disable generation of HTML status attributes, to reduce attribute clutter.
 *             * Updated many attribute values to use lower-case, to align with estabilished Hubitat values (on/off, open/closed, etc)
 *             * Nickname is now the Device name and will syncronize with the nickname the user specifies in the Bluelink app.
 *             * Tweaked device to support the Lock hubitat capability.
 *             * Classic climate profiles are now purged after being ported, to clean up app settings.
 *             * Removed separate device version, since it wasn't working anyways on update.  Will eventually be replaced with
 *               the app's version.
 *  01/02/26 - (corinuss) More cleanup
 *             * Officially renamed this copy of the app to Bluelink Communicator to prevent conflict with the previous app.
 *             * Cleaned up logging heleprs to match my other app.  Makes it harder to forget the category.
 *             * Timezone offset now uses the hub's current offset rather than hardcoded to EST.
 *             * Removed the device preference to force Refresh to always forcefully from the car.  This is dangerous and will either
 *               lead to temporary blocking by Hyundai (at best) or a dead 12V battery (at worst).  Refresh now accepts an optional bool
 *               to make explicit force refreshes when needed, but will no longer be set-and-forget.
 *
 * Tim's Special thanks to:
 *
 * @thecloudtaylor for his excellent work on the Honeywell Home Thermostat App/Driver for Hubitat - his app was a template for this
 * App/Driver implementation.
 *
 * @Hacksore and team for their work on Bluelinky, the Node.js app that provided functional Bluelink API calls that I studied to implement this app. This team
 * reverse-engineered the undocumented Bluelink API. Awesome job.
 *
 * @corinuss for fixing EV Start/Stop
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.json.JSONObject
import groovy.transform.Field

static String appVersion() { return "0.1.0" }
def setVersion() {
	if (state.version != appVersion())
	{
		// First install will be null, so don't request a refresh before they've set up.
		if (state.version != null) {
			logInfo("Version updated from ${state.version} to ${appVersion()}.  Queued vehicle refresh request.")
			state.needsVehicleRefresh = true
		}

		state.name = "Bluelink Communicator"
		state.version = appVersion()

		// TODO: Figure out how best to manage the child device version.
	}
}

@Field static String global_apiURL = "https://api.telematics.hyundaiusa.com"
@Field static String client_id = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
@Field static String client_secret = "v558o935-6nne-423i-baa8"

// Chicken Switch
// Currently, classic profiles are not deleted when migrating, to allow for users to switch back to
// an older version of the app if there is a problem.  Eventually we'll want to set this to true to
// delete the settings after the next migration.
@Field static final DELETE_CLASSIC_CLIMATE_PROFILES = true

definition(
		name: "Bluelink Communicator App",
		namespace: "Bluelink",
		author: "corinuss",
		description: "Application for Hyundai Bluelink web service access.",
		importUrl:"https://raw.githubusercontent.com/corinuss/Hyundai-Bluelink/dev/BluelinkApp.groovy",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "accountInfoPage")
	page(name: "profilesPage")
	page(name: "profilesSavedPage")
	page(name: "debugPage", title: "Debug Options", install: false)
	page(name: "debugClimateCapabilitiesPage")
	page(name: "debugClimateCapabilitiesSavedPage")
}

def mainPage()
{
	dynamicPage(name: "mainPage", title: "Bluelink Communicator App", install: true, uninstall: true) {
		section(getFormat("title","About Bluelink Communicator")) {
			paragraph "This application and the corresponding driver are used to access the Hyundai Bluelink web services with Hubitat Elevation. Follow the steps below to configure the application."
		}
		section(getFormat("header-blue-grad","   1.  Set Bluelink Account Information")) {
		}
		getAccountLink()
		section(getFormat("item-light-grey","Account log-in")) {
			input(name: "stay_logged_in", type: "bool", title: "Stay logged in - turn off to force logging in before performing each action.", defaultValue: true, submitOnChange: true)
		}
		section(getFormat("header-blue-grad","   2.  Use This Button To Discover Vehicles and Create Drivers for Each")) {
			input 'discover', 'button', title: 'Discover Registered Vehicles', submitOnChange: true
		}
		listDiscoveredVehicles()
		section(getFormat("header-blue-grad","   3.  Review or Change Remote Start Options")) {
		}
		getProfileLink()
		section(getFormat("header-blue-grad","Debug Logging Level")) {
	        input name: "debugLoggingLevel", type: "enum", title: "Log Level", description: "Debug logging", options: LogDebugLevel.collect{k,v -> k}, defaultValue: "none", submitOnChange: true

		}
		getDebugLink()
	}
}

def accountInfoPage()
{
	dynamicPage(name: "accountInfoPage", title: "<strong>Set Bluelink Account Information</strong>", install: false, uninstall: false) {
		section(getFormat("item-light-grey", "Username")) {
			input name: "user_name", type: "string", title: "Bluelink Username"
		}
		section(getFormat("item-light-grey", "Password")) {
			input name: "user_pwd", type: "password", title: "Bluelink Password"
		}
		section(getFormat("item-light-grey", "PIN")) {
			input name: "bluelink_pin", type: "password", title: "Bluelink PIN"
		}
	}
}

def getAccountLink() {
	section{
		href(
				name       : 'accountHref',
				title      : 'Account Information',
				page       : 'accountInfoPage',
				description: 'Set or Change Bluelink Account Information'
		)
	}
}

void loadClimateProfileSettings(String profileName, Map climateProfileSettings, Map climateCapabilities) {
	logDebug("climateProfileSettings: ${climateProfileSettings}")
 	logDebug("climateCapabilities: ${climateCapabilities}")
	app.updateSetting("climate_${profileName}_airCtrl", climateProfileSettings.airCtrl)
	app.updateSetting("climate_${profileName}_airTemp", climateProfileSettings.airTemp)
	app.updateSetting("climate_${profileName}_defrost", climateProfileSettings.defrost)
	app.updateSetting("climate_${profileName}_steeringHeat", climateProfileSettings.steeringHeat)
	app.updateSetting("climate_${profileName}_rearWindowHeat", climateProfileSettings.rearWindowHeat)
	app.updateSetting("climate_${profileName}_ignitionDur", climateProfileSettings.ignitionDur)
	
	// Seat/Vent saved settings
	if (!climateCapabilities.seatConfigs.isEmpty()) {
		climateCapabilities.seatConfigs.each { seatId, LocationInfo -> 
			app.updateSetting("climate_${profileName}_${CLIMATE_SEAT_LOCATIONS[seatId].name}SeatHeatState", 
								climateProfileSettings.seatHeatState[seatId])
		}
	}
}

Map getSanitizedClimateProfileSettings(String profileName, Map climateProfiles, Map climateCapabilities)
{
	def profileSettings = [:]

	// Gather what the displayed settings defaults should be, according to what the user previously
	// saved or a reasonable default if they haven't set this setting yet.
	def climateProfile = climateProfiles?."${profileName}"

	profileSettings.airCtrl = climateProfile?.airCtrl ?: true
	profileSettings.airTemp = climateProfile?.airTemp?.value ?: 70
	profileSettings.defrost = climateProfile?.defrost ?: false

	if (climateCapabilities.igniOnDurationMax) {
		profileSettings.ignitionDur = climateProfile?.igniOnDuration ?: CLIMATE_IGNIONDURATION_DEFAULT
	}
	
	def heating1 = climateProfile?.heating1 ?: 0
	profileSettings.steeringHeat = heating1HasSteeringHeatingEnabled(heating1)
	profileSettings.rearWindowHeat = heating1HasRearWindowHeatingEnabled(heating1)
	
	profileSettings.seatHeatState = [:]

	CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
			def current_value = 0
			def hasSeat = climateCapabilities.seatConfigs.containsKey(seatId)
			def seatConfig = hasSeat ? climateCapabilities.seatConfigs[seatId] : null
			if (seatConfig != null) {
				current_value = climateProfile?.seatHeaterVentInfo?."${locationInfo.name}SeatHeatState"

				if (current_value == null || !seatConfig.supportedLevels.contains(current_value)) {
					current_value = getDefaultSeatLevel(seatConfig.supportedLevels)
				}
			}

			profileSettings.seatHeatState[seatId] = current_value
	}

	return profileSettings
}

def profilesPage() {
	// If the profiles haven't been migrated yet, do that now so we can show the user accurate data.
	migrateClassicProfiles()

	dynamicPage(name: "profilesPage", title: "<strong>Review/Edit Vehicle Start Options</strong>", nextPage: "profilesSavedPage", install: false, uninstall: false) {
		 section("Choose your vehicle:") {
		 	input(name: "climate_vehicle", type: "device.BluelinkCommunicatorVehicle", title: "Vehicle to configure", required: true, submitOnChange: true)
			paragraph "When done, click 'Next' at the bottom to save your climate profile changes to this vehicle."
		}

		if (climate_vehicle != null) {
			def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)

			if (childDevice == null) {
				section("Error:") {
					paragraph "${climate_vehicle.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app."
				}
			}
			else {
				// Identify what climate options are available to the user.
				def climateProfiles = childDevice.getClimateProfiles()
				def climateCapabilities = getSanitizedClimateCapabilities(childDevice)
				CLIMATE_PROFILES.each { profileName ->

					def climateProfileSettings = getSanitizedClimateProfileSettings(profileName, climateProfiles, climateCapabilities)

					loadClimateProfileSettings(profileName, climateProfileSettings, climateCapabilities)

					section(getFormat("header-blue-grad","Profile: ${profileName}")) {
						input(name: "climate_${profileName}_airCtrl", type: "bool", title: "Turn on climate control when starting")
						input(name: "climate_${profileName}_airTemp", type: "number", title: "Climate temperature to set (${climateCapabilities.tempMin}-${climateCapabilities.tempMax})", range: "${climateCapabilities.tempMin}..${climateCapabilities.tempMax}", required: true)
						input(name: "climate_${profileName}_defrost", type: "bool", title: "Turn on Front Defroster when starting")

						// Could customize this visibility on "rearWindowHeatCapable" and/or "sideMirrorHeatCapable", but they
						// currently share the same value, and pretty much every car has rear window heating.
						input(name: "climate_${profileName}_rearWindowHeat", type: "bool", title: "Turn on Rear Window and Side Mirror Defrosters when starting")

						if (climateCapabilities.steeringWheelHeatCapable) {
							input(name: "climate_${profileName}_steeringHeat", type: "bool", title: "Turn on Steering Wheel Heater when starting")
						}

						if (climateCapabilities.igniOnDurationMax > 0) {
							input(name: "climate_${profileName}_ignitionDur", type: "number", title: "Minutes run engine? (1-${climateCapabilities.igniOnDurationMax})", range: "1..${climateCapabilities.igniOnDurationMax}", required: true)
						}
					}

					if (!climateCapabilities.seatConfigs.isEmpty()) {
						// Collapse by default to match Bluelink app behavior and keep the page a bit tighter.
						section("Seat Temperatures", hideable:true, hidden: true) {
							climateCapabilities.seatConfigs.each { seatId, seatConfig ->
								input(
									name: "climate_${profileName}_${CLIMATE_SEAT_LOCATIONS[seatId].name}SeatHeatState",
									type: "enum",
									title: "${CLIMATE_SEAT_LOCATIONS[seatId].description} Temperature",
									options: seatConfig.supportedLevels.collect{ [ (it) : CLIMATE_SEAT_SETTINGS[it] ] },
									required: true)
							}
						}
					}
				}
			}
		}
	}
}

def profilesSavedPage() {
	dynamicPage(name: "profilesSavedPage", title: "<strong>Profiles saved</strong>", nextPage: "mainPage", install: false, uninstall: false) {
		if (climate_vehicle != null) {
			saveClimateProfiles()
			section("") {
				paragraph "Climate profiles have been saved to ${climate_vehicle.getDisplayName()}."
			}
		}
		else {
			section("") {
				paragraph "No climate profiles saved since no vehicle was selected."
			}
		}
	}
}

def saveClimateProfiles() {
	logTrace("saveClimateProfiles called")

	if (climate_vehicle != null) {
	
		def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)
		if (childDevice == null) {
			// This case shouldn't happen, because we already validated the child device earlier.
			log "Could not remap ${climate_vehicle.getDisplayName()} to childDevice to save climate profiles."
		}
		else {
			def climateCapabilities = getSanitizedClimateCapabilities(childDevice)

			def climateProfileStorage = [:]
			CLIMATE_PROFILES.each { profileName ->
				def climateProfile = [:]
				climateProfile.airCtrl = app.getSetting("climate_${profileName}_airCtrl") ? 1: 0
				climateProfile.defrost = app.getSetting("climate_${profileName}_defrost")

				def airTemp = app.getSetting("climate_${profileName}_airTemp")
				climateProfile.airTemp = ["unit" : 1, "value" : airTemp.toString()]

				def rearWindowHeat = app.getSetting("climate_${profileName}_rearWindowHeat")
				def steeringHeat = climateCapabilities.steeringWheelHeatCapable ? app.getSetting("climate_${profileName}_steeringHeat") : false
				climateProfile.heating1 = getHeating1Value(rearWindowHeat, steeringHeat)

				if (climateCapabilities.igniOnDurationMax > 0) {
					climateProfile.igniOnDuration = app.getSetting("climate_${profileName}_ignitionDur")
				}

				if (!climateCapabilities.seatConfigs.isEmpty())
				{
					climateProfile.seatHeaterVentInfo = [:]
					climateCapabilities.seatConfigs.each { seatId, seatConfig ->
						def shortSeatName = CLIMATE_SEAT_LOCATIONS[seatId].name

						// Even though we gave the input() options a list of maps [ int : string ],
						// it returns us the Integer as a String, so we need to convert it back.  :(
						def seatLevel = app.getSetting("climate_${profileName}_${shortSeatName}SeatHeatState") as Integer

						climateProfile.seatHeaterVentInfo["${shortSeatName}SeatHeatState"] = seatLevel
					}
				}

				climateProfileStorage[profileName] = climateProfile
			}

			childDevice.setClimateProfiles(climateProfileStorage)

			logDebug("Saved climate profiles to ${climate_vehicle.getDisplayName()}: ${climateProfileStorage}")
		}
	}
}

def getProfileLink() {
	section{
		href(
				name       : 'profileHref',
				title      : 'Start Profiles',
				page       : 'profilesPage',
				description: 'View or edit vehicle start profiles'
		)
	}
}

////////
// Debug Stuff
///////
def getDebugLink() {
	section{
		href(
				name       : 'debugHref',
				title      : 'Debug buttons',
				page       : 'debugPage',
				description: 'Access debug buttons (refresh token, initialize)'
		)
	}
}

def debugPage() {
	dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
		section {
			paragraph "<strong>Debug buttons</strong>"
		}
		section {
			input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
		}
		section {
			input 'initialize', 'button', title: 'initialize', submitOnChange: true
		}
		getDebugClimateCapabilitiesLink()
	}
}

def getDebugClimateCapabilitiesLink() {
	section{
		href(
				name       : 'debugClimateCapabilitiesHref',
				title      : 'Modify Vehicle Climate Capabilities',
				page       : 'debugClimateCapabilitiesPage',
				description: 'Overried a vehicles auto-detected climate capabilities so App features not supported by the vehicle can be tested.'
		)
	}
}

def debugClimateCapabilitiesPage() {
	dynamicPage(name:"debugClimateCapabilitiesPage", title: "Override Climate Capabilities", nextPage: "debugClimateCapabilitiesSavedPage", install: false, uninstall: false) {
		section("Choose your vehicle:") {
			input(name: "climate_vehicle", type: "device.HyundaiBluelinkDriver", title: "Vehicle to configure", required: true, submitOnChange: true)
			paragraph "When done, click 'Next' at the bottom to save your changes to this vehicle."
			paragraph "Use the 'Force Refresh Vehicle Details' button on the Debug page to reset these values when done."
		}

		if (climate_vehicle != null) {
			def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)

			if (childDevice == null) {
				section("Error:") {
					paragraph "${climate_vehicle.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app."
				}
			}
			else {
				def climateCapabilities = getSanitizedClimateCapabilities(childDevice)
				logTrace("climateCapabilities $climateCapabilities")

				app.removeSetting("vehicleClimateCapability_tempMin")
				app.removeSetting("vehicleClimateCapability_tempMax")
				app.removeSetting("vehicleClimateCapability_steeringWheelHeatCapable")
				app.removeSetting("vehicleClimateCapability_igniOnDurationMax")

				// Clear out all seat location names that we support.
				CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
					app.removeSetting("vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels")
				}

				def current_tempMin = climateCapabilities.tempMin
				def current_tempMax = climateCapabilities.tempMax
				def current_steeringWheelHeatCapable = climateCapabilities.steeringWheelHeatCapable
				def current_igniOnDurationMax = climateCapabilities.igniOnDurationMax

				def current_seatConfigs = [:]
				CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
					def seatConfig = [:]
					seatConfig.hasSeat = climateCapabilities.seatConfigs.containsKey(seatId)
					seatConfig.supportedLevels = seatConfig.hasSeat ? climateCapabilities.seatConfigs[seatId].supportedLevels : []
					current_seatConfigs[seatId] = seatConfig
				}

				section("") {
					input(name: "vehicleClimateCapability_tempMin", type: "number", title: "Minimum Temperature", defaultValue: current_tempMin, required: true)
					input(name: "vehicleClimateCapability_tempMax", type: "number", title: "Maximum Temperature", defaultValue: current_tempMax, required: true)
					input(name: "vehicleClimateCapability_steeringWheelHeatCapable", type: "bool", title: "Steering Wheel Heat Capable", defaultValue: current_steeringWheelHeatCapable)
					input(name: "vehicleClimateCapability_igniOnDurationMax", type: "number", title: "Ignition On Duration Maximum (0 = Disabled)", defaultValue: current_igniOnDurationMax)
				}

				section("Seat Configurations") {
					CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
						logTrace("current_seatConfigs[seatId].supportedLevels ${current_seatConfigs[seatId].supportedLevels}")
						input(
							name: "vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels",
							type: "enum",
							title: "${CLIMATE_SEAT_LOCATIONS[seatId].description} Supported Levels",
							defaultValue: current_seatConfigs[seatId].supportedLevels,
							options: CLIMATE_SEAT_SETTINGS.collect{ settingId,name -> [(settingId) : name] },
							multiple: true)
					}
				}
			}
		}
	}
}

def debugClimateCapabilitiesSavedPage() {
	dynamicPage(name: "debugClimateCapabilitiesSavedPage", title: "<strong>Capabilities saved</strong>", nextPage: "mainPage", install: false, uninstall: false) {
		if (climate_vehicle != null) {
			saveClimateCapabilities()
			section("") {
				paragraph "Climate capabilities have been saved to ${climate_vehicle.getDisplayName()}."
			}
		}
		else {
			section("") {
				paragraph "No climate capabilities saved since no vehicle was selected."
			}
		}
	}
}

def saveClimateCapabilities() {
	logTrace("saveClimateProfiles called")

	if (climate_vehicle != null) {
	
		def childDevice = getChildDevice(climate_vehicle.deviceNetworkId)
		if (childDevice == null) {
			// This case shouldn't happen, because we already validated the child device earlier.
			log "Could not remap ${climate_vehicle.getDisplayName()} to childDevice to save climate profiles."
		}
		else {
			def climateCapabilities = [:]

			climateCapabilities.tempMin = vehicleClimateCapability_tempMin
			climateCapabilities.tempMax = vehicleClimateCapability_tempMax
			climateCapabilities.steeringWheelHeatCapable = vehicleClimateCapability_steeringWheelHeatCapable
			climateCapabilities.igniOnDurationMax = vehicleClimateCapability_igniOnDurationMax

			def seatConfigs = [:]
			CLIMATE_SEAT_LOCATIONS.each { seatId, locationInfo ->
				def supportedLevels = app.getSetting("vehicleClimateCapability_seatConfigs_${seatId}_supportedLevels")
				def has_seat = (supportedLevels != null) && !supportedLevels.isEmpty()
				if (has_seat) {
					def seatInfo = [:]
					seatInfo.supportedLevels = supportedLevels.collect{ it as Integer }
					seatConfigs."$seatId" = seatInfo
				}
			}
			climateCapabilities.seatConfigs = seatConfigs

			childDevice.setClimateCapabilities(climateCapabilities)
			logDebug("Saved climate capabilities to ${climate_vehicle.getDisplayName()}: ${climateCapabilities}")
		}
	}
}

def appButtonHandler(btn) {
	switch (btn) {
		case 'discover':
			authorize()
			getVehicles(true)
			break
		case 'refreshToken':
			refreshToken()
			break
		case 'initialize':
			initialize()
			break
		default:
			logError("Invalid Button In Handler")
	}
}

void installed() {
	logTrace("Installed with settings: ${settings}")
	stay_logged_in = true // initialized to ensure token refresh happens with default setting
	initialize()
}

void updated() {
	logTrace("Updated with settings: ${settings}")
	initialize()
}

void uninstalled() {
	logInfo("Uninstalling Bluelink Communicator App and deleting child devices")
	unschedule()
	for (device in getChildDevices())
	{
		deleteChildDevice(device.deviceNetworkId)
	}
}

void initialize() {
	logTrace("Initialize called")
	setVersion()
	unschedule()
	if(stay_logged_in && (state.refresh_token != null)) {
		refreshToken()
	}
}

void authorize() {
	logTrace("authorize called")

	// Periodically ensure the app version has been updated, in case the user didn't click 'Done' in the App after an update.
	setVersion()

	// make sure there are no outstanding token refreshes scheduled
	unschedule()

	def headers = [
			"client_id": client_id,
			"client_secret": client_secret
	]
	def body = [
			"username": user_name,
			"password": user_pwd
	]
	def params = [uri: global_apiURL, path: "/v2/ac/oauth/token", headers: headers, body: body]

	try
	{
		httpPostJson(params) { response -> authResponse(response) }

		if (state.needsVehicleRefresh)
		{
			logDebug("Refreshing vehicles details after authorize due to 'needsVehicleRefresh' being set.")
			getVehicles()
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		logError("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
	}
}

void refreshToken(Boolean refresh=false) {
	logTrace("refreshToken called")

	// Periodically ensure the app version has been updated, in case the user didn't click 'Done' in the App after an update.
	setVersion()

	if (state.refresh_token != null)
	{
		def headers = [
				"client_id": client_id,
				"client_secret": client_secret
		]
		def body = [
				refresh_token: state.refresh_token
		]
		def params = [uri: global_apiURL, path: "/v2/ac/oauth/token/refresh", headers: headers, body: body]

		try
		{
			httpPostJson(params) { response -> authResponse(response) }

			if (state.needsVehicleRefresh)
			{
				logDebug("Refreshing vehicles details after refreshToken to 'needsVehicleRefresh' being set.")
				getVehicles()
			}
		}
		catch (java.net.SocketTimeoutException e)
		{
			if (!refresh) {
				logInfo("Socket timeout exception, will retry refresh token")
				refreshToken(true)
			}
		}
		catch (groovyx.net.http.HttpResponseException e)
		{
			// could be authorization has been lost, try again after authorizing again
			if (!refresh) {
				logInfo("Authoriztion may have been lost, will retry refreshing token after reauthorizing")
				authorize()
				refreshToken(true)
			}
			else {
				logError("refreshToken failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
			}
		}
	}
	else
	{
		logError("Failed to refresh token, refresh token null.")
	}
}

def authResponse(response)
{
	logTrace("authResponse called")

	def reCode = response.getStatus()
	def reJson = response.getData()
	logDebug("reCode: {$reCode}")
	logDebug("reJson: {$reJson}")

	if (reCode == 200)
	{
		state.access_token = reJson.access_token
		state.refresh_token = reJson.refresh_token

		Integer expireTime = (Integer.parseInt(reJson.expires_in) - 180)
		logDebug("Bluelink token refreshed successfully, Next Scheduled in: ${expireTime} sec")
		// set up token refresh
		if (stay_logged_in) {
			runIn(expireTime, refreshToken)
		}
	}
	else
	{
		logError("LoginResponse Failed HTTP Request Status: ${reCode}")
	}
}

def getVehicles(Boolean createNewVehicleDevices=false, Boolean retry=false)
{
	logTrace("getVehicles called")

	def uri = global_apiURL + "/ac/v2/enrollment/details/" + user_name
	def headers = [ access_token: state.access_token, client_id: client_id, includeNonConnectedVehicles : "Y"]
	def params = [ uri: uri, headers: headers ]
	logDebug("getVehicles ${params}")

	//add error checking
	LinkedHashMap reJson = []
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus()
			reJson = response.getData()
			logDebug("reCode: ${reCode}")
			logJsonHelper("getVehicles", reJson)
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			getVehicles(createNewVehicleDevices, true)
		}
		logError("getVehicles failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
		return
	}

	if (reJson.enrolledVehicleDetails == null) {
		logInfo("No enrolled vehicles found.")
	}
	else {
			reJson.enrolledVehicleDetails.each{ vehicle ->

				if (createNewVehicleDevices) {
					// Only log while creating new vehicles, so we don't spam during Refresh.
					logInfo("Found vehicle: ${vehicle.vehicleDetails.nickName} with VIN: ${vehicle.vehicleDetails.vin}")
				}

				// Try to get the device if it already exists.
				com.hubitat.app.ChildDeviceWrapper childDevice = getChildDevice(getChildDeviceNetId(vehicle.vehicleDetails.vin))
				if (childDevice == null && createNewVehicleDevices) {
					// Try to create a new device.
					childDevice = CreateChildDriver(vehicle.vehicleDetails.nickName, vehicle.vehicleDetails.vin)
				}

				if (childDevice != null) {
					//populate/update attributes
					childDevice.setName(vehicle.vehicleDetails.nickName)
					updateDeviceData(childDevice, "NickName", vehicle.vehicleDetails.nickName)
					updateDeviceData(childDevice, "VIN", vehicle.vehicleDetails.vin)
					updateDeviceData(childDevice, "RegId", vehicle.vehicleDetails.regid)
					safeSendEvent(childDevice, "Odometer", vehicle.vehicleDetails.odometer)
					updateDeviceData(childDevice, "Model", vehicle.vehicleDetails.series)
					updateDeviceData(childDevice, "Trim", vehicle.vehicleDetails.trim)
					updateDeviceData(childDevice, "vehicleGeneration", vehicle.vehicleDetails.vehicleGeneration)
					updateDeviceData(childDevice, "brandIndicator", vehicle.vehicleDetails.brandIndicator)
					updateDeviceData(childDevice, "evStatus", vehicle.vehicleDetails.evStatus)
					safeSendEvent(childDevice, "isEV", vehicle.vehicleDetails.evStatus == "E")  // ICE will be "N"

					cacheClimateCapabilities(childDevice, vehicle.vehicleDetails)
				 }
			}
	}

	// If a refresh was needed, we can clear out that flag now.
	state.remove("needsVehicleRefresh")
}

void getVehicleStatus(com.hubitat.app.DeviceWrapper device, Boolean refresh = false, Boolean retry=false)
{
	logTrace("getVehicleStatus() called")

	if( !stay_logged_in ) {
		authorize()
	}

	//Note: this API can take up to a minute tor return if REFRESH=true because it contacts the car's modem and
	//doesn't use cached info.
	def uri = global_apiURL + "/ac/v2/rcs/rvs/vehicleStatus"
	def headers = getDefaultHeaders(device)
	headers.put('offset', "${getUtcOffset()}")
	headers.put('REFRESH', refresh.toString())
	int valTimeout = refresh ? 240 : 10 //timeout in sec.
	def params = [ uri: uri, headers: headers, timeout: valTimeout ]
	logDebug("getVehicleStatus ${params}")

	//add error checking
	LinkedHashMap  reJson = []
	try
	{
		httpGet(params) { response ->
			def reCode = response.getStatus()
			reJson = response.getData()
			logDebug("reCode: ${reCode}")
			logJsonHelper("getVehicleStatus", reJson)
		}

		// Update relevant device attributes
		safeSendEvent(device, 'Engine', reJson.vehicleStatus.engine, 'on', 'off')
		safeSendEvent(device, 'lock', reJson.vehicleStatus.doorLock, 'locked', 'unlocked')
		safeSendEvent(device, 'Hood', reJson.vehicleStatus.hoodOpen, 'open', 'closed')
		safeSendEvent(device, 'Trunk', reJson.vehicleStatus.trunkOpen, 'open', 'closed')
		if (reJson.vehicleStatus.dte?.value != null) {
			safeSendEvent(device, "Range", reJson.vehicleStatus.dte.value)
		}
		if (reJson.vehicleStatus.battery?.batSoc != null) {
			safeSendEvent(device, "BatterySoC", reJson.vehicleStatus.battery.batSoc)
		}
		safeSendEvent(device, "LastRefreshTime", reJson.vehicleStatus.dateTime)
		safeSendEvent(device, "TirePressureWarning", reJson.vehicleStatus.tirePressureLamp.tirePressureWarningLampAll, "true", "false")
		if (reJson.vehicleStatus.odometer != null) {
			safeSendEvent(device, "Odometer", reJson.vehicleStatus.odometer)
		}
		if (device.currentValue("isEV") == "true" && reJson.vehicleStatus?.evStatus != null) {
			safeSendEvent(device, "EVBatteryCharging", reJson.vehicleStatus.evStatus.batteryCharge, "true", "false")
			safeSendEvent(device, "EVBatteryPluggedIn", reJson.vehicleStatus.evStatus.batteryPlugin, "true", "false")
			safeSendEvent(device, "EVBattery", reJson.vehicleStatus.evStatus.batteryStatus)
			safeSendEvent(device, "EVRange", reJson.vehicleStatus.evStatus.drvDistance[0].rangeByFuel.evModeRange.value)
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			getVehicleStatus(device, refresh, true)
		}
		logError("getVehicleStatus failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
	}
}

void getLocation(com.hubitat.app.DeviceWrapper device, Boolean refresh=false)
{
	logTrace("getLocation() called")

	if( !stay_logged_in ) {
		authorize()
	}

	def uri = global_apiURL + '/ac/v2/rcs/rfc/findMyCar'
	def headers = getDefaultHeaders(device)
	headers.put('offset', "${getUtcOffset()}")
	def params = [ uri: uri, headers: headers, timeout: 120 ] // long timeout, contacts modem
	logDebug("getLocation ${params}")

	LinkedHashMap reJson = []
	try
	{
		httpGet(params) { response ->
			int reCode = response.getStatus()
			reJson = response.getData()
			logDebug("reCode: ${reCode}")
			logJsonHelper("getLocation", reJson)
			if (reCode == 200) {
				logInfo("getLocation successful.")
				sendEventHelper(device, "Location", true)
			}
			if( reJson.coord != null) {
				//convert altitude from m to ft
				def theAlt = reJson.coord.alt * 3.28084
				safeSendEvent(device, 'locLatitude', reJson.coord.lat)
				safeSendEvent(device, 'locLongitude', reJson.coord.lon)
				safeSendEvent(device, 'locUpdateTime', reJson.time)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			getLocation(device, true)
			return
		}
		logError("getLocation failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}")
		sendEventHelper(device, "Location", false)
	}
}

void Lock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/off') )
	{
		logInfo("Lock call failed -- try waiting before retrying")
		sendEventHelper(device, "Lock", false)
	} else
	{
		logInfo("Lock call made to car -- can take some time to lock")
		sendEventHelper(device, "Lock", true)
	}
}

void Unlock(com.hubitat.app.DeviceWrapper device)
{
	if( !LockUnlockHelper(device, '/ac/v2/rcs/rdo/on') )
	{
		logInfo("Unlock call failed -- try waiting before retrying")
		sendEventHelper(device, "Unlock", false)
	}else
	{
		logInfo("Unlock call made to car -- can take some time to unock")
		sendEventHelper(device, "Unlock", true)
	}
}

void Start(com.hubitat.app.DeviceWrapper device, String profile, Boolean retry=false)
{
	logTrace("Start() called with profile: ${profile}")

	if( !stay_logged_in ) {
		authorize()
	}

	def isEV = device.currentValue("isEV") == "true"
	def uri = global_apiURL + (isEV ? '/ac/v2/evc/fatc/start' : '/ac/v2/rcs/rsc/start')
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')

	// If the classic profiles haven't been migrated yet, do that now so we can apply accurate data.
	migrateClassicProfiles()
	
	// Fill in profile parameters
	def childDevice = getChildDevice(device.deviceNetworkId)
	def climateBody = [ "airCtrl" : 0 ] // default to off unless we have data
	if (childDevice == null) {
		logError("Could not obtain climate profiles.  ${device.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app.")
	}
	else {
		def climateProfiles = childDevice.getClimateProfiles()
		if (climateProfiles == null || !climateProfiles.containsKey(profile)) {
			// Empty should always use defaults without complaint
			if (!profile.isEmpty()) {
				logWarn("Ignoring profile '$profile' when starting vehicle ${device.getDisplayName()} because it doesn't exist.")
			}
		}
		else {
			climateBody = climateProfiles[profile]
		}
	}

	String theCar = device.getDataValue("NickName")

	def body = [:]

	if (!isEV) {
		String theVIN = device.getDataValue("VIN")

		body.username = user_name
		body.vin = theVIN
		body.Ims = 0
	}

	body += climateBody

	String sBody = JsonOutput.toJson(body).toString()

	def params = [ uri: uri, headers: headers, body: sBody, timeout: 10 ]
	logDebug("Start ${params}")

	int reCode = 0
	try
	{
		httpPostJson(params) { response ->
			reCode = response.getStatus()
			if (reCode == 200) {
				logInfo("Vehicle ${theCar} successfully started.")
				sendEventHelper(device, "Start", true)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			Start(device, profile,true)
			return
		}
		logError("Start vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.data}")
		sendEventHelper(device, "Start", false)
	}
}

void Stop(com.hubitat.app.DeviceWrapper device, Boolean retry=false)
{
	logTrace("Stop() called")

	if( !stay_logged_in ) {
		authorize()
	}

	def isEV = device.currentValue("isEV") == "true"
	def uri = global_apiURL + (isEV ? '/ac/v2/evc/fatc/stop' : '/ac/v2/rcs/rsc/stop')
	def headers = getDefaultHeaders(device)
	headers.put('offset', '-4')
	def params = [ uri: uri, headers: headers, timeout: 10 ]
	logDebug("Stop ${params}")

	String theCar = device.getDataValue("NickName")
	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus()
			if (reCode == 200) {
				logInfo("Vehicle ${theCar} successfully stopped.")
				sendEventHelper(device, "Stop", true)
			}
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			Stop(device, true)
			return
		}
		logError("Stop vehicle failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}")
		sendEventHelper(device, "Stop", false)
	}
}

///
// Climate functionality
///
@Field static final CLIMATE_TEMP_MIN_DEFAULT = 62
@Field static final CLIMATE_TEMP_MAX_DEFAULT = 82

@Field static final CLIMATE_IGNIONDURATION_GEN2_MAX = 10	// GEN 1 & 2 Maximum
@Field static final CLIMATE_IGNIONDURATION_GEN2EV_MAX = 0	// Not supported on Gen2 EVs  :(
@Field static final CLIMATE_IGNIONDURATION_GEN3_MAX = 30	// GEN 3 (and later?) Maximum
@Field static final CLIMATE_IGNIONDURATION_DEFAULT = 10

@Field static final CLIMATE_PROFILES =
[
	"Summer",
	"Winter",
	"Profile3"
]

@Field static final CLIMATE_SEAT_LOCATIONS =
[
	"1" : ["name" : "drv", "description" : "Driver Seat" ],
	"2" : ["name" : "ast", "description" : "Passenger Seat" ],
	"3" : ["name" : "rl",  "description" : "Rear Left Seat" ],
	"4" : ["name" : "rr",  "description" : "Rear Right Seat" ],
	// Protection against newer locations.  These seats will probably end up ignored.
].withDefault { otherValue -> ["name" : "Unknown$otherValue", "description" : "Unknown$otherValue Seat" ]  }

@Field static final CLIMATE_SEAT_SETTINGS =
[
	// These appear to be legacy simple on/off values
	0 : "Off",
	1 : "On",

	// These appear to be the current modern flexible multi-on state values, with its own Off value.
	2 : "Off",
	3 : "Cool Low",
	4 : "Cool Medium",
	5 : "Cool High",
	6 : "Heat Low",
	7 : "Heat Medium",
	8 : "Heat High",

	// Protection against newer settings.  This should continue to function even with Unknowns.
].withDefault { otherValue -> "Unknown$otherValue" }

// heating1 values are:
// ====================
// 0: 'Off',
// 1: 'Steering Wheel and Rear Window',
// 2: 'Rear Window',
// 3: 'Steering Wheel',
// 4: "Steering Wheel and Rear Window" // # Seems to be the same as 1 but different region (EU)
Integer getHeating1Value(Boolean enableRearWindowHeat, Boolean enableSteeringHeat) {
	if (enableRearWindowHeat) {
		// If supporting EU, might need to return 4 here instead of 1.
		return enableSteeringHeat ? 1 : 2
	}
	else {
		return enableSteeringHeat ? 3 : 0
	}
}

Boolean heating1HasRearWindowHeatingEnabled(Integer heating1) {
	return (heating1 == 1 || heating1 == 2 || heating1 == 4)
}

Boolean heating1HasSteeringHeatingEnabled(Integer heating1) {
	return (heating1 == 1 || heating1 == 3 || heating1 == 4)
}

Integer getDefaultSeatLevel(ArrayList supportedLevels) {
	// There are multiple 'Off' states ('0' and '2').  '0' should be allowed for all vehicle types,
	// but prefer the supported 'Off' state whenever possible.
	Integer defaultLevel = 0
	if (supportedLevels.contains(2)) {
		defaultLevel = 2
	}

	return defaultLevel
}

// Converts raw climate seat capabilities from Bluelink to what we store in the device.
// (Filters out data we don't care about, and does some upfront processing on some strings.)
Map sanitizeSeatConfigs(ArrayList seatConfigs) {
	def sanitizedSeatConfigs = [:]

	seatConfigs?.each{ seatConfig ->
		if (seatConfig.seatLocationID == null) {
			logDebug("Seat location doesn't have a locationID")
		}
		else if (!CLIMATE_SEAT_LOCATIONS.containsKey(seatConfig.seatLocationID)) {
			logInfo("Seat location ${seatConfig.seatLocationID} is not recognized and will be ignored.  Contact developer to add support for this seat.")
		}
		else {
			def supportedLevelsString = seatConfig.supportedLevels ?: "0"

			// This is a comma-delimited string, which isn't that useful to us.
			// Convert it to an integer list before saving, which is much easier to work with.
			sanitizedSeatConfigs[seatConfig.seatLocationID] = [
				"supportedLevels" : supportedLevelsString.split(',').collect{ it as Integer }
			]
		}
	}

	return sanitizedSeatConfigs
}

// Cache the vehicle's climate capabilities to the device.
void cacheClimateCapabilities(com.hubitat.app.DeviceWrapper device, Map vehicleDetails)
{
	def climateCapabilities = [
		"tempMin" : vehicleDetails.additionalVehicleDetails?.minTemp ?: CLIMATE_TEMP_MIN_DEFAULT,
		"tempMax" : vehicleDetails.additionalVehicleDetails?.maxTemp ?: CLIMATE_TEMP_MAX_DEFAULT,
		"steeringWheelHeatCapable" : (vehicleDetails.steeringWheelHeatCapable ?: "NO") == "YES",
		"seatConfigs" : sanitizeSeatConfigs(vehicleDetails.seatConfigurations?.seatConfigs),

		// Gen 2 EVs don't support setting "igniOnDuration".
		"igniOnDurationMax" : (device.currentValue("isEV") != "true") || (device.getDataValue("vehicleGeneration") != "2")
	]

	// Different combinations of Gen and isEV have different igniOnDuration limits.
	// Not technically in vehicleDetails, but simplifies the rest of the code.
	def vehicleGeneration = (device.getDataValue("vehicleGeneration") as Integer) ?: 0
	if (vehicleGeneration >= 3) {
		logDebug "igniOnDurationMax CLIMATE_IGNIONDURATION_GEN3_MAX"
		climateCapabilities.igniOnDurationMax = CLIMATE_IGNIONDURATION_GEN3_MAX
	}
	else if (device.currentValue("isEV") == "true") {
		climateCapabilities.igniOnDurationMax = CLIMATE_IGNIONDURATION_GEN2EV_MAX
	}
	else {
		climateCapabilities.igniOnDurationMax = CLIMATE_IGNIONDURATION_GEN2_MAX
	}

	// Need to convert to a child device to be able to save to the device.
	def childDevice = getChildDevice(device.deviceNetworkId)
	if (childDevice == null) {
		 logError("Could not cache climate capabilities.  ${device.getDisplayName()} does not appear to be a child device of this app.  Please delete the device and re-discover your vehicle through this app.")
	}
	else {
		childDevice.setClimateCapabilities(climateCapabilities)
	}
}

// Gets the vehicle's climate capabilities cached from the device and handles missing data.
Map getSanitizedClimateCapabilities(com.hubitat.app.ChildDeviceWrapper device)
{
	Map climateCapabilities = device.getClimateCapabilities()

	if (climateCapabilities == null) {
		logDebug "getSanitizedClimateCapabilities: No climate cabilities found on ${device.getDisplayName}.  Using defaults."
		climateCapabilities = [:]
	}

	if (!climateCapabilities.containsKey("tempMin")){
		climateCapabilities.tempMin = CLIMATE_TEMP_MIN_DEFAULT
	}

	if (!climateCapabilities.containsKey("tempMax")){
		climateCapabilities.tempMax = CLIMATE_TEMP_MAX_DEFAULT
	}

	if (!climateCapabilities.containsKey("steeringWheelHeatCapable")){
		climateCapabilities.steeringWheelHeatCapable = false
	}

	if (!climateCapabilities.containsKey("seatConfigs")){
		climateCapabilities.seatConfigs = [:]
	}

	if (!climateCapabilities.containsKey("igniOnDurationMax")){
		climateCapabilities.igniOnDurationMax = CLIMATE_IGNIONDURATION_GEN2_MAX
	}

	return climateCapabilities
}

// Migrates climate profiles exactly from the previous version, despite what the vehicle actually supports.
// This will continue to work as it did before, and features will be add or removed according to vehicle
// capabilities the next time the user modifies the profile for their vehicle.
void migrateClassicProfiles() {
	// Check if one setting exists before doing the full migration.
	// If we've already cleaned it up, the data has already been migrated.
	if (app.getSetting("Summer_climate") != null) {
		logTrace("Attempting to migrateClassicProfiles")

		def climateProfileStorage = [:]
		CLIMATE_PROFILES.each { profileName ->
			def climateProfile = [:]
			climateProfile.airCtrl = app.getSetting("${profileName}_climate") ? 1: 0
			climateProfile.defrost = app.getSetting("${profileName}_defrost")
			climateProfile.heating1 = app.getSetting("${profileName}_heatAcc") ? 1 : 0
			climateProfile.igniOnDuration = app.getSetting("${profileName}_ignitionDur")

			def temp_setting = app.getSetting("${profileName}_temp")
			if (temp_setting == "LO") {
				temp_setting = CLIMATE_TEMP_MIN_DEFAULT
			}
			else if (temp_setting == "HI") {
				temp_setting = CLIMATE_TEMP_MAX_DEFAULT
			}

			climateProfile.airTemp = ["unit" : 1, "value" : temp_setting.toString()]

			climateProfileStorage[profileName] = climateProfile
		}

		getChildDevices().each { device ->
			if (device.getClimateProfiles() == null) {
				logInfo("Migrated climate profile to ${device.getDisplayName()}")
				device.setClimateProfiles(climateProfileStorage)
			}
		}

		if (DELETE_CLASSIC_CLIMATE_PROFILES) {
			logDebug("Deleting classic climate profiles.")

			// Clean up deprected profiles.
			CLIMATE_PROFILES.each { profileName ->
				app.removeSetting("${profileName}_climate")
				app.removeSetting("${profileName}_temp")
				app.removeSetting("${profileName}_defrost")
				app.removeSetting("${profileName}_heatAcc")
				app.removeSetting("${profileName}_ignitionDur")
			}
		}
	}
}

///
// Supporting helpers
///
private void sendEventHelper(com.hubitat.app.DeviceWrapper device, String sentCommand, Boolean result)
{
	logTrace("sendEventHelper() called")
	String strResult = result ? "successfully sent to vehicle" : "sent to vehicle - error returned"
	String strDesc = "Command ${sentCommand} ${strResult}"
	String strVal = result ? "Successful" : "Error"
	sendEvent(device, [name: sentCommand, value: strVal, descriptionText: strDesc, isStateChange: true])
}

private Boolean LockUnlockHelper(com.hubitat.app.DeviceWrapper device, String urlSuffix, Boolean retry=false)
{
	logTrace("LockUnlockHelper() called")

	if( !stay_logged_in ) {
		authorize()
	}

	def uri = global_apiURL + urlSuffix
	def headers = getDefaultHeaders(device)
	headers.put('offset', "${getUtcOffset()}")
	String theVIN = device.getDataValue("VIN")
	def body = [
			"userName": user_name,
			"vin": theVIN
	]

	def params = [ uri: uri, headers: headers, body: body, timeout: 10 ]
	logDebug("LockUnlockHelper ${params}")

	int reCode = 0
	try
	{
		httpPost(params) { response ->
			reCode = response.getStatus()
		}
	}
	catch (groovyx.net.http.HttpResponseException e)
	{
		if (e.getStatusCode() == 401 && !retry)
		{
			logWarn("Authorization token expired, will refresh and retry.")
			refreshToken()
			LockUnlockHelper(device, urlSuffix, true)
		}
		logError("LockUnlockHelper failed -- ${e.getLocalizedMessage()}: Status: ${e.response.getStatus()}")
	}
	return (reCode == 200)
}

private void listDiscoveredVehicles() {
	def children = getChildDevices()
	def builder = new StringBuilder()
	builder << "<ul>"
	children.each {
		if (it != null) {
			builder << "<li><a href='/device/edit/${it.getId()}'>${it.getDisplayName()}</a></li>"
		}
	}
	builder << "</ul>"
	def theCars = builder.toString()
	if (!children.isEmpty())
	{
		section {
			paragraph "Discovered vehicles are listed below:"
			paragraph theCars
		}
	}
}


private LinkedHashMap<String, String> getDefaultHeaders(com.hubitat.app.DeviceWrapper device) {
	logTrace("getDefaultHeaders() called")

	LinkedHashMap<String, String> theHeaders = []
	try {
		String theVIN = device.getDataValue("VIN")
		String regId = device.getDataValue("RegId")
		String generation = device.getDataValue("vehicleGeneration")
		String brand = device.getDataValue("brandIndicator")
		theHeaders = [
				'access_token' : state.access_token,
				'client_id'   : client_id,
				'language'    : '0',
				'vin'         : theVIN,
				'APPCLOUD-VIN' : theVIN,
				'username' : user_name,
				'registrationId' : regId,
				'gen' : generation,
				'to' : 'ISS',
				'from' : 'SPA',
				'encryptFlag' : 'false',
				'bluelinkservicepin' : bluelink_pin,
				'brandindicator' : brand
		]
	} catch(Exception e) {
		logError("Unable to generate API headers - Did you fill in all required information?")
	}

	return theHeaders
}

private getChildDeviceNetId(String Vin)
{
	return "BluelinkComVehicle_" + Vin
}

private com.hubitat.app.ChildDeviceWrapper CreateChildDriver(String Name, String Vin)
{
	logTrace("CreateChildDriver called")
	String vehicleNetId = getChildDeviceNetId(Vin)
	com.hubitat.app.ChildDeviceWrapper newDevice = null
	try {
			newDevice = addChildDevice(
				'Bluelink Communicator Vehicle',
				vehicleNetId,
				[
						name : Name
				])
	}
	catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
		logInfo("${e.message} - you need to install the appropriate driver.")
	}
	catch (IllegalArgumentException e) {
		//Intentionally ignored.  Expected if device id already exists in HE.
		logTrace("Ignored: ${e.message}")
	}
	return newDevice
}

@Field Map LogDebugLevel =
[
    "none": 0,
    "debug": 1,
    "trace": 2
]

def logError(Object data) {
	log.error "${data}"
}

def logWarn(Object data) {
    log.warn "${data}"
}

def logInfo(Object data) {
    log.info "${data}"
}

def logDebug(Object data) {
    if (debugLoggingLevel != null && LogDebugLevel[debugLoggingLevel] >= LogDebugLevel["debug"])
    {
        log.debug "${data}"
    }
}

def logTrace(Object data) {
    if (debugLoggingLevel != null && LogDebugLevel[debugLoggingLevel] >= LogDebugLevel["trace"])
    {
        log.trace "${data}"
    }
}

// concept stolen bptworld, who stole from @Stephack Code
def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#81BC00; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background-color:#D8D8D8; border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
	if(type == "header-blue-grad") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; line-height: 2.0; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "header-center-blue-grad") return "<div style='text-align:center; color:#000000; border-radius: 5px 5px 5px 5px; font-weight: bold; padding-left: 10px; background: linear-gradient(to bottom, #d4e4ef 0%,#86aecc 100%);  border: 2px'>${myText}</div>"
	if(type == "item-light-grey") return "<div style='color:#000000; border-radius: 5px 5px 5px 5px; font-weight: normal; padding-left: 10px; background-color:#D8D8D8; border: 1px solid'>${myText}</div>"
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

private void logJsonHelper(String api_call, LinkedHashMap input)
{
    if (debugLoggingLevel != null && LogDebugLevel[debugLoggingLevel] >= LogDebugLevel["debug"])
		String strJson = JsonOutput.prettyPrint(new JSONObject(input).toString())
		logDebug("${api_call} - reJson: ${strJson}")
	}
}

private Boolean updateDeviceData(com.hubitat.app.DeviceWrapper device, String dataName, def value)
{
    def currentState = device.getDataValue(dataName)
    if (currentState == null || currentState != value)
    {
        device.updateDataValue(dataName, value)
        return (currentState != null)
    }

    return false
}

private void safeSendEvent(com.hubitat.app.DeviceWrapper device, String attrib, def val, def valTrue = null, def valFalse = null)
{
	if (val == null) {
		logDebug(" *** Attribute: ${attrib} JSON value is null")
	}
	else {
		if (valTrue && valFalse) 
		{
			sendEvent(device, [name: attrib, value: val ? valTrue : valFalse])
		} 
		else if ((valTrue == null) && (valFalse == null)) 
		{
			sendEvent(device, [name: attrib, value: val])
		}
		else
		{
			logError("SafeSendEvent programming error - missing argument value")
		}
	}
}

private Integer getUtcOffset()
{
	location.timeZone.getOffset(now()) / 3600000
}
