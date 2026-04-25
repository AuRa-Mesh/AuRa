package com.example.aura.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.ui.components.MeshBackground
import com.example.aura.ui.theme.MeshBorder
import com.example.aura.ui.theme.MeshCard
import com.example.aura.ui.theme.MeshCyan
import com.example.aura.ui.theme.MeshTextPrimary
import com.example.aura.ui.theme.MeshTextSecondary
import kotlinx.coroutines.launch

private data class OnboardSlide(
    val imageRes: Int,
    val title: Int,
    val body: Int,
    val isWelcome: Boolean,
)

private val onboardSlides = listOf(
    OnboardSlide(R.drawable.tutorial_onboard_01_welcome, R.string.first_onboard_01_title, R.string.first_onboard_01_body, isWelcome = true),
    OnboardSlide(R.drawable.tutorial_onboard_02_auth, R.string.first_onboard_02_title, R.string.first_onboard_02_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_03_connections, R.string.first_onboard_03_title, R.string.first_onboard_03_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_04_channels, R.string.first_onboard_04_title, R.string.first_onboard_04_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_05_groups, R.string.first_onboard_05_title, R.string.first_onboard_05_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_06_map_nodes, R.string.first_onboard_06_title, R.string.first_onboard_06_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_07_map_beacon, R.string.first_onboard_07_title, R.string.first_onboard_07_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_08_settings, R.string.first_onboard_08_title, R.string.first_onboard_08_body, isWelcome = false),
    OnboardSlide(R.drawable.tutorial_onboard_09_chat_lists, R.string.first_onboard_09_title, R.string.first_onboard_09_body, isWelcome = false),
)

@Composable
private fun OnboardHeroImage(
    imageRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(14.dp),
        color = MeshCard,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Первый запуск: приветствие (скрин 1) и далее слайды по скриншотам приложения.
 * Крестик вверху справа закрывает инструкцию на любом шаге.
 */
@Composable
fun FirstLaunchInstructionScreen(
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { onboardSlides.size },
    )
    val scope = rememberCoroutineScope()
    val lastIndex = onboardSlides.lastIndex

    MeshBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Spacer(modifier = Modifier.height(44.dp))
                Text(
                    text = stringResource(R.string.first_onboard_guide_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    color = MeshTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val slide = onboardSlides[page]
                    val titleText = stringResource(slide.title)
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        OnboardHeroImage(
                            imageRes = slide.imageRes,
                            contentDescription = titleText,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            text = titleText,
                            color = MeshCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(slide.body),
                            color = MeshTextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                        if (slide.isWelcome) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(R.string.first_onboard_skip_instruction),
                                        color = MeshTextSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(1)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MeshCyan,
                                        contentColor = MeshCard,
                                    ),
                                ) {
                                    Text(stringResource(R.string.first_onboard_welcome_next))
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(onboardSlides.size) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == i) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == i) MeshCyan
                                    else MeshBorder,
                                ),
                        )
                    }
                }
                if (pagerState.currentPage > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                        ) {
                            Text(
                                stringResource(R.string.first_launch_back),
                                color = if (pagerState.currentPage > 0) MeshCyan else MeshTextSecondary,
                            )
                        }
                        if (pagerState.currentPage < lastIndex) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeshCyan,
                                    contentColor = MeshCard,
                                ),
                            ) {
                                Text(stringResource(R.string.first_launch_next))
                            }
                        } else {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeshCyan,
                                    contentColor = MeshCard,
                                ),
                            ) {
                                Text(stringResource(R.string.first_launch_start_app))
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 4.dp, end = 4.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.first_launch_close_cd),
                    tint = Color.White,
                )
            }
        }
    }
}
