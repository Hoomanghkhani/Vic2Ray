package com.vic2ray.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Opaque shared colors keep text contrast predictable on cards, menus, and dialogs.
internal val Navy = Color(0xFF0B1220)
internal val Panel = Color(0xFF142033)
internal val PanelLight = Color(0xFF1D2D44)
internal val MainText = Color(0xFFF3F7FC)
internal val Muted = Color(0xFFB7C6DB)
internal val DisabledText = Color(0xFF9DAFC7)
internal val Outline = Color(0xFF7D91AD)
internal val OutlineVariant = Color(0xFF41536E)
internal val Accent = Color(0xFF67DCF1)
internal val OnAccent = Color(0xFF052D38)
internal val AccentPanel = Color(0xFF123D4B)
internal val Mint = Color(0xFF7BE0BB)
internal val ConnectedPanel = Color(0xFF174A3C)
internal val Warning = Color(0xFFF4D08B)

internal val VictoryColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentPanel,
    onPrimaryContainer = Color(0xFFB3F0FA),
    inversePrimary = Color(0xFF096577),
    secondary = Mint,
    onSecondary = Color(0xFF073A2A),
    secondaryContainer = ConnectedPanel,
    onSecondaryContainer = Color(0xFFC1F5DF),
    tertiary = Warning,
    onTertiary = Color(0xFF3D2D08),
    tertiaryContainer = Color(0xFF50401B),
    onTertiaryContainer = Color(0xFFFAE8C0),
    background = Navy,
    onBackground = MainText,
    surface = Panel,
    onSurface = MainText,
    surfaceVariant = PanelLight,
    onSurfaceVariant = Muted,
    surfaceTint = Accent,
    inverseSurface = Color(0xFFE1EAF5),
    inverseOnSurface = Color(0xFF19283C),
    error = Color(0xFFFFB4B0),
    onError = Color(0xFF5F151A),
    errorContainer = Color(0xFF4C252A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Color.Black
)

@Composable
fun VictoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VictoryColors) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Accent,
                backgroundColor = Accent.copy(alpha = 0.3f)
            )
        ) {
            // MaterialTheme does not set LocalContentColor. A root Surface ensures
            // plain Text/Icon nodes inherit light content instead of default black.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Navy,
                contentColor = MainText,
                content = content
            )
        }
    }
}

@Composable
internal fun victoryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Accent,
    contentColor = OnAccent,
    disabledContainerColor = PanelLight,
    disabledContentColor = DisabledText
)

@Composable
internal fun victoryOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = Accent,
    disabledContentColor = DisabledText
)

@Composable
internal fun victoryTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = Accent,
    disabledContentColor = DisabledText
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun victoryTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = MainText,
    disabledTextColor = DisabledText,
    containerColor = PanelLight,
    cursorColor = Accent,
    errorCursorColor = MaterialTheme.colorScheme.error,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Outline,
    disabledBorderColor = OutlineVariant,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLeadingIconColor = Accent,
    unfocusedLeadingIconColor = Muted,
    disabledLeadingIconColor = DisabledText,
    focusedTrailingIconColor = Accent,
    unfocusedTrailingIconColor = Muted,
    disabledTrailingIconColor = DisabledText,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    disabledLabelColor = DisabledText,
    errorLabelColor = MaterialTheme.colorScheme.error,
    placeholderColor = Muted,
    disabledPlaceholderColor = DisabledText,
    focusedSupportingTextColor = Muted,
    unfocusedSupportingTextColor = Muted,
    disabledSupportingTextColor = DisabledText,
    errorSupportingTextColor = MaterialTheme.colorScheme.error
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun victoryFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Panel,
    labelColor = Muted,
    iconColor = Muted,
    disabledContainerColor = Panel,
    disabledLabelColor = DisabledText,
    disabledLeadingIconColor = DisabledText,
    disabledTrailingIconColor = DisabledText,
    selectedContainerColor = AccentPanel,
    disabledSelectedContainerColor = PanelLight,
    selectedLabelColor = MainText,
    selectedLeadingIconColor = Accent,
    selectedTrailingIconColor = Accent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun victoryFilterChipBorder() = FilterChipDefaults.filterChipBorder(
    borderColor = Outline,
    selectedBorderColor = Accent,
    disabledBorderColor = OutlineVariant,
    disabledSelectedBorderColor = OutlineVariant,
    selectedBorderWidth = 1.dp
)
