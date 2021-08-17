package ru.scoltech.openran.speedtest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.core.view.isVisible
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ru.scoltech.openran.speedtest.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.net.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val PING_SERVER_UDP_PORT = 49121
    lateinit var binding: ActivityMainBinding
    lateinit var iperfRunner: IperfRunner

    @Volatile
    private lateinit var pcs: PingCheckServer

    private val justICMPPingInChecking = AtomicBoolean(false)
    val pingerByICMP = ICMPPing()


    private lateinit var pingByUDPButtonDispatcher: RunForShortTimeButtonDispatcher
    private lateinit var pingServerButtonDispatcher: ButtonDispatcherOfTwoStates
    private lateinit var justICMPPingDispatcher: ButtonDispatcherOfTwoStates
    private lateinit var startStopButtonDispatcher: ButtonDispatcherOfTwoStates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.refreshButton.setOnClickListener { refreshAddresses() }

        refreshAddresses()

        iperfRunner = IperfRunner(applicationContext.filesDir.absolutePath).also {
            it.stdoutHandler = ::handleIperfOutput
            it.stderrHandler = ::handleIperfOutput
        }

        startStopButtonDispatcher = ButtonDispatcherOfTwoStates(
            binding.startStopButton, this,
            applicationContext.getString(R.string.stopIperf)
        )

        startStopButtonDispatcher.firstAction = {
            binding.thisISserver.isEnabled = false
            binding.startStopButton.isEnabled = false
            Log.d("getReq", "preparing")
            CoroutineScope(Dispatchers.IO).launch {
                if (binding.thisISserver.isChecked) {
                    startIperf()
                    delay(1000)
                }
                
                val value = sendGETRequest(
                    binding.serverIpField.text.toString(),
                    binding.serverArgs.text.toString(),
                    1000
                )
                Log.d("requestValue", value)
                runOnUiThread {
                    if (value != "error") {
                        if (!binding.thisISserver.isChecked) {
                            Thread.sleep(1000)
                            startIperf()
                        }
                        binding.startStopButton.isEnabled = true
                    } else {
                        binding.startStopButton.isEnabled = true
                        startStopButtonDispatcher.changeState()
                    }
                    binding.iperfOutput.append(value + "\n")
                }
            }
        }

        startStopButtonDispatcher.secondAction = {
            stopIperf()
            binding.thisISserver.isEnabled = true
        }

        pingByUDPButtonDispatcher = RunForShortTimeButtonDispatcher(
            binding.pingUDPButt,
            this,
            applicationContext.getString(R.string.pingTesting)
        ) { resetAct ->
            pingUDPButtonAction(resetAct)
        }

        pingServerButtonDispatcher = ButtonDispatcherOfTwoStates(
            binding.pingServerButt,
            this,
            applicationContext.getString(R.string.bigStop)
        )
        pingServerButtonDispatcher.firstAction = { startPingCheckServer() }
        pingServerButtonDispatcher.secondAction = { stopPingServer() }

        binding.iperfOutput.movementMethod = ScrollingMovementMethod()

        justICMPPingDispatcher = ButtonDispatcherOfTwoStates(
            binding.justPingButt,
            this,
            applicationContext.getString(R.string.bigStop)
        )
        justICMPPingDispatcher.firstAction = {
            justICMPPing()
        }
        justICMPPingDispatcher.secondAction = {
            stopICMPPing()
        }

        binding.expandButton.setOnClickListener {
            if (binding.pingLayout.isVisible) {
                binding.pingLayout.isVisible = false
                binding.expandButton.setImageResource(android.R.drawable.arrow_down_float)

            } else {
                binding.pingLayout.isVisible = true
                binding.expandButton.setImageResource(android.R.drawable.arrow_up_float)

            }
        }
        binding.pingLayout.isVisible = false


        binding.expandButton2.setOnClickListener {
            if (binding.deviceInfoLayout.isVisible) {
                binding.deviceInfoLayout.isVisible = false
                binding.expandButton2.setImageResource(android.R.drawable.arrow_down_float)

            } else {
                binding.deviceInfoLayout.isVisible = true
                binding.expandButton2.setImageResource(android.R.drawable.arrow_up_float)

            }
        }
        binding.deviceInfoLayout.isVisible = false

    }

    private fun startPingCheckServer() {
        binding.pingServerButt.text = getString(R.string.bigStop)
        CoroutineScope(Dispatchers.IO).launch {
            pcs = PingCheckServer(PING_SERVER_UDP_PORT)
            pcs.start()
        }
    }

    private suspend fun sendGETRequest(address: String, value: String, timeout: Int): String {
        var result = ""
        val isComplete = AtomicBoolean(false)
        val queue = Volley.newRequestQueue(this)
        val url = "http://$address:5000/start-iperf?args=$value"
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

    private fun stopPingServer() {
        CoroutineScope(Dispatchers.Main).launch {
            Log.d("ping server", "pcs thread is alive: ${pcs.isAlive}")
            if (pcs.isAlive) {
                pcs.interrupt()
            }
            binding.pingServerButt.text = getString(R.string.startUdpPingServer)
            delay(500)
            Log.d("ping server:", "pcs thread is alive: ${pcs.isAlive}")
        }
    }

    private fun pingUDPButtonAction(afterWorkAct: () -> Unit) = runBlocking {
        val pcl = PingCheckClient()
        Log.d("pingTestButtonAction", "started")
        CoroutineScope(Dispatchers.IO).launch {
            pcl.doPingTest(
                { value: String ->
                    runOnUiThread {
                        binding.pingValue.text = value
                    }
                },
                binding.serverIP.text.toString()
            )
            afterWorkAct()
            Log.d("pingTestButtonAction", "ended")
        }
    }


    private fun refreshAddresses() {
        print("checking ip")
        binding.ipInfo.text = NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.inetAddresses.hasMoreElements() }
            .joinToString(separator = System.lineSeparator()) { networkInterface ->
                val addresses = networkInterface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .joinToString(separator = ", ")
                "${networkInterface.displayName}: $addresses"
            }
    }

    private fun handleIperfOutput(text: String) {
        runOnUiThread {
            binding.iperfOutput.append(text)
        }
    }

    private fun startIperf() {
        iperfRunner.start(binding.iperfArgs.text.toString())

    }

    private fun stopIperf() {
        iperfRunner.stop()
    }

    private fun runIcmpPingAsCommand() = runBlocking {
        val args = binding.iperfArgs.text.toString()
        CoroutineScope(Dispatchers.IO).launch {
            pingerByICMP.performPingWithArgs(args) { line ->
                runOnUiThread {
                    binding.iperfOutput.append(line + "\n")
                }
            }
        }
    }

    private fun stopICMPPing() {
        pingerByICMP.stopExecuting()
    }


    private fun justICMPPing() = runBlocking {
        justICMPPingInChecking.set(true)
        binding.justPingButt.text = getString(R.string.bigStop)
        CoroutineScope(Dispatchers.IO).launch {
            pingerByICMP.justPingByHost(
                binding.serverIP.text.toString()
            ) { value -> runOnUiThread { binding.pingValue.text = value } }
            justICMPPingInChecking.set(false)
        }
    }
}
