package com.example.kaone.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.model.Model as TFLiteModel
import kotlin.math.sqrt

class IdolClassifier(context: Context) {

    // 1. 載入模型
    private var model: FacenetEmbeddingModel? = try {
        val options = TFLiteModel.Options.Builder()
            .setDevice(TFLiteModel.Device.CPU)
            .setNumThreads(4)
            .build()
        FacenetEmbeddingModel.newInstance(context, options)
    } catch (e: Exception) {
        Log.e("IDOL_AI", "❌ 模型載入失敗: ${e.message}")
        null
    }

    // 2. 載入特徵資料庫
    private val featureDatabase: Map<String, FloatArray> by lazy {
        try {
            val jsonString = context.assets.open("idol_features.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, FloatArray>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val jsonArray = jsonObject.getJSONArray(key)
                val vector = FloatArray(jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    vector[i] = jsonArray.getDouble(i).toFloat()
                }
                map[key] = vector
            }
            Log.i("IDOL_AI", "✅ 成功載入 ${map.size} 個成員特徵")
            map
        } catch (e: Exception) {
            Log.e("IDOL_AI", "❌ 載入 JSON 失敗: ${e.message}")
            emptyMap()
        }
    }

    data class RecognitionResult(
        val group: String,
        val member: String,
        val confidence: Float,
        val allScores: String
    )

    fun classify(bitmap: Bitmap): RecognitionResult? {
        if (model == null) {
            Log.e("IDOL_AI", "辨識失敗：模型未初始化")
            return null
        }
        if (featureDatabase.isEmpty()) {
            Log.e("IDOL_AI", "辨識失敗：特徵庫是空的")
            return null
        }

        try {
            val size = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)

            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(127.5f, 128f))
                .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(cropped)
            val processedImage = imageProcessor.process(tensorImage)

            val outputs = model?.process(processedImage.tensorBuffer)
            // 💡 如果這裡報紅，請檢查你的模型輸出名稱是否為 output_0
            val currentVector = outputs?.outputFeature0AsTensorBuffer?.floatArray ?: return null

            var bestLabel = ""
            var maxSim = -1f
            val details = StringBuilder("相似度分析：\n")

            for ((label, refVector) in featureDatabase) {
                val sim = cosineSimilarity(currentVector, refVector)
                val displayName = label.split(":").last().trim().split("|").first()
                details.append("${displayName}: ${"%.2f".format(sim * 100)}%\n")

                if (sim > maxSim) {
                    maxSim = sim
                    bestLabel = label
                }
            }

            Log.d("IDOL_AI", "最佳匹配: $bestLabel, 分數: $maxSim")

            // 💡 除錯用：降低門檻到 0.3，看看有沒有反應
            if (maxSim < 0.3f) {
                Log.w("IDOL_AI", "相似度太低 ($maxSim)，無法確認身份")
                return null
            }

            val parts = bestLabel.split(":")
            return RecognitionResult(
                group = if (parts.size >= 2) parts[0].trim() else "BTS",
                member = if (parts.size >= 2) parts[1].split("|")[0].trim() else bestLabel,
                confidence = maxSim,
                allScores = details.toString()
            )

        } catch (e: Exception) {
            Log.e("IDOL_AI", "辨識異常: ${e.message}")
            return null
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0; var n1 = 0.0; var n2 = 0.0
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        val score = (dot / (sqrt(n1) * sqrt(n2))).toFloat()
        return if (score.isNaN()) 0f else score
    }

    fun close() = model?.close()
}