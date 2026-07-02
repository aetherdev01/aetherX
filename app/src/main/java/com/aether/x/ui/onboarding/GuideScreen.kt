package com.aether.x.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.x.R
import com.aether.x.ui.theme.AccentBlue
import com.aether.x.ui.theme.BgVoid
import com.aether.x.ui.theme.StrokeSubtle
import com.aether.x.ui.theme.TextMuted
import com.aether.x.ui.theme.TextPrimary
import com.aether.x.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class GuidePage(val title: Int, val body: Int)

private val guidePages = listOf(
    GuidePage(R.string.guide_title_1, R.string.guide_body_1),
    GuidePage(R.string.guide_title_2, R.string.guide_body_2),
    GuidePage(R.string.guide_title_3, R.string.guide_body_3),
    GuidePage(R.string.guide_title_4, R.string.guide_body_4),
)

/**
 * Halaman panduan/selamat datang — rework total kedua.
 *
 * Rework sebelumnya membuat ilustrasi jadi hero penuh dengan animasi Canvas
 * (ring radar berputar, ripple, parallax scale saat swipe) yang di layar
 * kecil terasa terlalu besar dan ramai. Versi ini menurunkan hero jadi badge
 * ikon kecil dan statis (lihat [GuideIllustration]) di dalam kartu datar
 * bergaya sama seperti [com.aether.x.ui.components.SectionCard] pada tab
 * lain, lalu memberi teks jauh lebih banyak ruang karena itulah inti dari
 * halaman panduan. Transisi antar halaman disederhanakan jadi fade biasa,
 * tanpa parallax/scale manual.
 */
@Composable
fun GuideScreen(
    onFinish: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == guidePages.lastIndex
    val isFirstPage = pagerState.currentPage == 0

    Scaffold(containerColor = BgVoid) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding),
        ) {
            // --- Header: back button, step label, skip ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    AnimatedVisibility(visible = !isFirstPage) {
                        IconButton(onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.guide_back),
                                tint = TextSecondary,
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(
                        R.string.guide_step_label,
                        pagerState.currentPage + 1,
                        guidePages.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )

                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.guide_skip), color = TextMuted)
                }
            }

            // --- Progress bar: satu batang tipis per halaman ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                guidePages.indices.forEach { index ->
                    val filled = index <= pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (filled) AccentBlue else StrokeSubtle),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                ) {
                    // --- Badge ikon: kecil, statis, rata kiri di atas judul ---
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                    ) {
                        GuideIllustration(
                            page = page,
                            modifier = Modifier.size(132.dp),
                        )
                    }

                    // --- Teks: judul besar + body, rata kiri untuk hierarki lebih jelas ---
                    Text(
                        text = stringResource(guidePages[page].title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(guidePages[page].body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }

            // --- CTA ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 4.dp),
            ) {
                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    AnimatedContent(
                        targetState = isLastPage,
                        transitionSpec = {
                            (fadeIn(tween(150)) togetherWith fadeOut(tween(150)))
                        },
                        label = "cta-label",
                    ) { last ->
                        Text(
                            text = if (last) {
                                stringResource(R.string.guide_finish)
                            } else {
                                stringResource(R.string.guide_next)
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
