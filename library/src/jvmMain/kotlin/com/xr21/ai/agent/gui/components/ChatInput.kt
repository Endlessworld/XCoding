package com.xr21.ai.agent.gui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.xr21.ai.agent.gui.getCurrentChatColors
import com.xr21.ai.agent.gui.getGlassGradientBrush

@Composable
fun ChatInput(
    inputText: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onSendMessage: () -> Unit,
    onStop: (() -> Unit)? = null,
    isLoading: Boolean,
    isSending: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val chatColors = getCurrentChatColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var isFocused by remember { mutableStateOf(false) }
    val hasText = inputText.text.isNotBlank()

    // 暖色系定义
    val warmOrange = Color(0xFFFF6B35)      // 活力橙
    val warmCoral = Color(0xFFFF8E53)       // 珊瑚色
    val warmGold = Color(0xFFFFB347)        // 金色
    val warmRed = Color(0xFFFF4757)        // 暖红
    val warmYellow = Color(0xFFFFD93D)     // 明黄

    // 呼吸灯动画 - 暖色系，聚焦时频率加快到1.2秒
    val breatheAnimation = rememberInfiniteTransition(label = "breathe")
    val breatheAlpha by breatheAnimation.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isFocused) 1200 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    // 焦点边框颜色动画
    val focusBorderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            chatColors.borderColor.copy(alpha = 0.3f)
        },
        animationSpec = tween(300),
        label = "focusBorder"
    )

    // 背景颜色动画
    val inputBgColor by animateColorAsState(
        targetValue = when {
            isPressed -> chatColors.assistantMessageColor.copy(alpha = 0.7f)
            isFocused -> chatColors.assistantMessageColor.copy(alpha = 0.55f)
            else -> chatColors.assistantMessageColor.copy(alpha = 0.4f)
        },
        animationSpec = tween(200),
        label = "inputBg"
    )

    // 发送按钮动画状态
    val sendButtonScale by animateFloatAsState(
        targetValue = when {
            isPressed && hasText -> 0.85f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sendScale"
    )

    val sendButtonAlpha by animateFloatAsState(
        targetValue = if (hasText && !isLoading) 1f else 0.5f,
        animationSpec = tween(200),
        label = "sendAlpha"
    )

    // 磨砂玻璃输入区域 - 精致紫调
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(getGlassGradientBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 统一的输入区域容器 - 呼吸灯特效
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 移除缩放动画，保持固定大小
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                inputBgColor.copy(alpha = 0.65f),
                                inputBgColor.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .then(
                        if (isFocused) {
                            // 聚焦时：暖色系呼吸灯效果（频率1.2秒）
                            Modifier.drawWithCache {
                                onDrawWithContent {
                                    drawContent()

                                    // 边框宽度
                                    val strokeWidth = 4.dp.toPx()
                                    val breathe = breatheAlpha

                                    val gradientBrush = Brush.sweepGradient(
                                        colors = listOf(
                                            warmOrange.copy(alpha = 0.95f * breathe),
                                            warmCoral.copy(alpha = 0.85f * breathe),
                                            warmGold.copy(alpha = 0.75f * breathe),
                                            warmYellow.copy(alpha = 0.65f * breathe),
                                            warmRed.copy(alpha = 0.8f * breathe),
                                            warmCoral.copy(alpha = 0.9f * breathe),
                                            warmOrange.copy(alpha = 0.95f * breathe)
                                        ),
                                        center = Offset(size.width / 2, size.height / 2)
                                    )

                                    drawRoundRect(
                                        brush = gradientBrush,
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                        style = Stroke(width = strokeWidth)
                                    )

                                    // 呼吸时也添加高光效果
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.3f * breathe),
                                        size = Size(size.width, size.height),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                        style = Stroke(width = strokeWidth * 0.25f)
                                    )
                                }
                            }
                        } else {
                            // 非聚焦时：普通边框
                            Modifier.border(
                                width = 1.5.dp,
                                color = chatColors.borderColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter) {
                            // Ctrl+Enter: 手动插入换行符
                            if (event.isCtrlPressed) {
                                onInputChange(TextFieldValue(
                                    text = inputText.text + "\n",
                                    selection = TextRange(inputText.text.length + 1)
                                ))
                                true
                            }
                            // Enter: 发送
                            else if (hasText && !isLoading && !isSending) {
                                onSendMessage()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // 输入框
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp),
                        placeholder = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isFocused) {
                                    // 聚焦时的动态光点提示 - 暖色系呼吸效果
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer {
                                                scaleX = breatheAlpha
                                                scaleY = breatheAlpha
                                            }
                                            .background(
                                                warmOrange,
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isFocused) "输入消息..." else "输入消息... (Enter 发送)",
                                    color = chatColors.textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        enabled = !isLoading,
                        maxLines = 4,
                        interactionSource = interactionSource,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default
                        ),
                    )

                    // 发送按钮 - 无阴影，直接集成在输入框内
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        // 清空按钮（当有内容时显示）
                        if (hasText) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer { scaleX = sendButtonScale }
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onInputChange(TextFieldValue(""))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(18.dp),
                                    tint = chatColors.textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 发送/停止按钮 - 精致紫调
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = sendButtonScale
                                    scaleY = sendButtonScale
                                }
                                .shadow(
                                    elevation = if ((hasText && !isLoading) || (isLoading && onStop != null)) 6.dp else 0.dp,
                                    shape = CircleShape,
                                    ambientColor = if (isLoading || isSending) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    },
                                    spotColor = if (isLoading || isSending) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    }
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isLoading || isSending) {
                                        // 停止按钮颜色
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.error,
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                            )
                                        )
                                    } else {
                                        // 发送按钮颜色 - 紫色渐变
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    }
                                )
                                .then(
                                    if ((hasText && !isLoading) || (isLoading && onStop != null)) {
                                        Modifier.border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.3f),
                                                    Color.White.copy(alpha = 0.15f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = (hasText && !isLoading && !isSending) || (isLoading && onStop != null)
                                ) {
                                    if (isLoading || isSending) {
                                        onStop?.invoke()
                                    } else {
                                        onSendMessage()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading || isSending) {
                                // 显示停止按钮
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "停止",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            } else {
                                // 显示发送按钮
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White.copy(alpha = sendButtonAlpha)
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}
