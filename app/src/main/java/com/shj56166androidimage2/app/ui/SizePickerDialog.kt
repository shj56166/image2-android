package com.shj56166androidimage2.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shj56166androidimage2.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SizePickerDialog(
    currentSize: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val matchedPreset = remember(currentSize) { findPresetForSize(currentSize) }
    val parsedSize = remember(currentSize) { parseSizeLiteral(currentSize) }

    var mode by rememberSaveable(currentSize) {
        mutableStateOf(
            when {
                currentSize == "auto" -> SizePickerMode.AUTO
                matchedPreset != null -> SizePickerMode.RATIO
                else -> SizePickerMode.CUSTOM
            },
        )
    }
    var selectedTier by rememberSaveable(currentSize) {
        mutableStateOf(matchedPreset?.tier ?: SizeTier.ONE_K)
    }
    var selectedRatio by rememberSaveable(currentSize) {
        mutableStateOf(matchedPreset?.ratio ?: sizeRatioOptions.first().value)
    }
    var customWidth by rememberSaveable(currentSize) {
        mutableStateOf(parsedSize?.first ?: "1024")
    }
    var customHeight by rememberSaveable(currentSize) {
        mutableStateOf(parsedSize?.second ?: "1024")
    }

    val customValidation = remember(customWidth, customHeight) {
        validateCustomSize(customWidth, customHeight)
    }
    val previewSize =
        when (mode) {
            SizePickerMode.AUTO -> "auto"
            SizePickerMode.RATIO -> calculatePresetSize(selectedTier, selectedRatio).orEmpty()
            SizePickerMode.CUSTOM -> customValidation.size.orEmpty()
        }
    val customErrorText =
        when (customValidation.error) {
            CustomSizeError.POSITIVE_INTEGER_REQUIRED -> stringResource(R.string.size_invalid_positive)
            CustomSizeError.MULTIPLE_OF_SIXTEEN_REQUIRED -> stringResource(R.string.size_invalid_multiple_of_16)
            null -> null
        }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("size_picker_dialog"),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.size_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.current_size_value, currentSize),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SizePickerMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            modifier =
                                when (option) {
                                    SizePickerMode.AUTO -> Modifier.testTag("size_mode_auto")
                                    SizePickerMode.RATIO -> Modifier.testTag("size_mode_ratio")
                                    SizePickerMode.CUSTOM -> Modifier.testTag("size_mode_custom")
                                },
                            selected = mode == option,
                            onClick = { mode = option },
                            shape =
                                androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SizePickerMode.entries.size,
                                ),
                        ) {
                            Text(
                                text =
                                    when (option) {
                                        SizePickerMode.AUTO -> stringResource(R.string.size_mode_auto)
                                        SizePickerMode.RATIO -> stringResource(R.string.size_mode_ratio)
                                        SizePickerMode.CUSTOM -> stringResource(R.string.size_mode_custom)
                                    },
                            )
                        }
                    }
                }

                when (mode) {
                    SizePickerMode.AUTO -> {
                        Text(
                            text = stringResource(R.string.size_auto_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SizePickerMode.RATIO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.size_base_resolution),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SizeTier.entries.forEachIndexed { index, tier ->
                                        SegmentedButton(
                                            selected = selectedTier == tier,
                                            onClick = { selectedTier = tier },
                                            shape =
                                                androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                                    index = index,
                                                    count = SizeTier.entries.size,
                                                ),
                                        ) {
                                            Text(tier.label)
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.size_ratio_label),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    sizeRatioOptions.forEach { ratio ->
                                        FilterChip(
                                            selected = selectedRatio == ratio.value,
                                            onClick = { selectedRatio = ratio.value },
                                            label = { Text(ratio.label) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SizePickerMode.CUSTOM -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
                                    value = customWidth,
                                    onValueChange = { customWidth = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f).testTag("size_custom_width"),
                                    label = { Text(stringResource(R.string.size_custom_width)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                OutlinedTextField(
                                    value = customHeight,
                                    onValueChange = { customHeight = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f).testTag("size_custom_height"),
                                    label = { Text(stringResource(R.string.size_custom_height)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }

                            AnimatedVisibility(visible = customErrorText != null) {
                                Text(
                                    text = customErrorText.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.size_preview_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = previewSize,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_action))
                    }
                    TextButton(
                        onClick = { onConfirm(previewSize) },
                        enabled = previewSize.isNotBlank() && (mode != SizePickerMode.CUSTOM || customValidation.error == null),
                        modifier = Modifier.testTag("size_picker_confirm"),
                    ) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                }
            }
        }
    }
}
