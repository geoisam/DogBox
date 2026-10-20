package pjs.dogbox.data.model

import pjs.dogbox.data.repository.ApiSitesRepository
import pjs.dogbox.utils.system.LunarSnapshot

object AppConfig {
    var snapshot: LunarSnapshot? = null

    const val GITHUB_PAGE = "https://github.com/geoisam"

    const val YXXQ_HOST = "https://www.yxxq41.cc"
    const val YXXQ_SEARCH = "$YXXQ_HOST/search/-------------.html?wd="
    const val YXXQ_KEY = "YXXQ"
    const val YXXQ_NAME = "映像星球"

    const val FDZYS_HOST = "https://fdzys.net"
    const val FDZYS_KEY = "FDZYS"
    const val FDZYS_NAME = "饭搭子"

    fun sourceKey(host: String): String = when {
        host.contains("yxxq") -> YXXQ_KEY
        host.contains("fdzys") -> FDZYS_KEY
        else -> ApiSitesRepository.keyForHost(host) ?: YXXQ_KEY
    }

    fun sourceName(key: String): String = when (key) {
        YXXQ_KEY -> YXXQ_NAME
        FDZYS_KEY -> FDZYS_NAME
        else -> ApiSitesRepository.nameForKey(key) ?: "未知来源"
    }

    fun hostForKey(key: String): String = when (key) {
        YXXQ_KEY -> YXXQ_HOST
        FDZYS_KEY -> FDZYS_HOST
        else -> ApiSitesRepository.originForKey(key) ?: YXXQ_HOST
    }

    const val UA_CHROME_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36 Edg/169.0.0.0"
    const val UA_CHROME_M =
        "Mozilla/5.0 (Linux; Android 16; MCE16 Build/BP3A.250905.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/169.0.0.0 Mobile Safari/537.36 EdgA/169.0.0.0"
    const val UA_WECHAT_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781 NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat UnifiedPCWindowsWechat XWEB/19841"
}
