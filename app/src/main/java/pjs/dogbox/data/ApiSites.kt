package pjs.dogbox.data

import android.content.Context
import org.json.JSONArray
import java.net.URLEncoder

/**
 * 从 assets/json/sites.json 读取的 CMS JSON 片源（type=1）。
 * key 为 key，name 为片源名，api 为接口链接。
 * 搜索链接：api + "?ac=videolist&wd=关键词"（翻页追加 &pg=N）
 * 详情链接：api + "?ac=detail&ids=搜索到的ID"
 */
data class ApiSite(
    val key: String,
    val name: String,
    val api: String,
)

object ApiSites {
    private const val ASSET = "json/sites.json"

    @Volatile
    private var cached: List<ApiSite>? = null

    /** 同步读取 assets，带内存缓存，多处调用成本可忽略。 */
    fun ensureLoaded(context: Context): List<ApiSite> {
        cached?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(text)
        }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    fun sitesOrEmpty(): List<ApiSite> = cached ?: emptyList()

    fun parse(text: String): List<ApiSite> {
        val arr = JSONArray(text)
        val out = ArrayList<ApiSite>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key").trim()
            val name = o.optString("name").trim()
            val api = o.optString("api").trim()
            if (key.isBlank() || api.isBlank()) continue
            out.add(ApiSite(key = key, name = name.ifBlank { key }, api = api))
        }
        return out
    }

    fun findByKey(key: String): ApiSite? = sitesOrEmpty().firstOrNull { it.key == key }

    fun originOf(api: String): String {
        val idx = api.indexOf("/", 8)
        return if (idx > 0) api.substring(0, idx) else api
    }

    private fun sep(api: String): String = if (api.contains("?")) "&" else "?"

    fun searchUrl(api: String, keyword: String, page: Int): String {
        val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val base = "$api${sep(api)}ac=videolist&wd=$encoded"
        return if (page > 1) "$base&pg=$page" else base
    }

    fun detailUrl(api: String, vodId: String): String =
        "$api${sep(api)}ac=detail&ids=${URLEncoder.encode(vodId, "UTF-8")}"

    fun isApiDetailUrl(url: String): Boolean {
        if (!url.contains("ac=detail")) return false
        return findByDetailUrl(url) != null
    }

    /** 详情链接必然以所属站点的 api 开头（仅差 query），优先前缀匹配，兜底 host 匹配。 */
    fun findByDetailUrl(url: String): ApiSite? {
        val sites = sitesOrEmpty()
        if (sites.isEmpty()) return null
        // 去掉 query 后做前缀匹配，兼容 api 尾部有无 "/" 的情况
        val noQuery = url.substringBefore("?")
        for (s in sites) {
            val apiNoQuery = s.api.substringBefore("?").trimEnd('/')
            if (apiNoQuery.isNotBlank() && noQuery.startsWith(apiNoQuery)) return s
        }
        // 兜底：同 host 且含 ac=detail&ids= 即认为是该 host 的 API 详情
        val host = originOf(url)
        val hasIds = url.contains("ids=") || url.contains("ids%3D")
        if (hasIds) {
            for (s in sites) {
                if (originOf(s.api) == host) return s
            }
        }
        return null
    }

    fun keyForHost(host: String): String? =
        sitesOrEmpty().firstOrNull { originOf(it.api) == host }?.key

    fun nameForKey(key: String): String? = findByKey(key)?.name

    fun originForKey(key: String): String? = findByKey(key)?.let { originOf(it.api) }

    /** 从 API 详情链接中取出 ids 参数。 */
    fun extractVodId(detailUrl: String): String? {
        val q = detailUrl.substringAfter("?", "")
        if (q.isBlank()) return null
        for (part in q.split("&")) {
            val kv = part.split("=", limit = 2)
            if (kv.size == 2 && kv[0].equals("ids", ignoreCase = true)) {
                val v = runCatching {
                    java.net.URLDecoder.decode(kv[1], "UTF-8")
                }.getOrDefault(kv[1])
                if (v.isNotBlank()) return v
            }
        }
        return null
    }
}
