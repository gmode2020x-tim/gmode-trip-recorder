package ca.gmode.triprecorder.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantEndpointTest {
    @Test
    fun privateAndLocalHomeAssistantAddressesRequireLocalNetwork() {
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://192.168.50.4:8123"))
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://10.0.0.4:8123"))
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://172.16.5.4:8123"))
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://homeassistant.local:8123"))
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://homeassistant:8123"))
        assertTrue(HomeAssistantEndpoint.requiresLocalNetwork("http://[fd12::4]:8123"))
    }

    @Test
    fun publicHomeAssistantAddressUsesDefaultAndroidNetwork() {
        assertFalse(HomeAssistantEndpoint.requiresLocalNetwork("https://ha.example.com"))
        assertFalse(HomeAssistantEndpoint.requiresLocalNetwork("https://fc.example.com"))
        assertFalse(HomeAssistantEndpoint.requiresLocalNetwork("https://203.0.113.10:8123"))
        assertFalse(HomeAssistantEndpoint.requiresLocalNetwork("not a URL"))
    }
}
