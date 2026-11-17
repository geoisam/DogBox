package pjs.dogbox.utils.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri

/**
 * 直接打开本应用的「所有文件访问权限」页面，用户只需在页面上点一下开关，
 * 不用在整份应用列表里自己寻找本应用。
 *
 * 个别厂商 ROM 未实现应用级页面，此时依次降级：
 * 应用级页面 -> 所有应用列表页面 -> 厂商权限管理页 -> 应用详情页。
 */
fun openAllFilesAccessSettings(context: Context) {
    val candidates = listOf(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri(),
        ),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
    for (intent in candidates) {
        if (context.startActivitySafely(intent)) return
    }
    openPermissionManager(context)
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    // 非 Activity 上下文必须带 NEW_TASK，否则会抛 AndroidRuntimeException
    startActivity(if (this is Activity) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

fun openPermissionManager(context: Context) {
    val pkg = context.packageName
    val manufacturer = Build.MANUFACTURER.lowercase()
    val candidates = mutableListOf<Intent>()

    when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
            candidates += Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                .putExtra("extra_pkgname", pkg)
            candidates += Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
                .putExtra("extra_pkgname", pkg)
        }

        manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
            candidates += Intent("com.huawei.systemmanager.intent.action.MANAGE_APPLICATIONS_PERMISSION")
                .setData(Uri.parse("package:$pkg"))
            candidates += Intent("huawei.intent.action.ACTION_APPLICATION_DETAILS")
                .setData(Uri.parse("package:$pkg"))
        }

        manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> {
            candidates += Intent()
                .setClassName(
                    "com.color.safecenter",
                    "com.color.safecenter.permission.PermissionAppAllPermissionActivity"
                )
                .putExtra("packageName", pkg)
            candidates += Intent()
                .setClassName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.PermissionAppAllPermissionActivity"
                )
                .putExtra("packageName", pkg)
        }

        manufacturer.contains("vivo") -> {
            candidates += Intent()
                .setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
                )
                .putExtra("packagename", pkg)
        }
    }

    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }

    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$pkg".toUri()
            )
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开权限设置", Toast.LENGTH_SHORT).show()
    }
}
