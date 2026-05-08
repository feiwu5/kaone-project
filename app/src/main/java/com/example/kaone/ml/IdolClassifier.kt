package com.example.kaone.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.kaone.ml.Model
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class IdolClassifier(context: Context) {
    private val model = Model.newInstance(context)

    private val labels: List<String> by lazy {
        try {
            FileUtil.loadLabels(context, "labels.txt")
        } catch (e: Exception) {
            Log.e("IDOL_AI", "無法讀取標籤檔", e)
            emptyList()
        }
    }

    data class RecognitionResult(
        val group: String,
        val member: String,
        val confidence: Float,
        val allScores: String
    )

    fun classify(bitmap: Bitmap): RecognitionResult? {
        try {
            // 1. 中央裁切以維持臉部比例 (與 Colab 同步：center_crop_and_resize)
            val size = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)

            val imageProcessor = ImageProcessor.Builder()
                // 💡 使用 BILINEAR，因為 0.4.4 版本不支援 BICUBIC
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                // 維持 0~255，因為模型內部已有 Rescaling 層 (1./255)
                .add(NormalizeOp(0f, 1f))
                .build()

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(croppedBitmap)
            val processedImage = imageProcessor.process(tensorImage)

            val outputs = model.process(processedImage.tensorBuffer)
            val floatArray = outputs.outputFeature0AsTensorBuffer.floatArray

            var maxIdx = -1
            var maxScore = -1f
            val scoreDetails = StringBuilder("AI 預測分布：\n")

            Log.i("IDOL_AI", "========= AI 辨識 Debug =========")
            for (i in floatArray.indices) {
                val fullName = if (i < labels.size) labels[i] else "索引$i"
                val score = floatArray[i]

                // 解析顯示名稱
                val displayName = if (fullName.contains(":")) fullName.split(":")[1] else fullName
                scoreDetails.append("${displayName.split("|").first()}: ${"%.2f".format(score)}\n")

                Log.i("IDOL_AI", "索引 $i ($fullName): $score")

                if (score > maxScore) {
                    maxScore = score
                    maxIdx = i
                }
            }

            // 💡 門檻值調低至 0.15f，提供更靈敏的偵測反饋
            if (maxIdx == -1 || maxScore < 0.15f) return null

            val rawLabel = if (maxIdx < labels.size) labels[maxIdx] else "Index:$maxIdx"
            val parts = rawLabel.split(":")

            return RecognitionResult(
                group = if (parts.size >= 2) parts[0].trim() else "BTS",
                member = if (parts.size >= 2) parts[1].trim() else rawLabel,
                confidence = maxScore,
                allScores = scoreDetails.toString()
            )
        } catch (e: Exception) {
            Log.e("IDOL_AI", "辨識異常", e)
            return null
        }
    }

    fun close() = model.close()
}
