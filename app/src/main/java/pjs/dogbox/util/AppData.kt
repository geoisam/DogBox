package pjs.dogbox.util

object AppData {
    var snapshot: LunarSnapshot? = null

    const val BASE_URL = "https://raw.githubusercontent.com/geoisam/geoisam/main/data/base.json"

    const val GITHUB_REPO = "https://github.com/geoisam/DogBox"

    const val NIVOD_HOST = "https://www.nivod.vip"
    const val NIVOD_SEARCH = "https://www.nivod.vip/s/-------------/?wd="

    const val UA_CHROME_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Safari/537.36"
    const val UA_CHROME_M =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/169.0.0.0 Mobile Safari/537.36"

    const val UA_WECHAT_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781 NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat UnifiedPCWindowsWechat XWEB/19841"
}
