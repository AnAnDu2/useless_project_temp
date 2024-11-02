package com.example.battery

import android.util.Log
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

class CryptoMiner(
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) {
    private var miningJob: Job? = null
    private val scope = CoroutineScope(coroutineContext)

    companion object {
        private const val DIFFICULTY = 80 // Number of leading zeros required
        private const val TAG = "CryptoMiner"
    }

    sealed class MiningResult {
        data class Success(val hash: String, val nonce: Long) : MiningResult()
        data class Progress(val attempts: Long) : MiningResult()
        data class Error(val message: String) : MiningResult()
    }

    fun startMining(
        data: String,
        onResult: (MiningResult) -> Unit
    ) {
        if (miningJob?.isActive == true) {
            Log.d(TAG, "Mining already in progress")
            return
        }

        miningJob = scope.launch {
            try {
                var nonce = 0L
                var attempts = 0L
                val target = "0".repeat(DIFFICULTY)

                while (isActive) {
                    val blockData = data + nonce
                    val hash = calculateHash(blockData)

                    if (hash.substring(0, DIFFICULTY) == target) {
                        withContext(Dispatchers.Main) {
                            onResult(MiningResult.Success(hash, nonce))
                        }

                        break
                    }

                    nonce++
                    attempts++

                    if (attempts % 1 == 0L) {
                        withContext(Dispatchers.Main) {
                            calculateHash(data + nonce)
                            onResult(MiningResult.Progress(attempts))
                        }
                    }


                    // Prevent device overheating

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(MiningResult.Error(e.message ?: "Unknown error occurred"))
                }
            }
        }
    }

    fun stopMining() {
        miningJob?.cancel()
        miningJob = null
    }
    private fun complexHash(input: String, iterations: Int = 1000): String {
        var data = input.toByteArray(StandardCharsets.UTF_8)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")

        repeat(iterations) { i ->
            // First, hash with SHA-256
            data = sha256.digest(data)

            // Then, hash the result with SHA-512
            data = sha512.digest(data)

            // Optionally: add a salt based on the iteration number for additional complexity
            val salt = i.toString().toByteArray(StandardCharsets.UTF_8)
            data = data + salt
        }

        // Convert the final byte array to a hex string
        return data.joinToString("") { byte -> "%02x".format(byte) }
    }


    private fun calculateHash(input: String): String {
        return complexHash(input, 3000)
    }
}