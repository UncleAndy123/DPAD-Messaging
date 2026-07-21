package com.dpad.messaging.helpers

import android.content.RestrictionsManager
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowRestrictionsManager

@RunWith(RobolectricTestRunner::class)
class SmsWhitelistManagerTest {

    @Before
    fun setUp() {
        setRestrictions(Bundle())
    }

    @Test
    fun `off mode allows all`() {
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+12345678901")
        assertTrue(result.allowed)
        assertEquals("filtering off", result.reason)
    }

    @Test
    fun `null restrictions bundle does not crash`() {
        setRestrictions(null)
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+15551234567")
        assertTrue(result.allowed)
    }

    @Test
    fun `whitelist passes matching number`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "+15551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+15551234567")
        assertTrue(result.allowed)
    }

    @Test
    fun `whitelist blocks non-matching number`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "+15551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+19876543210")
        assertFalse(result.allowed)
    }

    @Test
    fun `whitelist wildcard allows all`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "*")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+19876543210")
        assertTrue(result.allowed)
    }

    @Test
    fun `blocklist blocks matching number`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "blocklist")
            putString("sms_blocked_numbers", "+15551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+15551234567")
        assertFalse(result.allowed)
    }

    @Test
    fun `blocklist passes non-matching number`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "blocklist")
            putString("sms_blocked_numbers", "+15551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+19876543210")
        assertTrue(result.allowed)
    }

    @Test
    fun `normalize strips leading country code for NANP`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "5551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "+15551234567")
        assertTrue(result.allowed)
    }

    @Test
    fun `normalize matches digits-only address against formatted whitelist`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "+15551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "5551234567")
        assertTrue(result.allowed)
    }

    @Test
    fun `blank address is not whitelisted`() {
        setRestrictions(Bundle().apply {
            putString("sms_filter_mode", "whitelist")
            putString("sms_allowed_numbers", "5551234567")
        })
        val result = SmsWhitelistManager.check(RuntimeEnvironment.getApplication(), "")
        assertFalse(result.allowed)
    }

    private fun setRestrictions(bundle: Bundle?) {
        val rm = RuntimeEnvironment.getApplication()
            .getSystemService(RestrictionsManager::class.java)
        (shadowOf(rm) as ShadowRestrictionsManager).setApplicationRestrictions(bundle)
    }
}
