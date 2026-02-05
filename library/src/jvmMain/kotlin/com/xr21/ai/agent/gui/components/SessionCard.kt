package com.xr21.ai.agent.gui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xr21.ai.agent.gui.SessionStatus
import com.xr21.ai.agent.gui.UiSessionInfo
import com.xr21.ai.agent.gui.getCurrentChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: UiSessionInfo,
    sessionStatus: SessionStatus = SessionStatus.IDLE,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatColors = getCurrentChatColors()

    // 运行中状态动画
    val infiniteTransition = rememberInfiniteTransition(label = "running")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // 磨砂玻璃会话卡片 - 精致紫调
    Card(
        modifier = modifier.fillMaxWidth().shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(
                    Brush.verticalGradient(
                        colors = listOf(
                            chatColors.glassGradientStart,
                            chatColors.glassGradientEnd.copy(alpha = 0.85f)
                        )
                    )
                ).border(
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
                    ), shape = RoundedCornerShape(18.dp)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top
            ) {
                // 会话图标 - 带渐变效果和状态指示 - 精致紫调
                Box(
                    modifier = Modifier.size(52.dp).shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = if (sessionStatus == SessionStatus.RUNNING) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                            spotColor = if (sessionStatus == SessionStatus.RUNNING) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            }
                        ).clip(RoundedCornerShape(16.dp)).background(
                            Brush.linearGradient(
                                colors = if (sessionStatus == SessionStatus.RUNNING) {
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            )
                        ), contentAlignment = Alignment.Center
                ) {
                    // 运行中指示器
                    if (sessionStatus == SessionStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // 标题行 - 固定高度确保卡片高度一致
                    Row(
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = session.briefDescription.ifEmpty { "新会话" },
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 状态标签（运行中时显示）- 精致紫调
                            if (sessionStatus == SessionStatus.RUNNING) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                                )
                                            )
                                        ).padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(6.dp).background(
                                                    MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "运行中", style = TextStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 消息计数标签 - 磨砂玻璃风格 - 精致紫调
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                        )
                                    )
                                ).padding(horizontal = 12.dp, vertical = 6.dp)
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

                    // 描述文字区域 - 固定高度确保卡片高度一致
                    Text(
                        text = session.briefDescription.ifEmpty { "新会话" }, style = TextStyle(
                            color = chatColors.textSecondary, fontSize = 13.sp
                        ), modifier = Modifier.height(32.dp), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.lastUpdated, style = TextStyle(
                                color = chatColors.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 删除按钮
                IconButton(
                    onClick = onDelete, modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}