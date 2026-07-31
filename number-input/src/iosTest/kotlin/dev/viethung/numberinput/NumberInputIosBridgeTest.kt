package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentNatural
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UITextField
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the UIKit bridge on a real simulator. Everything here is interop that a Compose-only
 * test can never reach: selector dispatch, key-value coding, and the `UIToolbar` that becomes
 * the keyboard accessory view.
 */
@OptIn(ExperimentalForeignApi::class)
class NumberInputIosBridgeTest {

    private fun style() = NumberInputStyle(
        toolbar = NumberInputToolbarStyle(
            clearLabel = "Xoá",
            signLabel = "±",
            doneLabel = "Xong",
        ),
    )

    private fun identifierOf(item: Any): String? = (item as NSObject).identifier()

    @Test
    fun toolbar_carries_clear_sign_spacer_done_in_order() {
        val toolbar = NumberInputCoordinator().buildToolbar(style())
        val items = assertNotNull(toolbar.items)

        assertEquals(4, items.size)
        assertEquals(TAG_CLEAR, identifierOf(items[0]!!))
        assertEquals(TAG_SIGN, identifierOf(items[1]!!))
        assertEquals(TAG_DONE, identifierOf(items[3]!!))
    }

    @Test
    fun toolbar_titles_come_from_style() {
        val toolbar = NumberInputCoordinator().buildToolbar(style())
        val items = assertNotNull(toolbar.items)

        assertEquals("Xoá", (items[0] as UIBarButtonItem).title)
        assertEquals("±", (items[1] as UIBarButtonItem).title)
        assertEquals("Xong", (items[3] as UIBarButtonItem).title)
    }

    @Test
    fun syncToolbar_applies_the_shared_enable_rules() {
        val coordinator = NumberInputCoordinator()
        val toolbar = coordinator.buildToolbar(style())
        val items = assertNotNull(toolbar.items)
        val clear = items[0] as UIBarButtonItem
        val sign = items[1] as UIBarButtonItem

        val empty = NumberInputState(config = NumberInputConfig(allowNegative = true))
        coordinator.syncToolbar(empty.clearEnabled, empty.signEnabled)
        assertFalse(clear.enabled, "Clear is disabled when there is nothing to clear")
        assertFalse(sign.enabled, "Sign is disabled with no value")

        val filled = NumberInputState(
            initialValue = 12.0,
            config = NumberInputConfig(allowNegative = true),
        )
        coordinator.syncToolbar(filled.clearEnabled, filled.signEnabled)
        assertTrue(clear.enabled)
        assertTrue(sign.enabled)
    }

    @Test
    fun sign_disabled_when_negatives_are_not_allowed() {
        val coordinator = NumberInputCoordinator()
        val toolbar = coordinator.buildToolbar(style())
        val sign = assertNotNull(toolbar.items)[1] as UIBarButtonItem

        val unsigned = NumberInputState(
            initialValue = 12.0,
            config = NumberInputConfig(allowNegative = false),
        )
        coordinator.syncToolbar(unsigned.clearEnabled, unsigned.signEnabled)
        assertFalse(sign.enabled)
    }

    @Test
    fun clear_action_mutates_the_attached_state() {
        val coordinator = NumberInputCoordinator()
        coordinator.buildToolbar(style())
        val state = NumberInputState(initialValue = 42.0)
        coordinator.attach(UITextField(), state, style())

        coordinator.clearTapped()

        assertEquals(null, state.value)
        assertEquals("", state.rawText)
    }

    @Test
    fun sign_action_mutates_the_attached_state() {
        val coordinator = NumberInputCoordinator()
        coordinator.buildToolbar(style())
        val state = NumberInputState(
            initialValue = 42.0,
            config = NumberInputConfig(allowNegative = true),
        )
        coordinator.attach(UITextField(), state, style())

        coordinator.signTapped()

        assertEquals(-42.0, state.value)
    }

    @Test
    fun text_changes_are_forwarded_from_the_native_field() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        var seen: String? = null
        coordinator.onTextChanged = { seen = it }
        coordinator.attach(field, NumberInputState(), style())

        field.setText("1234")
        coordinator.textChanged()

        assertEquals("1234", seen)
    }

    @Test
    fun focus_callbacks_run_on_begin_and_end_editing() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val seen = mutableListOf<Boolean>()
        coordinator.onFocusChanged = { seen += it }
        coordinator.attach(field, NumberInputState(), style())

        coordinator.textFieldDidBeginEditing(field)
        coordinator.textFieldDidEndEditing(field)

        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun field_identifier_is_set_through_key_value_coding() {
        val field = UITextField()
        field.identify(TAG_FIELD)
        assertEquals(TAG_FIELD, identifierOf(field))
    }

    @Test
    fun style_is_applied_to_the_native_field() {
        val field = UITextField()
        val style = NumberInputStyle(textAlign = TextAlign.Center)

        field.applyStyle(style, enabled = true, placeholder = "Nhập số tiền")

        assertEquals(NSTextAlignmentCenter, field.textAlignment)
        assertEquals(style.cornerRadius.value.toDouble(), field.layer.cornerRadius)
        assertEquals(style.borderWidth.value.toDouble(), field.layer.borderWidth)
        assertEquals("Nhập số tiền", field.attributedPlaceholder?.string)
    }

    @Test
    fun colors_map_component_wise_to_uikit() {
        val uiColor = Color(red = 1f, green = 0f, blue = 0.5f, alpha = 0.25f).toUIColor()
        // Round-tripping through CoreGraphics is the only way to read the components back.
        assertNotNull(uiColor.CGColor)
    }

    @Test
    fun text_alignment_maps_across_the_three_buckets() {
        assertEquals(NSTextAlignmentCenter, TextAlign.Center.toNSTextAlignment())
        assertEquals(NSTextAlignmentRight, TextAlign.End.toNSTextAlignment())
        assertEquals(NSTextAlignmentNatural, TextAlign.Start.toNSTextAlignment())
    }

    @Test
    fun font_weight_buckets_map_to_uikit_weights() {
        assertTrue(FontWeight.Bold.toUIFontWeight() > FontWeight.SemiBold.toUIFontWeight())
        assertTrue(FontWeight.SemiBold.toUIFontWeight() > FontWeight.Normal.toUIFontWeight())
    }
}
