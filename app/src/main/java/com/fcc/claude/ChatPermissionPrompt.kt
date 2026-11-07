package com.fcc.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun QuestionPrompt(prompt: PermissionCard, onAnswer: (JSONObject) -> Unit, onCancel: () -> Unit) {
    val questions = remember(prompt.id) {
        val source = prompt.input.optJSONArray("questions") ?: JSONArray()
        (0 until source.length()).mapNotNull { index ->
            val question = source.optJSONObject(index) ?: return@mapNotNull null
            val options = question.optJSONArray("options") ?: JSONArray()
            ClaudeQuestion(
                question = question.optString("question"),
                header = question.optString("header"),
                multiSelect = question.optBoolean("multiSelect"),
                options = (0 until options.length()).mapNotNull { optionIndex ->
                    options.optJSONObject(optionIndex)?.let { QuestionOption(it.optString("label"), it.optString("description")) }
                },
            )
        }
    }
    val selected = remember(prompt.id) { mutableStateMapOf<Int, Set<Int>>() }
    val otherAnswers = remember(prompt.id) { mutableStateMapOf<Int, String>() }
    val ready = questions.isNotEmpty() && questions.indices.all { index ->
        val choices = selected[index].orEmpty()
        choices.isNotEmpty() && (questions[index].options.size !in choices || !otherAnswers[index].isNullOrBlank())
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Claude quer saber", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        questions.forEachIndexed { index, question ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (question.header.isNotBlank()) Text(question.header, color = MutedText, fontSize = 12.sp)
                Text(question.question, color = MainText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                if (question.multiSelect) Text("Selecione uma ou mais opções", color = MutedText, fontSize = 12.sp)
                val options = question.options + QuestionOption("Outra resposta", "")
                options.forEachIndexed { optionIndex, option ->
                    val isSelected = optionIndex in selected[index].orEmpty()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SurfaceRaised else Color.Transparent)
                            .border(1.dp, if (isSelected) Accent else SurfaceRaised, RoundedCornerShape(12.dp))
                            .clickable {
                                val existing = selected[index].orEmpty()
                                selected[index] = if (!question.multiSelect) setOf(optionIndex)
                                    else if (isSelected) existing - optionIndex else existing + optionIndex
                            }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val shape = if (question.multiSelect) RoundedCornerShape(5.dp) else CircleShape
                        Box(Modifier.size(19.dp).border(1.dp, if (isSelected) Accent else MutedText, shape), contentAlignment = Alignment.Center) {
                            if (isSelected) Box(Modifier.size(11.dp).clip(shape).background(Accent))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = MainText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (option.description.isNotBlank()) Text(option.description, color = MutedText, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    if (isSelected && optionIndex == question.options.size) {
                        BasicTextField(
                            value = otherAnswers[index].orEmpty(),
                            onValueChange = { otherAnswers[index] = it },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceRaised).padding(12.dp),
                            textStyle = TextStyle(color = MainText, fontSize = 14.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                            decorationBox = { inner ->
                                Box {
                                    if (otherAnswers[index].isNullOrEmpty()) Text("Digite sua resposta", color = MutedText, fontSize = 14.sp)
                                    inner()
                                }
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionChip("Cancelar", MutedText, action = onCancel)
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (ready) Accent else SurfaceRaised)
                    .clickable(enabled = ready) {
                        val answers = JSONObject()
                        questions.forEachIndexed { index, question ->
                            val value = selected[index].orEmpty().sorted().map { choice ->
                                if (choice == question.options.size) otherAnswers[index].orEmpty().trim()
                                else question.options[choice].label
                            }.joinToString(", ")
                            answers.put(question.question, value)
                        }
                        onAnswer(answers)
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Enviar resposta", color = if (ready) Backdrop else MutedText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}
