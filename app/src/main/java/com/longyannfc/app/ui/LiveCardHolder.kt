package com.longyannfc.app.ui

import com.longyannfc.app.data.ble.MifareInfo

/**
 * Simple state holder for passing MifareInfo between screens
 * without URL encoding complex data types.
 */
object LiveCardHolder {
    var info: MifareInfo? = null

    fun set(mifareInfo: MifareInfo) {
        info = mifareInfo
    }

    fun getAndClear(): MifareInfo? {
        val result = info
        info = null
        return result
    }
}
