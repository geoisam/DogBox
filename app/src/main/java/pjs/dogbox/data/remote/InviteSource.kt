package pjs.dogbox.data.remote

import io.ktor.http.ContentType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pjs.dogbox.utils.network.HttpUtil
import pjs.dogbox.utils.security.CryptUtil

object InviteExecutor {

    data class Params(
        val inviteCode: String,
        val minInterval: Int,
        val maxInterval: Int,
        val count: Int,
        val selectedType: String,
    )

    private val charPool = ('a'..'z') + ('0'..'9') + ('A'..'Z')
    private val deviceBrands = listOf(
        "Samsung", "Xiaomi", "REDMI", "OPPO", "vivo", "iQOO",
        "HUAWEI", "HONOR", "OnePlus", "Realme", "Google"
    )
    private val successCodes = listOf("200", "1", "ok")

    suspend fun execute(
        configArray: JsonArray,
        params: Params,
        onLog: (String) -> Unit,
    ) {
        try {
            val inviteCode = params.inviteCode.trim()

            if (params.minInterval !in 1..999) {
                onLog("最小间隔未正确填写，停止运行"); return
            }
            if (params.maxInterval !in 1..999) {
                onLog("最大间隔未正确填写，停止运行"); return
            }
            if (params.count !in 1..99) {
                onLog("邀请次数未正确填写，停止运行"); return
            }
            if (inviteCode.isEmpty()) {
                onLog("邀请码未填写，停止运行"); return
            }

            val selectedConfig = configArray.firstOrNull { item ->
                item.jsonObject["name"]?.jsonPrimitive?.contentOrNull == params.selectedType
            }?.jsonObject ?: run {
                onLog("未找到与「${params.selectedType}」对应的配置，停止运行"); return
            }

            val cryptObj = selectedConfig["crypt"]?.jsonObject ?: run {
                onLog("配置缺少 config.crypt 字段，停止运行"); return
            }
            val cryptKey = cryptObj["key"]?.jsonPrimitive?.contentOrNull ?: run {
                onLog("配置缺少 KEY 或格式无效，停止运行"); return
            }
            val cryptIv = cryptObj["iv"]?.jsonPrimitive?.contentOrNull ?: run {
                onLog("配置缺少 IV 或格式无效，停止运行"); return
            }

            val apiHome = extractApiUrl(selectedConfig, "home", onLog) ?: return
            val apiInfo = extractApiUrl(selectedConfig, "info", onLog) ?: return
            val apiBind = extractApiUrl(selectedConfig, "bind", onLog) ?: return

            if (selectedConfig["ishex"]?.jsonPrimitive?.boolean == true) {
                cfbFlow(
                    selectedConfig, inviteCode, params.count, apiHome, apiInfo, apiBind,
                    cryptKey, cryptIv, params.minInterval, params.maxInterval, onLog
                )
            } else {
                cbcFlow(
                    selectedConfig, inviteCode, params.count, apiHome, apiInfo, apiBind,
                    cryptKey, cryptIv, params.minInterval, params.maxInterval, onLog
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            onLog("运行发生异常: ${e.message ?: "未知错误"}")
            e.printStackTrace()
            when (e) {
                is java.net.SocketTimeoutException -> onLog("网络请求超时，请检查网络或增加超时时间")
                is java.net.SocketException -> onLog("网络连接异常: ${e.message}")
                is javax.net.ssl.SSLException -> onLog("SSL握手失败: ${e.message}")
                is org.json.JSONException -> onLog("JSON解析异常: ${e.message}")
            }
        }
    }


    private suspend fun cbcFlow(
        cfg: JsonObject, inviteCode: String, count: Int,
        apiHome: String, apiInfo: String, apiBind: String,
        cryptKey: String, cryptIv: String,
        minGap: Int, maxGap: Int, log: (String) -> Unit,
    ) {
        val jsonb = cfg["jsonb"]?.jsonObject ?: run { log("配置缺少 jsonb 字段，停止运行"); return }
        val jsonbCrypt =
            jsonb["crypt"]?.jsonObject ?: run { log("配置缺少 jsonb.crypt 字段，停止运行"); return }
        val jKey = jsonbCrypt["key"]?.jsonPrimitive?.contentOrNull
            ?: run { log("配置缺少KEY或格式无效，停止运行"); return }
        val jIv = jsonbCrypt["iv"]?.jsonPrimitive?.contentOrNull
            ?: run { log("配置缺少IV或格式无效，停止运行"); return }

        val hosts = jsonb["host"]?.jsonArray ?: run { log("配置缺少配置链接，停止运行"); return }
        if (hosts.isEmpty()) {
            log("配置链接列表为空，停止运行"); return
        }

        val hostJson = discoverHosts(hosts, jKey, jIv, log) ?: return
        val isVtoken = cfg["istoken"]?.jsonPrimitive?.boolean ?: false
        val vmdkStr = cfg["vmdk"]?.jsonPrimitive?.contentOrNull ?: ""
        val rawStr = cfg["raw"]?.jsonPrimitive?.contentOrNull ?: ""
        val baseHeaders = extractHeaders(cfg)

        log("运行准备完成，开始邀请...")

        for (i in 1..count) {
            log("正在执行第 $i/$count 次邀请...")
            val ok = cbcRound(
                i, inviteCode, hostJson, apiHome, apiInfo, apiBind,
                cryptKey, cryptIv, isVtoken, vmdkStr, rawStr, baseHeaders, log
            )
            if (!handleGap(ok, count, i, minGap, maxGap, log)) return
        }
    }

    private suspend fun cbcRound(
        i: Int, inviteCode: String, hostJson: JsonArray,
        apiHome: String, apiInfo: String, apiBind: String,
        cryptKey: String, cryptIv: String,
        isVtoken: Boolean, vmdkStr: String, rawStr: String,
        baseHeaders: Map<String, String>, log: (String) -> Unit,
    ): Boolean {
        val deviceID = md5DeviceId()
        val sendTime = now()
        val androidVer = randomAndroidVer()
        val deviceModal = deviceBrands.random()
        val equipVal = randomEquip()
        val appVer = "666"

        val vtoken = buildVtoken(isVtoken, deviceID, sendTime, equipVal, vmdkStr, cryptKey, cryptIv)
        val vke = md5Vke(appVer, sendTime, rawStr, deviceID)

        val headers = mutableMapOf<String, String>().apply {
            putAll(baseHeaders)
            put("equip", equipVal)
            put("sysinfo", "ANDROID")
            put("sysver", androidVer)
            put("vos", "ANDROID")
            put("vcode", "")
            put("vmid", deviceID)
            put("app-version", appVer.chunked(1).joinToString("."))
            put("version-code", "1")
            put("device-modal", deviceModal)
            put("vke", vke)
            put("vtoken", vtoken)
            put("vtime", sendTime.toString())
            put("vversion", appVer)
            put("device-uniq", deviceID)
            put("os", "android")
            put("os-version", androidVer)
            put("client-time", sendTime.toString())
            put("rand-str", randomStr(deviceID, sendTime))
        }

        for ((j, el) in hostJson.withIndex()) {
            val baseUrl = el.jsonPrimitive.contentOrNull ?: continue

            val homeResp = httpGet("$baseUrl$apiHome", headers) ?: run {
                log("第 ${j + 1} 个节点请求失败，尝试下一个节点..."); continue
            }

            val code = parseCode(homeResp)
            if (code != null) {
                if (!isOk(code)) {
                    log("邀请频繁，请切换IP或稍后再试，停止运行 $homeResp"); return false
                }
                log("第 $i 次获取 token 成功")
            } else {
                log("第 $i 次请求成功，但未找到 code 字段"); continue
            }

            val encData = parseData(homeResp) ?: run {
                log("第 $i 次请求成功，但响应中未找到有效的 data 字段"); continue
            }

            val decRaw = aesDecrypt(encData, cryptKey, cryptIv) ?: run {
                log("第 $i 次 data 解密失败"); continue
            }

            val decJson = toJson(decRaw) ?: run {
                log("第 $i 次解密数据 JSON 解析失败"); continue
            }

            val tokenField = if (isVtoken) "vtoken" else "vno"
            val tokenVal = decJson[tokenField]?.jsonPrimitive?.contentOrNull ?: run {
                log("未找到 $tokenField 字段，无法进行邀请"); continue
            }

            val memberOk = cbcVerify(
                i, deviceID, appVer, rawStr, baseUrl, apiInfo,
                isVtoken, tokenVal, headers, cryptKey, cryptIv, log
            )
            if (!memberOk) return false

            delay(1000L)

            cbcBind(
                i, inviteCode, deviceID, appVer, rawStr, baseUrl, apiBind,
                isVtoken, tokenVal, headers, cryptKey, cryptIv, log
            )
            return true
        }

        return false
    }

    private suspend fun cbcVerify(
        i: Int, deviceID: String, appVer: String, rawStr: String,
        baseUrl: String, apiInfo: String,
        isVtoken: Boolean, tokenVal: String,
        baseHeaders: Map<String, String>, cryptKey: String, cryptIv: String,
        log: (String) -> Unit,
    ): Boolean {
        var ok = 0;
        var fail = 0;
        var attempt = 0

        while (ok < 2 && attempt < 5) {
            attempt++
            val t = now()
            val vk = md5Vke(appVer, t, rawStr, deviceID)
            val vtok = buildMemberToken(isVtoken, deviceID, tokenVal, cryptKey, cryptIv)
            val hdrs = memberHeaders(baseHeaders, deviceID, t, vk, vtok, tokenVal, isVtoken)

            val resp = httpGet("$baseUrl$apiInfo", hdrs)

            if (resp != null) {
                val code = parseCode(resp)
                if (code != null && !isOk(code)) {
                    fail++
                    log("第 $i 次会员验证失败($fail/2)，$resp")
                    if (fail >= 2) {
                        log("会员验证连续失败，停止运行"); return false
                    }
                } else if (code != null) {
                    ok++
                    log("第 $i 次会员验证成功($ok/2)")
                } else {
                    log("第 $i 次会员验证请求成功，但未找到 code 字段")
                }
            } else {
                log("第 $i 次会员验证请求失败")
            }

            if (ok < 2) delay(1000L)
        }

        if (ok < 2) {
            log("会员验证未达到2次成功，停止运行"); return false
        }
        return true
    }

    private suspend fun cbcBind(
        i: Int, inviteCode: String, deviceID: String, appVer: String, rawStr: String,
        baseUrl: String, apiBind: String,
        isVtoken: Boolean, tokenVal: String,
        baseHeaders: Map<String, String>, cryptKey: String, cryptIv: String,
        log: (String) -> Unit,
    ) {
        val t = now()
        val vk = md5Vke(appVer, t, rawStr, deviceID)
        val vtok = buildMemberToken(isVtoken, deviceID, tokenVal, cryptKey, cryptIv)
        val hdrs = memberHeaders(baseHeaders, deviceID, t, vk, vtok, tokenVal, isVtoken)

        val resp = httpGet("$baseUrl$apiBind$inviteCode", hdrs)

        if (resp != null) {
            val result = toJson(resp)?.let { obj ->
                val d = obj["data"]
                if (d is JsonPrimitive) aesDecrypt(d.content, cryptKey, cryptIv) ?: resp else resp
            } ?: resp
            log("第 $i 次邀请绑定结果 $result")
        } else {
            log("第 $i 次邀请绑定请求失败")
        }
    }


    private suspend fun cfbFlow(
        cfg: JsonObject, inviteCode: String, count: Int,
        apiHome: String, apiInfo: String, apiBind: String,
        cryptKey: String, cryptIv: String,
        minGap: Int, maxGap: Int, log: (String) -> Unit,
    ) {
        val hosts = cfg["host"]?.jsonArray ?: run { log("配置缺少 host 数组，停止运行"); return }
        if (hosts.isEmpty()) {
            log("host 列表为空，停止运行"); return
        }

        val rawStr = cfg["raw"]?.jsonPrimitive?.contentOrNull ?: ""
        val baseHeaders = extractHeaders(cfg)

        log("运行准备完成，开始邀请...")

        for (i in 1..count) {
            log("正在执行第 $i/$count 次邀请...")
            val ok = cfbRound(
                i, inviteCode, hosts, apiHome, apiInfo, apiBind,
                cryptKey, cryptIv, rawStr, baseHeaders, log
            )
            if (!handleGap(ok, count, i, minGap, maxGap, log)) return
        }
    }

    private suspend fun cfbRound(
        i: Int, inviteCode: String, hosts: JsonArray,
        apiHome: String, apiInfo: String, apiBind: String,
        cryptKey: String, cryptIv: String,
        rawStr: String, baseHeaders: Map<String, String>,
        log: (String) -> Unit,
    ): Boolean {
        val deviceID = md5DeviceId()
        val deviceBrand = deviceBrands.random()
        val deviceModal = randomEquip()
        val dataJson =
            """{"device_model":"$deviceModal","device_brand":"$deviceBrand","bundle_id":"com.play.app","new_player":"fx","oauth_type":"android","oauth_id":"$deviceID","theme":"gw","version":"6.6.6","token":""}"""
        val encData = (cryptIv + CryptUtil.cfbEncrypt(dataJson, cryptKey, cryptIv)).uppercase()

        val prefixes = listOf("api1", "api2", "api3")

        for ((j, el) in hosts.withIndex()) {
            val domain = el.jsonPrimitive.contentOrNull ?: continue

            val apiHost = probeHosts(domain, prefixes, baseHeaders)
            if (apiHost == null) {
                log("第 $i 次域名 $domain 所有 API 均不通，尝试下一个域名..."); continue
            }

            if (apiHome.isNotEmpty()) {
                val body = hexBody(encData, now(), rawStr)
                if (httpPost("$apiHost$apiHome", body, baseHeaders) == null) {
                    log("第 $i 次 home 请求失败，尝试下一个域名..."); continue
                }
            }

            val infoBody = hexBody(encData, now(), rawStr)
            val response = httpPost("$apiHost$apiInfo", infoBody, baseHeaders) ?: run {
                log("第 $i 次节点 $apiHost POST 失败，尝试下一个域名..."); continue
            }

            val respData = parseData(response) ?: run {
                log("第 $i 次获取 token 响应中未找到 data 字段"); continue
            }

            val decJson = hexDecrypt(respData, cryptKey) ?: run {
                log("第 $i 次 token 数据解密失败"); continue
            }

            val token = decJson["data"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
                ?: run { log("第 $i 次未找到 data 中的 token 字段"); continue }

            log("第 $i 次获取 token 成功")

            val bindJson =
                """{"aff":"$inviteCode","device_model":"$deviceModal","device_brand":"$deviceBrand","bundle_id":"com.play.app","new_player":"fx","oauth_type":"android","oauth_id":"$deviceID","theme":"gw","version":"6.6.6","token":"$token"}"""
            val bindEnc = (cryptIv + CryptUtil.cfbEncrypt(bindJson, cryptKey, cryptIv)).uppercase()
            val bindBody = hexBody(bindEnc, now(), rawStr)
            val bindResp = httpPost("$apiHost$apiBind", bindBody, baseHeaders)

            if (bindResp != null) {
                log("第 $i 次邀请绑定请求成功")
            } else {
                log("第 $i 次邀请绑定请求失败")
            }

            return true
        }

        return false
    }


    private fun buildVtoken(
        isVtoken: Boolean, deviceID: String, sendTime: Long,
        equipVal: String, vmdkStr: String,
        cryptKey: String, cryptIv: String,
    ): String {
        return if (isVtoken) {
            val inner =
                """{"vmid":"$deviceID","timestamp":$sendTime,"equip":"$equipVal","vsize":"999*1888","vmdk":"${
                    vmdkStr.replace(
                        "/",
                        "\\/"
                    )
                }"}"""
            CryptUtil.cbcEncrypt("${CryptUtil.md5(inner)}$inner", cryptKey, cryptIv)
        } else {
            CryptUtil.cbcEncrypt("""{"vmid":"$deviceID"}""", cryptKey, cryptIv)
        }
    }

    private fun buildMemberToken(
        isVtoken: Boolean,
        deviceID: String,
        tokenVal: String,
        cryptKey: String,
        cryptIv: String
    ): String {
        return if (isVtoken) tokenVal
        else CryptUtil.cbcEncrypt("""{"vmid":"$deviceID","vno":"$tokenVal"}""", cryptKey, cryptIv)
    }

    private fun md5Vke(appVer: String, time: Long, rawStr: String, deviceID: String) =
        CryptUtil.md5("$appVer-$time-$rawStr-$deviceID")

    private fun memberHeaders(
        base: Map<String, String>, deviceID: String, time: Long,
        vke: String, vtoken: String, tokenVal: String, isVtoken: Boolean,
    ): MutableMap<String, String> {
        return base.toMutableMap().apply {
            remove("equip"); remove("sysinfo"); remove("sysver")
            remove("vos"); remove("vcode"); remove("vsk")
            put("vtime", time.toString())
            put("client-time", time.toString())
            put("rand-str", randomStr(deviceID, time))
            put("vke", vke)
            put("vtoken", vtoken)
            if (!isVtoken) put("vno", tokenVal)
        }
    }

    private fun hexBody(encData: String, ts: Long, rawStr: String): String {
        val pre = "_ver=v2&data=$encData&timestamp=$ts"
        val sign = CryptUtil.md5(CryptUtil.sha256("$pre$rawStr"))
        return "$pre&sign=$sign"
    }

    private fun hexDecrypt(data: String, keyHex: String): JsonObject? {
        return try {
            val iv = data.substring(0, 32)
            val enc = data.substring(32)
            val dec = CryptUtil.cfbDecrypt(enc, keyHex, iv)
            toJson(dec)
        } catch (_: Exception) {
            null
        }
    }


    private suspend fun discoverHosts(
        hosts: JsonArray, jKey: String, jIv: String,
        log: (String) -> Unit,
    ): JsonArray? {
        val result = selectFirstNonNull(
            hosts.mapIndexed { i, el ->
                suspend {
                    if (el !is JsonPrimitive) {
                        log("第 ${i + 1} 个配置链接格式无效"); null
                    } else {
                        resolveHostList(el.content, jKey, jIv, i, log)
                    }
                }
            }
        )
        if (result == null) {
            log("所有 host 链接均请求失败，停止运行")
        }
        return result
    }

    private suspend fun resolveHostList(
        url: String, jKey: String, jIv: String,
        index: Int, log: (String) -> Unit,
    ): JsonArray? {
        val resp = httpGet(url) ?: run {
            log("第 ${index + 1} 个API列表获取失败或为空"); return null
        }

        val json = toJson(resp)
        if (json == null || "data" !in json) {
            log("第 ${index + 1} 个API列表返回数据无效（缺少 data 字段）"); return null
        }

        val dataEl = json["data"]
        if (dataEl == null || dataEl is JsonNull || dataEl !is JsonPrimitive) {
            log("第 ${index + 1} 个API列表 data 字段格式无效（非字符串）"); return null
        }

        val dec = aesDecrypt(dataEl.content, jKey, jIv) ?: run {
            log("第 ${index + 1} 个API列表数据解密失败"); return null
        }

        val hostData = toJson(dec)
        if (hostData == null || "apiHost" !in hostData) {
            log("第 ${index + 1} 个API列表返回数据无效（缺少 apiHost 字段）"); return null
        }

        val apiArr = hostData["apiHost"]
        if (apiArr !is JsonArray || apiArr.isEmpty()) {
            log("第 ${index + 1} 个API列表 apiHost 字段格式无效（非数组或为空）"); return null
        }

        log("第 ${index + 1} 个API列表获取成功")
        return apiArr.jsonArray
    }

    private suspend fun probeHosts(
        domain: String,
        prefixes: List<String>,
        headers: Map<String, String>
    ): String? {
        return selectFirstNonNull(prefixes.map { prefix ->
            val host = "https://$prefix.$domain/api.php"
            suspend { if (httpGet(host, headers) != null) host else null }
        })
    }

    private suspend fun <T> selectFirstNonNull(blocks: List<suspend () -> T?>): T? {
        if (blocks.isEmpty()) return null
        return coroutineScope {
            val deferred = blocks.map { block -> async { block() } }.toMutableList()
            var result: T? = null
            while (deferred.isNotEmpty()) {
                val winner = select<Deferred<T?>> {
                    deferred.forEach { d ->
                        d.onAwait { d }
                    }
                }
                deferred.remove(winner)
                val r = winner.await()
                if (r != null) {
                    result = r
                    coroutineContext.cancelChildren()
                    break
                }
            }
            result
        }
    }


    private suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()) =
        withContext(Dispatchers.IO) { HttpUtil.get(url, headers).getOrNull() }

    private suspend fun httpPost(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ) =
        withContext(Dispatchers.IO) {
            HttpUtil.post(url, body, ContentType.Application.FormUrlEncoded, headers)
                .getOrNull()
        }


    private fun toJson(s: String): JsonObject? =
        runCatching { Json.parseToJsonElement(s).jsonObject }.getOrNull()

    private fun parseCode(resp: String): String? =
        toJson(resp)?.let { obj ->
            obj["code"]?.let { if (it is JsonPrimitive) it.content else it.toString() }
        }

    private fun parseData(resp: String): String? =
        toJson(resp)?.let { obj ->
            val d = obj["data"]; if (d is JsonPrimitive) d.content else null
        }

    private fun isOk(code: String) = code.lowercase() in successCodes

    private fun aesDecrypt(data: String, key: String, iv: String) =
        runCatching { CryptUtil.cbcDecrypt(data, key, iv) }.getOrNull()


    private fun md5DeviceId() =
        CryptUtil.md5((1..32).map { charPool.random() }.joinToString(""))

    private fun randomEquip() =
        (1..(3..9).random()).map { charPool.random() }.joinToString("")

    private fun randomAndroidVer() =
        kotlin.random.Random.nextInt(7, 18).toString()

    private fun now() = System.currentTimeMillis()

    private fun randomStr(deviceID: String, time: Long) =
        "$deviceID$time${(1..10).map { ('0'..'9').random() }.joinToString("")}"


    private fun extractApiUrl(cfg: JsonObject, key: String, log: (String) -> Unit): String? {
        return cfg["api"]?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull ?: run {
            log("配置缺少 api.$key，停止运行"); null
        }
    }

    private fun extractHeaders(cfg: JsonObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        cfg["headers"]?.jsonObject?.entries?.forEach { (k, v) ->
            if (v is JsonPrimitive) map[k] = v.content
        }
        return map
    }


    private suspend fun handleGap(
        ok: Boolean, total: Int, i: Int,
        minGap: Int, maxGap: Int,
        log: (String) -> Unit,
    ): Boolean {
        if (!ok) {
            log("第 $i 次邀请所有节点均请求失败，停止运行"); return false
        }
        if (i < total) {
            val sec = (minGap..maxGap).random().toLong()
            log("等待 $sec 秒后继续...")
            delay(sec * 1000L)
        } else {
            log("全部任务已完成，停止运行...")
        }
        return true
    }
}
