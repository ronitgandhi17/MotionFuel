package com.ronitgandhi.motionfuel.integration

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class WearableBridge(private val context: Context) {
    suspend fun connectedNodeCount(): Int = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await().size
    }.getOrDefault(0)

    suspend fun sendWorkoutCommand(command: String): Result<Int> = runCatching {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            Wearable.getMessageClient(context)
                .sendMessage(node.id, "/motionfuel/workout", command.encodeToByteArray())
                .await()
        }
        nodes.size
    }
}
