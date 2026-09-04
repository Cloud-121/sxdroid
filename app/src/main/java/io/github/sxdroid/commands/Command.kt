package io.github.sxdroid.commands

import android.content.Context
import android.content.Intent
import android.net.Uri

/** A thing the palette can show and activate. */
interface Command {
    val id: String
    val name: String
    val description: String
    val keywords: List<String>
    suspend fun execute(context: Context)
}

class LaunchApplicationCommand(
    override val id: String,
    override val name: String,
    override val description: String,
    override val keywords: List<String>,
    val packageName: String,
    private val activityName: String,
) : Command {
    override suspend fun execute(context: Context) {
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

class OpenUrlCommand(
    override val id: String,
    override val name: String,
    override val description: String,
    override val keywords: List<String>,
    private val url: String,
) : Command {
    override suspend fun execute(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class OpenIntentCommand(
    override val id: String,
    override val name: String,
    override val description: String,
    override val keywords: List<String>,
    private val intent: Intent,
) : Command {
    override suspend fun execute(context: Context) {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class MenuCommand(
    override val id: String,
    override val name: String,
    override val description: String,
    override val keywords: List<String>,
    val menuId: String,
) : Command {
    override suspend fun execute(context: Context) = Unit
}

class InfoCommand(
    override val id: String,
    override val name: String,
    override val description: String,
    override val keywords: List<String> = emptyList(),
) : Command {
    override suspend fun execute(context: Context) = Unit
}
