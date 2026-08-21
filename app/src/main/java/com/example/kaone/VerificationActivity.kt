package com.example.kaone

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class VerificationActivity : ComponentActivity() {

    private lateinit var tvQuestion: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var btnNext: Button

    private val questions = listOf(
        "1. 卡面印刷：圖案是否清晰，無模糊、重影或明顯網點？",
        "2. 對光觀察：將卡片旋轉對光，表面是否無異常細微刮痕或點狀凹陷？",
        "3. 邊緣切割：四角圓弧是否自然，有無手工切割造成的毛邊或層次？",
        "4. 顏色飽和度：對比官方圖後，顏色是否過深或過淺（仿品常有色偏）？",
        "5. 材質紋理：對光時是否有官方特定的布紋或珍珠光澤，厚度是否適中？"
    )

    private var currentQuestionIndex = 0
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        tvQuestion = findViewById(R.id.tvQuestion)
        radioGroup = findViewById(R.id.radioGroup)
        btnNext = findViewById(R.id.btnNext)

        updateQuestion()

        btnNext.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "請針對該項目進行評估", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedId == R.id.rbYes) {
                score++
            }

            currentQuestionIndex++

            if (currentQuestionIndex < questions.size) {
                updateQuestion()
            } else {
                showResult()
            }
        }
    }

    private fun updateQuestion() {
        tvQuestion.text = questions[currentQuestionIndex]
        radioGroup.clearCheck()
        if (currentQuestionIndex == questions.size - 1) {
            btnNext.text = "查看鑑定結果"
        }
    }

    private fun showResult() {
        val result = when {
            score >= 5 -> "鑑定結果：極大機率為真品且品相完美。\n(符合項目: $score/${questions.size})"
            score >= 3 -> "鑑定結果：存在瑕疵或疑點，建議要求更多對光影片。\n(符合項目: $score/${questions.size})"
            else -> "鑑定結果：疑似仿品或嚴重廠損，請務必取消交易！\n(符合項目: $score/${questions.size})"
        }

        tvQuestion.text = result
        radioGroup.visibility = View.GONE
        btnNext.text = "完成並返回"
        btnNext.setOnClickListener {
            finish()
        }
    }
}
