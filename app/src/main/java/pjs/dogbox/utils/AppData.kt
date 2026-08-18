package pjs.dogbox.utils

object AppData {
    var snapshot: LunarSnapshot? = null

    const val GITHUB_REPO = "https://github.com/geoisam/DogBox"

    const val NIVOD_HOST = "https://www.nivod.vip"
    const val NIVOD_SEARCH = "$NIVOD_HOST/s/-------------/?wd="
    const val NIVOD_KEY = "NIVOD"
    const val NIVOD_NAME = "泥视频"

    const val YXXQ_HOST = "https://www.yxxq41.cc"
    const val YXXQ_SEARCH = "$YXXQ_HOST/search/-------------.html?wd="
    const val YXXQ_KEY = "YXXQ"
    const val YXXQ_NAME = "映像星球"

    fun sourceKey(host: String): String = when {
        host.contains("yxxq") -> YXXQ_KEY
        else -> NIVOD_KEY
    }

    fun sourceName(key: String): String = when (key) {
        YXXQ_KEY -> YXXQ_NAME
        else -> NIVOD_NAME
    }

    const val UA_CHROME_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36"
    const val UA_CHROME_M =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Mobile Safari/537.36"

    const val UA_WECHAT_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781 NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat UnifiedPCWindowsWechat XWEB/19841"
}
