package com.wakemove.android

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRulesTest {
    @Test
    fun `legacy backup rules exclude every credential and device protected domain`() {
        assertEquals(EXPECTED_DOMAINS, excludedDomains(R.xml.backup_rules))
    }

    @Test
    fun `cloud and transfer rules exclude every credential and device protected domain`() {
        assertEquals(EXPECTED_DOMAINS + EXPECTED_DOMAINS, excludedDomains(R.xml.data_extraction_rules))
    }

    private fun excludedDomains(resourceId: Int): List<String> {
        val context = RuntimeEnvironment.getApplication()
        val parser = context.resources.getXml(resourceId)
        val result = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                result += parser.getAttributeValue(null, "domain")
            }
            parser.next()
        }
        return result
    }

    private companion object {
        val EXPECTED_DOMAINS = listOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
