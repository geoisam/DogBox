package pjs.dogbox.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri

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
