package com.fcc.claude

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class TermuxCommandExecutor(private val context: Context) {
    fun execute(cwd: File, command: String): DeviceToolResult {
        if (context.checkSelfPermission(RUN_COMMAND_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return DeviceToolResult("Conceda ao FCC a permissão RUN_COMMAND nas configurações do Android.", true)
        }
        if (command.toByteArray(Charsets.UTF_8).size > MAX_COMMAND_BYTES) {
            return DeviceToolResult("O comando excede o limite aceito pelo Termux.", true)
        }

        val requestId = UUID.randomUUID().toString()
        val future = CompletableFuture<DeviceToolResult>()
        TermuxCommandResultReceiver.pending[requestId] = future
        return try {
            val callback = Intent(context, TermuxCommandResultReceiver::class.java)
                .setAction(requestId)
                .putExtra(EXTRA_REQUEST_ID, requestId)
            val flags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, requestId.hashCode(), callback, flags)
            val intent = Intent(ACTION_RUN_COMMAND)
                .setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                .putExtra(EXTRA_COMMAND_PATH, TermuxCommandSpec.COMMAND_PATH)
                .putExtra(EXTRA_ARGUMENTS, TermuxCommandSpec.arguments(command))
                .putExtra(EXTRA_WORKDIR, cwd.canonicalPath)
                .putExtra(EXTRA_BACKGROUND, true)
                .putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            context.startService(intent)
            future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            DeviceToolResult("O Termux não respondeu dentro de 65 segundos.", true)
        } catch (failure: Exception) {
            DeviceToolResult(failure.message ?: "Não foi possível encaminhar o comando ao Termux.", true)
        } finally {
            TermuxCommandResultReceiver.pending.remove(requestId)
        }
    }

    companion object {
        private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_REQUEST_ID = "fcc_request_id"
        private const val COMMAND_TIMEOUT_SECONDS = 65L
        private const val MAX_COMMAND_BYTES = 60 * 1024
    }
}

internal class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("fcc_request_id") ?: return
        val result = intent.getBundleExtra("result")
        val errorCode = result?.getInt("err", android.app.Activity.RESULT_OK)
            ?: android.app.Activity.RESULT_CANCELED
        val errorMessage = result?.getString("errmsg")
        val toolResult = TermuxCommandSpec.result(
            stdout = result?.getString("stdout").orEmpty(),
            stderr = result?.getString("stderr").orEmpty(),
            exitCode = result?.getInt("exitCode", 1) ?: 1,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
        pending.remove(requestId)?.complete(toolResult)
    }

    companion object {
        internal val pending = ConcurrentHashMap<String, CompletableFuture<DeviceToolResult>>()
    }
}
