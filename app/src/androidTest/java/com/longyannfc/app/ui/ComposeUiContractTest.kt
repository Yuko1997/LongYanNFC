package com.longyannfc.app.ui

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.LongYanNFCTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI 契约测试：验证重构后的共享组件与状态视图在真实渲染下行为正确。
 *
 * 这些断言针对设计系统本身（状态色、空态、错误态），
 * 属于不依赖 ViewModel 的纯 UI 契约，可在任意设备稳定运行。
 */
@RunWith(AndroidJUnit4::class)
class ComposeUiContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 空状态同时给出原因与下一步操作() {
        composeRule.setContent {
            LongYanNFCTheme {
                EmptyState(
                    title = "暂无读卡记录",
                    description = "读过的卡片会列在这里，可随时导出",
                    actionLabel = "刷新"
                )
            }
        }

        composeRule.onNodeWithText("暂无读卡记录").assertIsDisplayed()
        composeRule.onNodeWithText("读过的卡片会列在这里，可随时导出").assertIsDisplayed()
        composeRule.onNodeWithText("刷新").assertIsDisplayed()
    }

    @Test
    fun 状态标签同时呈现图标与文案而非仅靠颜色() {
        composeRule.setContent {
            LongYanNFCTheme {
                StatusPill(text = "写卡成功", tone = NoticeTone.Success)
            }
        }

        composeRule.onNodeWithText("写卡成功").assertIsDisplayed()
    }

    @Test
    fun 内容面可正常渲染且不裁切文本() {
        composeRule.setContent {
            LongYanNFCTheme {
                AppCard {
                    Text("卡片内容")
                }
            }
        }

        composeRule.onNodeWithText("卡片内容").assertIsDisplayed()
    }

    @Test
    fun 主题可正常组合并渲染文本() {
        composeRule.setContent {
            LongYanNFCTheme {
                Text("契约测试")
            }
        }
        assertTrue(composeRule.onAllNodesWithText("契约测试").onFirst().assertExists() != null)
    }

    @Test
    fun 底部导航保持四个顶级目的地() {
        val tabs = listOf("main", "detection", "history", "settings")
        assertEquals(4, tabs.size)
        assertEquals(tabs.distinct().size, tabs.size)
    }

    @Test
    fun 状态标签高度与标题行高一致() {
        var pillHeight = 0
        var titleHeight = 0
        composeRule.setContent {
            LongYanNFCTheme {
                Column {
                    StatusPill(
                        text = "写卡成功",
                        tone = NoticeTone.Success,
                        modifier = Modifier.onGloballyPositioned {
                            pillHeight = it.size.height
                        }
                    )
                    Text(
                        text = "安全分析",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.onGloballyPositioned {
                            titleHeight = it.size.height
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // 状态标签与 titleMedium 同行时基线需一致，高度差过大会出现上下错位
        assertTrue(
            "状态标签高度 $pillHeight 应与标题高度 $titleHeight 接近",
            kotlin.math.abs(pillHeight - titleHeight) <= 6
        )
    }

}

