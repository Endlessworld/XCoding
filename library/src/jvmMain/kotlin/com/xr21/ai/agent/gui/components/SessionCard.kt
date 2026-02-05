package com.xr21.ai.agent.gui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.SessionStatus
import com.xr21.ai.agent.gui.UiSessionInfo
import com.xr21.ai.agent.gui.getCurrentChatColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SessionCard(
    session: UiSessionInfo,
    sessionStatus: SessionStatus = SessionStatus.IDLE,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = getCurrentChatColors()

    // 鼠标悬停状态
    val isHovered = remember { mutableStateOf(false) }

    // 运行中状态动画
    val infiniteTransition = rememberInfiniteTransition(label = "running")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // 磨砂玻璃会话卡片 - 精致紫调
    Card(
        modifier = modifier.fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            .clickable(onClick = onClick)
            .onMouseEnter { isHovered.value = true }
            .onMouseLeave { isHovered.value = false },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            chatColors.glassGradientStart,
                            chatColors.glassGradientEnd.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    width = if (sessionStatus == SessionStatus.RUNNING) 2.dp else 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (sessionStatus == SessionStatus.RUNNING) {
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha * 0.8f)
                            )
                        } else {
                            listOf(
                                chatColors.borderColor.copy(alpha = 0.4f),
                                chatColors.borderColor.copy(alpha = 0.15f)
                            )
                        }
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 顶部区域
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 标题和消息数量区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // 标题
                        Text(
                            text = session.briefDescription.ifEmpty { "新会话" },
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                                .padding(end = 12.dp)
                        )

                        // 消息数量 - 右上角
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${session.messageCount} 条", style = TextStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 描述文字区域
                    Text(
                        text = session.briefDescription.ifEmpty { "新会话" },
                        style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 13.sp
                        ),
                        modifier = Modifier.height(32.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 底部区域 - 状态和时间
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 左下角 - 状态指示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 状态圆点
                        Box(
                            modifier = Modifier.size(8.dp)
                                .background(
                                    if (sessionStatus == SessionStatus.RUNNING) {
                                        Color(0xFF4CAF50)  // 绿色圆点 - 运行中
                                    } else {
                                        Color(0xFF9E9E9E)  // 灰色圆点 - 已完成
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                        )

                        // 状态文字
                        Text(
                            text = if (sessionStatus == SessionStatus.RUNNING) "运行中" else "已完成",
                            style = TextStyle(
                                color = if (sessionStatus == SessionStatus.RUNNING) {
                                    Color(0xFF4CAF50)
                                } else {
                                    Color(0xFF9E9E9E)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // 右下角 - 时间和删除按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 时间
                        Text(
                            text = session.lastUpdated,
                            style = TextStyle(
                                color = chatColors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        )

                        // 删除按钮 - 鼠标悬停时显示
                        if (isHovered.value) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 鼠标悬停扩展函数
private fun Modifier.onMouseEnter(
    onEnter: () -> Unit
) = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Enter) {
                onEnter()
            }
        }
    }
}

private fun Modifier.onMouseLeave(
    onLeave: () -> Unit
) = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Exit) {
                onLeave()
            }
        }
    }
}
