package pjs.dogbox.data.model

import pjs.dogbox.data.repository.ApiSitesRepository
import pjs.dogbox.utils.system.LunarSnapshot

object AppConfig {
    var snapshot: LunarSnapshot? = null

    const val GITHUB_PAGE = "https://github.com/geoisam"

    fun sourceKey(host: String): String =
        ApiSitesRepository.keyForHost(host) ?: ""

    fun sourceName(key: String): String =
        ApiSitesRepository.nameForKey(key) ?: "未知来源"

    fun hostForKey(key: String): String =
        ApiSitesRepository.originForKey(key) ?: ""

    const val UA_CHROME_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36 Edg/169.0.0.0"
    const val UA_CHROME_M =
        "Mozilla/5.0 (Linux; Android 16; MCE16 Build/BP3A.250905.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/169.0.0.0 Mobile Safari/537.36 EdgA/169.0.0.0"
    const val UA_WECHAT_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781 NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat UnifiedPCWindowsWechat XWEB/19841"
}
