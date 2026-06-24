package net.jgpower.gichan_land.util

object ErrorMessageSanitizer {
    private val urlRegex = Regex("""(?i)\b(?:https?|wss?|ftp)://[^\s,;\])}]+""")
    private val hostPortRegex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}:\d{2,5}\b""")
    private val nodeRedPortRegex = Regex("""(?i)\b[^\s,;\])}]*:1880\b[^\s,;\])}]*""")
    private val javaNetworkNoiseRegex = Regex("""(?i)\b(?:java\.net|okhttp3|websocket|socket|exception|failed|failure|connect|connection|refused|reset|abort|timeout|timed out|unreachable|host|route|dns|enetwork|enetunreach|econn|closed|canceled|cancelled)\b""")

    fun hideServerAddress(message: String?): String {
        val source = message.orEmpty()
        if (source.isBlank()) return ""

        return source
            .replace(urlRegex, "서버")
            .replace(hostPortRegex, "서버")
            .replace(nodeRedPortRegex, "서버")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun signalErrorMessage(message: String?): String {
        val sanitized = hideServerAddress(message)
        return when {
            sanitized.isBlank() -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("Failed to connect", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("Connection refused", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("timeout", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("timed out", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("Unable to resolve host", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("No route to host", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("Network is unreachable", ignoreCase = true) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            sanitized.contains("Connection reset", ignoreCase = true) -> "신호 연결이 끊겼습니다. 다시 연결 중입니다."
            sanitized.contains("Software caused connection abort", ignoreCase = true) -> "신호 연결이 끊겼습니다. 다시 연결 중입니다."
            sanitized.contains("Socket closed", ignoreCase = true) -> "신호 연결이 끊겼습니다. 다시 연결 중입니다."
            sanitized.contains("closed", ignoreCase = true) -> "신호 연결이 끊겼습니다. 다시 연결 중입니다."
            javaNetworkNoiseRegex.containsMatchIn(sanitized) -> "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
            else -> sanitized
        }
    }

    fun stableSignalNetworkError(): String {
        return "신호 서버에 연결할 수 없습니다. 네트워크를 확인하세요."
    }

    fun genericNetworkError(message: String?, fallback: String): String {
        val sanitized = hideServerAddress(message)
        if (sanitized.isBlank()) return fallback
        return if (javaNetworkNoiseRegex.containsMatchIn(sanitized)) fallback else sanitized
    }
}
