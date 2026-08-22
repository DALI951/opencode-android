package ai.opencode.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object TermuxCommandHelper {

    private const val TAG = "TermuxCommand"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendCommand(
        context: Context,
        command: String,
        args: Array<String> = emptyArray(),
        workDir: String? = null,
        background: Boolean = false,
        openTerminal: Boolean = true
    ): Boolean {
        return try {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", command)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
                workDir?.let {
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", it)
                }
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                if (openTerminal) {
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                }
            }
            context.startService(intent)
            Log.d(TAG, "Sent command to Termux: $command ${args.joinToString(" ")}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to Termux", e)
            false
        }
    }

    fun openTermux(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.TermuxActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Termux", e)
            false
        }
    }

    fun startOpencodeServe(context: Context, port: Int = 4096): Boolean {
        return sendCommand(
            context = context,
            command = "/data/data/com.termux/files/usr/bin/bash",
            args = arrayOf("-l", "-c", "opencode serve --port $port"),
            background = true,
            openTerminal = false
        )
    }

    fun updateOpencode(context: Context): Boolean {
        return sendCommand(
            context = context,
            command = "/data/data/com.termux/files/usr/bin/bash",
            args = arrayOf("-l", "-c", "npm i -g opencode@latest --force"),
            background = false,
            openTerminal = true
        )
    }
}
