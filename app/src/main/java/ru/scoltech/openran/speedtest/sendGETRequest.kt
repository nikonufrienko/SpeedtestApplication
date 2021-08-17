package ru.scoltech.openran.speedtest

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean


suspend fun sendGETRequest(
    address: String,
    requestType: MainActivity.Companion.RequestType,
    timeout: Int,
    context: Context,
    value: String = ""
): String {
    var result = ""
    val isComplete = AtomicBoolean(false)
    val queue = Volley.newRequestQueue(context)
    val url = when (requestType) {
        MainActivity.Companion.RequestType.START -> "http://$address:5000/start-iperf?args=$value"
        MainActivity.Companion.RequestType.STOP -> "http://$address:5000/stop-iperf"
    }
    val start = System.currentTimeMillis()
    val stringRequest = StringRequest(
        Request.Method.GET, url,
        { response ->
            result = response
            isComplete.set(true)
        },
        {
            result = "error"
            isComplete.set(true)
            Log.d("get:", "Error \n $url \n $it")
        })
    queue.add(stringRequest)

    while (System.currentTimeMillis() - start < timeout) {
        delay(10)
        if (isComplete.get()) {
            return result
        }
    }
    return "error"
}
