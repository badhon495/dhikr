package com.dhikr.app.feature.tasbih

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.entity.TasbihEntity
import com.dhikr.app.ui.theme.CardShape
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape

@Composable
fun TasbihLibraryScreen(
    viewModel: TasbihLibraryViewModel,
    onOpenTasbih: (String) -> Unit,
    onNewTasbih: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp),
    ) {
        // ---- Header: title + "+ New" pill ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.tasbih_library_title),
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { onNewTasbih() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.tasbih_library_new),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSage,
                )
            }
        }

        // ---- Search pill: surface fill, 1px line border, magnifier in faint ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(46.dp)
                .clip(PillShape)
                .background(colors.surface)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.tasbih_library_search_content_description),
                tint = colors.faint,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tasbih_library_search_placeholder),
                        fontSize = 14.sp,
                        color = colors.faint,
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Result-count line: switches wording depending on query state ----
        Text(
            text = if (state.query.isBlank()) {
                stringResource(
                    R.string.tasbih_library_result_count_all,
                    state.builtInCount,
                    state.customCount,
                )
            } else {
                stringResource(
                    R.string.tasbih_library_result_count_filtered,
                    state.results.size,
                    state.builtInCount + state.customCount,
                    state.query,
                )
            },
            fontSize = 11.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        // ---- Results: empty state or the scrollable list ----
        if (state.results.isEmpty() && state.query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.tasbih_library_empty),
                    fontSize = 14.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.results, key = { it.id }) { tasbih ->
                    TasbihRow(
                        tasbih = tasbih,
                        onClick = { onOpenTasbih(tasbih.id) },
                        onToggleFavorite = {
                            viewModel.onToggleFavorite(tasbih.id, tasbih.isFavorite)
                        },
                    )
                }
                item { Box(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TasbihRow(
    tasbih: TasbihEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val favoriteDescription = stringResource(R.string.tasbih_library_favorite_content_description)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The row's own tap target (opens the Dhikr) is this outer `clickable`.
        // The favorite heart below sits in its own nested `clickable` Box; a
        // click consumed by a nested clickable region does not propagate to an
        // ancestor's clickable in Compose, so tapping the heart toggles the
        // favorite WITHOUT also firing this row's onClick/opening the Dhikr.
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tasbih.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaText(tasbih),
                fontSize = 11.5.sp,
                color = colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = tasbih.transliteration,
                fontSize = 12.sp,
                color = colors.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = tasbih.arabic,
            fontSize = 18.sp,
            color = colors.dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(max = 96.dp)
                .padding(horizontal = 8.dp),
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = favoriteDescription) { onToggleFavorite() }
                .padding(6.dp),
        ) {
            Icon(
                imageVector = if (tasbih.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = favoriteDescription,
                tint = if (tasbih.isFavorite) colors.terra else colors.faint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "33 × 3 laps" when the Tasbih has more than one lap, else "100 per lap". */
@Composable
private fun metaText(tasbih: TasbihEntity): String = if (tasbih.lapCount > 1) {
    stringResource(R.string.tasbih_library_meta_laps, tasbih.lapTarget, tasbih.lapCount)
} else {
    stringResource(R.string.tasbih_library_meta_per_lap, tasbih.lapTarget)
}
