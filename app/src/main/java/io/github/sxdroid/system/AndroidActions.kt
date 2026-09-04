package io.github.sxdroid.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Supported Android fallbacks for actions a normal launcher cannot perform directly. */
object AndroidActions {
    fun openDisplayControls(context: Context): Result<Unit> = runCatching {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openApplicationDetails(context: Context, packageName: String): Result<Unit> = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
