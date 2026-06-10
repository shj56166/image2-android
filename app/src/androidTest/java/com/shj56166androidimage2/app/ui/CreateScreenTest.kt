package com.shj56166androidimage2.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shj56166androidimage2.app.data.model.ImageSource
import com.shj56166androidimage2.app.data.model.StoredImageAsset
import com.shj56166androidimage2.app.data.model.TaskParams
import com.shj56166androidimage2.app.ui.theme.ImagePlaygroundTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsNewPromptCopyAndCollapsedParameterSummaryByDefault() {
        composeRule.setCreateScreenContent(buildState())

        composeRule.onNodeWithTag("create_prompt_label").assertExists()
        composeRule.onNodeWithText("描述天马行空的想象").assertExists()
        composeRule.onNodeWithTag("create_parameter_summary").assertExists()
        composeRule.onNodeWithText("尺寸 auto", substring = true).assertExists()
    }

    @Test
    fun canOpenParameterBottomSheetAndOpenSizeDialog() {
        composeRule.setCreateScreenContent(buildState())

        composeRule.onNodeWithTag("create_parameter_section").performClick()
        composeRule.onNodeWithTag("create_parameter_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("create_parameter_form").assertExists()
        composeRule.onNodeWithTag("count_value").assertIsDisplayed()
        composeRule.onNodeWithTag("create_size_selector").performClick()
        composeRule.onNodeWithTag("size_picker_dialog").assertIsDisplayed()
    }

    @Test
    fun countStepperIncrementsAndDecrementsWithinBounds() {
        composeRule.setCreateScreenContent(buildState())

        composeRule.onNodeWithTag("create_parameter_section").performClick()
        composeRule.onNodeWithTag("count_value").assertTextEquals("1")
        composeRule.onNodeWithTag("count_decrement").assertIsNotEnabled()
        composeRule.onNodeWithTag("count_increment").performClick()
        composeRule.onNodeWithTag("count_value").assertTextEquals("2")
        composeRule.onNodeWithTag("count_decrement").assertIsEnabled()
    }

    @Test
    fun customSizeValidationBlocksInvalidValuesAndAcceptsValidValues() {
        composeRule.setCreateScreenContent(buildState())

        composeRule.onNodeWithTag("create_parameter_section").performClick()
        composeRule.onNodeWithTag("create_size_selector").performClick()
        composeRule.onNodeWithTag("size_mode_custom").performClick()
        composeRule.onNodeWithTag("size_custom_width").performTextReplacement("1025")
        composeRule.onNodeWithTag("size_custom_height").performTextReplacement("1024")
        composeRule.onNodeWithText("宽和高都必须是 16 的倍数。").assertExists()
        composeRule.onNodeWithTag("size_picker_confirm").assertIsNotEnabled()

        composeRule.onNodeWithTag("size_custom_width").performTextReplacement("1024")
        composeRule.onNodeWithTag("size_custom_height").performTextReplacement("1024")
        composeRule.onNodeWithTag("size_picker_confirm").assertIsEnabled()
        composeRule.onNodeWithTag("size_picker_confirm").performClick()

        composeRule.onNodeWithTag("create_size_selector").assertExists()
        composeRule.onNodeWithText("1024x1024").assertExists()
    }

    @Test
    fun referenceSectionStaysBelowPromptInput() {
        composeRule.setCreateScreenContent(buildState(withReferenceImage = true))

        val promptTop = composeRule.onNodeWithTag("create_prompt_input").fetchSemanticsNode().boundsInRoot.top
        val referenceTop = composeRule.onNodeWithTag("create_reference_section").fetchSemanticsNode().boundsInRoot.top

        assertTrue(referenceTop > promptTop)
    }

    @Test
    fun clickingReferenceThumbnailInsertsReferenceLabelIntoPrompt() {
        composeRule.setCreateScreenContent(buildState(withReferenceImage = true))

        composeRule.onNodeWithTag("reference_insert_image-1").performClick()
        composeRule.onNodeWithTag("create_prompt_input").assertTextContains("\u56FE1")
    }

    @Test
    fun referenceLabelsReorderAfterRemoval() {
        composeRule.setCreateScreenContent(buildState(imageIds = listOf("image-1", "image-2")))

        composeRule.onNodeWithTag("reference_remove_image-1").performClick()
        composeRule.onNodeWithTag("reference_insert_image-2").performClick()
        composeRule.onNodeWithTag("create_prompt_input").assertTextContains("\u56FE1")
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setCreateScreenContent(
    initialState: MainUiState,
) {
    setContent {
        var state by mutableStateOf(initialState)
        ImagePlaygroundTheme {
            CreateScreen(
                state = state,
                padding = PaddingValues(0.dp),
                onPromptChange = {
                    state = state.copy(composer = state.composer.copy(prompt = it))
                },
                onPickImages = {},
                onRemoveImage = { imageId ->
                    state =
                        state.copy(
                            composer =
                                state.composer.copy(
                                    selectedImageIds = state.composer.selectedImageIds.filterNot { it == imageId },
                                ),
                        )
                },
                onOpenMaskEditor = {},
                onOpenTask = {},
                onSubmit = {},
                onParamsChange = { params ->
                    state = state.copy(composer = state.composer.copy(params = params))
                },
            )
        }
    }
}

private fun buildState(
    withReferenceImage: Boolean = false,
    imageIds: List<String> = if (withReferenceImage) listOf("image-1") else emptyList(),
): MainUiState {
    val selectedIds = imageIds
    val images =
        if (selectedIds.isNotEmpty()) {
            selectedIds.associateWith { imageId ->
                    StoredImageAsset(
                        id = imageId,
                        filePath = "file:///tmp/reference.png",
                        thumbnailPath = null,
                        mimeType = "image/png",
                        source = ImageSource.UPLOAD,
                        width = 512,
                        height = 512,
                        createdAt = 1L,
                        sha256 = "abc",
                    )
            }
        } else {
            emptyMap()
        }
    return MainUiState(
        loading = false,
        composer = ComposerState(prompt = "", params = TaskParams(), selectedImageIds = selectedIds),
        images = images,
    )
}
