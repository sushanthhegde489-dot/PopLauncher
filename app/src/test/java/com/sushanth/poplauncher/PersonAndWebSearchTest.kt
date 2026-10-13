package com.sushanth.poplauncher

import android.content.Intent
import com.sushanth.poplauncher.data.model.ConversationEntity
import com.sushanth.poplauncher.data.model.WebOverviewEntity
import com.sushanth.poplauncher.data.model.PersonEntity
import com.sushanth.poplauncher.widgets.custom.WidgetManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PersonAndWebSearchTest {

    @Test
    fun testPersonEntityAggregatesConversations() {
        val person = PersonEntity(
            id = "person:123",
            title = "Zen",
            subtitle = "+1234567890",
            contactId = "123",
            phoneNumbers = listOf("+1234567890"),
            launchIntent = Intent(),
            relatedConversations = listOf(
                ConversationEntity(
                    id = "conv:whatsapp",
                    title = "Conversation with Zen",
                    subtitle = "WhatsApp • +1234567890",
                    source = "WhatsApp",
                    personAssociation = "Zen",
                    launchIntent = Intent()
                ),
                ConversationEntity(
                    id = "conv:sms",
                    title = "Message Zen",
                    subtitle = "Messages • +1234567890",
                    source = "Messages",
                    personAssociation = "Zen",
                    launchIntent = Intent()
                )
            )
        )

        assertEquals("Zen", person.title)
        assertEquals(2, person.relatedConversations.size)
        assertEquals("WhatsApp", person.relatedConversations[0].source)
        assertEquals("Messages", person.relatedConversations[1].source)
    }

    @Test
    fun testWebOverviewEntityFallback() {
        val query = "What is quantum entanglement"
        val overview = WebOverviewEntity(
            query = query,
            title = query,
            subtitle = "AI Overview for \"$query\"",
            browserIntent = Intent()
        )

        assertEquals(query, overview.title)
        assertEquals("Web Search", overview.source)
        assertNotNull(overview.browserIntent)
    }

    @Test
    fun testIntentSecurity() {
        // Deep link URI logic is implemented manually here as we don't have an Application Context in unit tests to test the direct providers,
        // but we verify the exact scheme blocks that the providers implement.
        val javascriptUrl = "javascript:alert(1)"
        val fileUrl = "file:///data/local.prop"
        
        val isSecureJs = WidgetManifest.isHttpsUrl(javascriptUrl)
        val isSecureFile = WidgetManifest.isHttpsUrl(fileUrl)
        
        assertEquals(false, isSecureJs)
        assertEquals(false, isSecureFile)
    }
}
