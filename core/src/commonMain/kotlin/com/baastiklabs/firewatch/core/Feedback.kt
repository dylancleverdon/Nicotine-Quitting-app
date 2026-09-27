package com.baastiklabs.firewatch.core

/**
 * "Suggest something / report a bug": submissions go straight to the maker's Google Form, which
 * feeds a private Google Sheet. The form only accepts new responses; these field codes can't read
 * or change anything. Shared by the Android and web apps.
 */
object Feedback {
    const val FORM_URL =
        "https://docs.google.com/forms/d/e/1FAIpQLSeWxR_mGDsvt1Bzop8RTL66RWmB_VRkjWiFy2Oxo8ngPqLTJQ/formResponse"
    private const val TYPE = "entry.2010880696"
    private const val SUGGESTION = "entry.1095248491"
    private const val DETAILS = "entry.1531437510"
    private const val NAME = "entry.610873672"
    private const val APP_INFO = "entry.904750360"

    val types = listOf("Idea", "Bug", "Other")

    /** The form fields to POST (application/x-www-form-urlencoded). */
    fun fields(type: String, suggestion: String, details: String, name: String, appInfo: String): List<Pair<String, String>> = listOf(
        TYPE to (types.firstOrNull { it == type } ?: "Other"),
        SUGGESTION to suggestion.trim().take(4000),
        DETAILS to details.trim().take(4000),
        NAME to name.trim().take(100),
        APP_INFO to appInfo.take(200),
    )

    fun encode(fields: List<Pair<String, String>>): String =
        fields.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

    private fun urlEncode(s: String): String {
        val sb = StringBuilder()
        s.encodeToByteArray().forEach { b ->
            val c = (b.toInt() and 0xFF)
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
            else if (ch == ' ') sb.append('+')
            else { sb.append('%'); sb.append("0123456789ABCDEF"[c shr 4]); sb.append("0123456789ABCDEF"[c and 15]) }
        }
        return sb.toString()
    }

    const val PRIVACY_NOTE = "Goes to the Firewatch maker's private suggestions sheet. Only what you type here, plus the app version and phone model, is sent."
}
