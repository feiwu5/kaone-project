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
import kotlin.math.sqrt

class IdolClassifier(context: Context) {

    private var model: FacenetEmbeddingModel? = try {
        FacenetEmbeddingModel.newInstance(context)
    } catch (e: Exception) {
        Log.e("IDOL_AI", "❌ 模型載入失敗: ${e.message}")
        null
    }

    private val featureDatabase: Map<String, FloatArray> by lazy {
        try {
            val jsonString = context.assets.open("kpop_face_embeddings.json").bufferedReader().use { it.readText() }
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
                // 💡 確保底庫向量也經過 L2 歸一化
                map[key] = l2Normalize(vector)
            }
            Log.i("IDOL_AI", "✅ 成功載入 ${map.size} 個成員特徵")
            map
        } catch (e: Exception) {
            Log.e("IDOL_AI", "❌ 載入 JSON 失敗: ${e.message}")
            emptyMap()
        }
    }

    fun classify(bitmap: Bitmap): RecognitionResult? {
        if (model == null || featureDatabase.isEmpty()) return null

        try {
            // 💡 調整 1: 更緊湊的裁切 (60%)。如果照片是遠照，這能大幅提升準確率
            val minDim = minOf(bitmap.width, bitmap.height)
            val cropSize = (minDim * 0.6).toInt()
            val x = (bitmap.width - cropSize) / 2
            val y = (bitmap.height - cropSize) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize)

            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
                // 💡 調整 2: 修正為標準的 FaceNet 歸一化參數
                .add(NormalizeOp(127.5f, 128f))
                .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(cropped)
            val processedImage = imageProcessor.process(tensorImage)

            val outputs = model?.process(processedImage.tensorBuffer)
            val rawVector = outputs?.outputFeature0AsTensorBuffer?.floatArray ?: return null

            // 💡 執行 L2 歸一化，讓向量長度一致
            val currentVector = l2Normalize(rawVector)

            // 計算所有匹配度並排序
            val allMatches = featureDatabase.map { (label, refVector) ->
                label to cosineSimilarity(currentVector, refVector)
            }.sortedByDescending { it.second }

            // 💡 診斷：查看前三名差距
            Log.d("IDOL_AI", "--- 辨識診斷 ---")
            allMatches.take(3).forEachIndexed { i, pair ->
                Log.d("IDOL_AI", "Top ${i+1}: ${pair.first} (${pair.second})")
            }

            val (bestLabel, maxSim) = allMatches.first()

            // 💡 調整 3: 提高門檻，L2 歸一化後低於 0.45 通常是認錯
            if (maxSim < 0.45f) {
                Log.w("IDOL_AI", "❌ 匹配度過低 ($maxSim)，放棄辨識")
                return null
            }

            val parts = bestLabel.split(":")
            return RecognitionResult(
                group = if (parts.size >= 2) parts[0].trim() else "未知",
                member = if (parts.size >= 2) parts[1].trim() else bestLabel,
                confidence = maxSim
            )
        } catch (e: Exception) {
            Log.e("IDOL_AI", "辨識異常: ${e.message}")
            return null
        }
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0.0f
        for (x in v) sum += x * x
        val magnitude = sqrt(sum)
        return if (magnitude == 0f) v else FloatArray(v.size) { i -> v[i] / magnitude }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f
        for (i in v1.indices) dot += v1[i] * v2[i]
        return dot
    }

    fun close() = model?.close()
    data class RecognitionResult(val group: String, val member: String, val confidence: Float)
}