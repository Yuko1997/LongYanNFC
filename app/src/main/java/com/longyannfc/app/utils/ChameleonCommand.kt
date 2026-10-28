package com.longyannfc.app.utils

/**
 * Chameleon 设备命令的输入校验。
 *
 * 设备协议以 \r\n 分行，命令中混入 CR/LF 会截断当前命令并注入后续命令，
 * 因此只接受长度受限的可打印 ASCII；UID 额外要求十六进制。
 */
object ChameleonCommand {

    const val MAX_LENGTH = 64

    private const val PRINTABLE_MIN = 0x20
    private const val PRINTABLE_MAX = 0x7E

    private val HEX_PATTERN = Regex("^[0-9A-Fa-f]{1,32}$")
    private val CONFIG_PATTERN = Regex("^[A-Za-z0-9_=,.\\- ]{1,32}$")

    /**
     * @return 规整后的命令
     * @throws IllegalArgumentException 命令为空、超长或包含控制字符
     */
    fun sanitize(cmd: String): String {
        val normalized = cmd.trim()
        require(normalized.isNotEmpty()) { "命令不能为空" }
        require(normalized.length <= MAX_LENGTH) { "命令长度超出限制" }
        require(normalized.all { it.code in PRINTABLE_MIN..PRINTABLE_MAX }) { "命令包含非法字符" }
        return normalized
    }

    fun isValidUid(uidHex: String): Boolean = HEX_PATTERN.matches(uidHex.trim())

    fun isValidConfig(config: String): Boolean = CONFIG_PATTERN.matches(config.trim())
}
