package com.example.kaone.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdolClassifier(context: Context) {

    private var model: FacenetEmbeddingModel? = try {
        FacenetEmbeddingModel.newInstance(context)
    } catch (e: Throwable) {
        Log.e("IDOL_AI", "❌ 模型載入失敗: ${e.message}")
        null
    }

    // 🆕 ML Kit 人臉偵測器設定
    private val detector = try {
        val detectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(detectorOptions)
    } catch (e: Throwable) {
        Log.e("IDOL_AI", "❌ FaceDetector 載入失敗: ${e.message}")
        null
    }

    // 🆕 資料結構：每個明星對應一組特徵向量 (List<FloatArray>)
    private val featureDatabase: Map<String, List<FloatArray>> by lazy {
        try {
            val jsonString = context.assets.open("kpop_face_embeddings.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, List<FloatArray>>()
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val multiEmbedsArray = jsonObject.getJSONArray(key)
                val embeddingsList = mutableListOf<FloatArray>()

                for (i in 0 until multiEmbedsArray.length()) {
                    val vectorArray = multiEmbedsArray.getJSONArray(i)
                    val vector = FloatArray(vectorArray.length())
                    for (j in 0 until vectorArray.length()) {
                        vector[j] = vectorArray.getDouble(j).toFloat()
                    }
                    embeddingsList.add(l2Normalize(vector))
                }
                map[key] = embeddingsList
            }
            Log.i("IDOL_AI", "✅ 成功載入 ${map.size} 個成員的多角度特徵")
            map
        } catch (e: Exception) {
            Log.e("IDOL_AI", "❌ 載入 JSON 失敗: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 辨識圖片中的偶像
     * 現在會先執行 ML Kit Face Detection 進行人臉裁切，再送入模型
     */
    suspend fun classify(bitmap: Bitmap): RecognitionResult? = withContext(Dispatchers.Default) {
        if (model == null || featureDatabase.isEmpty()) return@withContext null

        try {
            // 🆕 1. 使用 ML Kit 偵測人臉並裁切
            val faceBitmap = try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val faces = if (detector != null) Tasks.await(detector.process(inputImage)) else emptyList()
                
                if (faces.isNotEmpty()) {
                    // 取面積最大的臉
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                    val box = face.boundingBox
                    
                    // 確保裁切範圍在 Bitmap 內
                    val left = box.left.coerceAtLeast(0)
                    val top = box.top.coerceAtLeast(0)
                    val width = box.width().coerceAtMost(bitmap.width - left)
                    val height = box.height().coerceAtMost(bitmap.height - top)
                    
                    if (width > 0 && height > 0) {
                        Log.d("IDOL_AI", "✅ 偵測到人臉並裁切: $box")
                        Bitmap.createBitmap(bitmap, left, top, width, height)
                    } else {
                        Log.w("IDOL_AI", "⚠️ 人臉框異常，使用原圖")
                        bitmap
                    }
                } else {
                    Log.w("IDOL_AI", "⚠️ 未偵測到人臉，請靠近拍小卡 face，目前使用原圖辨識 (效果可能極差)")
                    bitmap
                }
            } catch (e: Exception) {
                Log.e("IDOL_AI", "人臉偵測過程出錯: ${e.message}")
                bitmap
            }

            // 2. 影像預處理 (Resize 成 160x160 並歸一化)
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(160, 160, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(127.5f, 127.5f))
                .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(faceBitmap) // 使用裁切後的圖片
            val processedImage = imageProcessor.process(tensorImage)

            val outputs = model?.process(processedImage.tensorBuffer)
            val rawVector = outputs?.outputFeature0AsTensorBuffer?.floatArray ?: return@withContext null
            val currentVector = l2Normalize(rawVector)

            // 3. 比對特徵庫
            val allMatches = featureDatabase.map { (label, refVectors) ->
                var memberMaxScore = -1.0f
                for (refVector in refVectors) {
                    val score = cosineSimilarity(currentVector, refVector)
                    if (score > memberMaxScore) {
                        memberMaxScore = score
                    }
                }
                label to memberMaxScore
            }.sortedByDescending { it.second }

            // 診斷 Log
            Log.d("IDOL_AI", "--- 辨識診斷 (人臉裁切版) ---")
            allMatches.take(3).forEachIndexed { i, pair ->
                Log.d("IDOL_AI", "Top ${i+1}: ${pair.first} (${pair.second})")
            }

            val (bestLabel, maxSim) = allMatches.first()

            // 門檻過低則不判定
            if (maxSim < 0.40f) { // 有了裁切後，分數應該會變準，稍微調整門檻
                Log.w("IDOL_AI", "❌ 匹配度過低 ($maxSim)，放棄辨識")
                return@withContext null
            }

            val parts = bestLabel.split(":")
            RecognitionResult(
                group = if (parts.size >= 2) parts[0].trim() else "未知",
                member = if (parts.size >= 2) parts[1].trim() else bestLabel,
                confidence = maxSim
            )
        } catch (e: Exception) {
            Log.e("IDOL_AI", "辨識異常: ${e.message}")
            null
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

    fun close() {
        try { model?.close() } catch (_: Throwable) {}
        try { detector?.close() } catch (_: Throwable) {}
    }
    
    data class RecognitionResult(val group: String, val member: String, val confidence: Float)
}
