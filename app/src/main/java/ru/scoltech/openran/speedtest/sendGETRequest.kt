package ru.scoltech.openran.speedtest

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.URL

enum class RequestType { START, STOP }

suspend fun sendGETRequest(
    address: String,
    requestType: RequestType,
    timeout: Int,
    context: Context,
    value: String = ""
): String {
    val url = when (requestType) {
        RequestType.START -> "http://$address:${ApplicationConstants.HTTP_SERVER_PORT}/start-iperf?args=$value"
        RequestType.STOP -> "http://$address:${ApplicationConstants.HTTP_SERVER_PORT}/stop-iperf"
    }
    val channel = Channel<String>()
    CoroutineScope(Dispatchers.IO).launch{
        val request = URL(url)
        val connection = request.openConnection()
        connection.doOutput = true
        channel.trySend(connection.getInputStream().bufferedReader().readLine())
    }
    CoroutineScope(Dispatchers.IO).launch {
        delay(timeout.toLong())
        channel.trySend("error")
    }
    return channel.receive()
}
