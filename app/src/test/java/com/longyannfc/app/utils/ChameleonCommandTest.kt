package com.longyannfc.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChameleonCommandTest {

    @Test
    fun `正常命令原样返回`() {
        assertEquals("VERSION?", ChameleonCommand.sanitize("VERSION?"))
        assertEquals("SETTING=9", ChameleonCommand.sanitize("SETTING=9"))
    }

    @Test
    fun `首尾空白被裁剪`() {
        assertEquals("UID=0102", ChameleonCommand.sanitize("  UID=0102  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `换行注入被拒绝`() {
        ChameleonCommand.sanitize("UID=0102\r\nPCRDETECTION=1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `回车注入被拒绝`() {
        ChameleonCommand.sanitize("UID=0102\rVERSION?")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空命令被拒绝`() {
        ChameleonCommand.sanitize("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `超长命令被拒绝`() {
        ChameleonCommand.sanitize("A".repeat(ChameleonCommand.MAX_LENGTH + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非 ASCII 命令被拒绝`() {
        ChameleonCommand.sanitize("UID=0102中文")
    }

    @Test
    fun `UID 只接受十六进制`() {
        assertTrue(ChameleonCommand.isValidUid("01020304"))
        assertTrue(ChameleonCommand.isValidUid("AABBCCDD"))
        assertFalse(ChameleonCommand.isValidUid("ZZZZ"))
        assertFalse(ChameleonCommand.isValidUid("0102 0304"))
        assertFalse(ChameleonCommand.isValidUid("01020304\n05060708"))
        assertFalse(ChameleonCommand.isValidUid(""))
    }

    @Test
    fun `结尾换行被裁剪不会注入命令`() {
        assertEquals("UID=01020304", ChameleonCommand.sanitize("UID=01020304\r\n"))
    }

    @Test
    fun `CONFIG 只接受受限字符集`() {
        assertTrue(ChameleonCommand.isValidConfig("TYPE=MIFARE"))
        assertFalse(ChameleonCommand.isValidConfig("TYPE=MIFARE\r\nVERSION?"))
        assertFalse(ChameleonCommand.isValidConfig("A".repeat(33)))
    }
}
