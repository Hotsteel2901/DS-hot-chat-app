// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Hotsteel2901

package com.hotsteel.deepseekhot.net

import com.hotsteel.deepseekhot.data.AppSettings
import com.hotsteel.deepseekhot.data.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayTest {

    @Test
    fun deepseekIsNotOpencode() {
        val g = Gateways.detect("https://api.deepseek.com")
        assertFalse(g.isOpencode)
        assertEquals("https://api.deepseek.com/anthropic/v1/messages",
            Endpoints.chat(AppSettings(protocol = Protocol.ANTHROPIC)))
        assertEquals("https://api.deepseek.com/chat/completions",
            Endpoints.chat(AppSettings(protocol = Protocol.CHAT)))
    }

    @Test
    fun zenLegacyStyle() {
        val base = "https://opencode.ai/zen/v1"
        val g = Gateways.detect(base)
        assertTrue(g.isOpencode)
        assertEquals(OpencodeLane.ZEN, g.lane)
        assertFalse(g.consoleStyle)
        assertEquals("$base/chat/completions", Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.CHAT)))
        assertEquals("$base/messages", Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.ANTHROPIC)))
        assertEquals("$base/models", Endpoints.models(AppSettings(baseUrl = base)))
    }

    @Test
    fun goLegacyStyle() {
        val base = "https://opencode.ai/zen/go/v1"
        assertEquals(OpencodeLane.GO, Gateways.detect(base).lane)
        assertEquals("$base/responses", Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.RESPONSES)))
    }

    @Test
    fun consoleV2Style() {
        val base = "https://opencode.ai/inference"
        val g = Gateways.detect(base)
        assertEquals(OpencodeLane.CONSOLE, g.lane)
        assertTrue(g.consoleStyle)
        assertEquals("$base/openai/v1/chat/completions",
            Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.CHAT)))
        assertEquals("$base/anthropic/v1/messages",
            Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.ANTHROPIC)))
        assertEquals("$base/v1/models", Endpoints.models(AppSettings(baseUrl = base)))
    }

    @Test
    fun consoleGoStyle() {
        val base = "https://opencode.ai/inference/go"
        assertEquals(OpencodeLane.GO, Gateways.detect(base).lane)
        assertEquals("$base/openai/v1/responses",
            Endpoints.chat(AppSettings(baseUrl = base, protocol = Protocol.RESPONSES)))
    }

    @Test
    fun fullEndpointIsStrippedBackToRoot() {
        val g = Gateways.detect("https://opencode.ai/zen/v1/chat/completions")
        assertEquals("https://opencode.ai/zen/v1", g.base)
        assertEquals("https://opencode.ai/inference/go/anthropic/v1/messages",
            Endpoints.chat(
                AppSettings(
                    baseUrl = "https://opencode.ai/inference/go/anthropic/v1/messages",
                    protocol = Protocol.ANTHROPIC,
                )
            ))
    }

    @Test
    fun sessionHeaderFollowsMode() {
        val auto = AppSettings(baseUrl = "https://opencode.ai/inference/go", opencodeSession = 0)
        assertEquals(
            mapOf(OpencodeSession.HEADER to OpencodeSession.id("abc-123")),
            OpencodeSession.headers(auto, "abc-123"),
        )
        assertEquals("ses_abc123", OpencodeSession.id("abc-123"))

        val off = auto.copy(opencodeSession = 2)
        assertTrue(OpencodeSession.headers(off, "abc-123").isEmpty())

        val forced = AppSettings(baseUrl = "https://api.deepseek.com", opencodeSession = 1)
        assertTrue(OpencodeSession.headers(forced, "x").isNotEmpty())
        assertTrue(OpencodeSession.headers(AppSettings(), "x").isEmpty())
    }

    @Test
    fun opencodeHasNoFilesOrBalance() {
        val s = AppSettings(baseUrl = "https://opencode.ai/inference")
        assertEquals(null, Endpoints.files(s))
        assertEquals(null, Endpoints.balance(s))
        assertEquals(null, Endpoints.voice(s, "tts"))
    }
}
