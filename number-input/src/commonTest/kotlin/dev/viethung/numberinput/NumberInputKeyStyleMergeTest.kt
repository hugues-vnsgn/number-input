package dev.viethung.numberinput

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two rules that decide what a key actually looks like.
 *
 * They are separate on purpose and easy to conflate. *Role fallback* runs once, at resolution:
 * anything the utility key leaves unset comes from the rest key, so a consumer restyling only the
 * decimal fill does not have to restate its glyph size. *State merge* runs at draw time and
 * contributes colours only — geometry always stays with the role, because an integer-only field
 * disables its decimal key permanently, and a disabled state that carried geometry would leave that
 * one key centred at the digit size while every neighbour is bottom-aligned at 26sp.
 */
class NumberInputKeyStyleMergeTest {

    private val role = NumberInputKeyStyle(
        backgroundColor = Color.White,
        contentColor = Color.Black,
        textSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        contentAlignment = Alignment.BottomCenter,
        contentBottomPadding = 10.dp,
        shadowColor = Color.Gray,
    )

    @Test
    fun role_fallback_fills_only_the_unset_values() {
        val base = NumberInputKeyStyle(
            backgroundColor = Color.White,
            contentColor = Color.Black,
            textSize = 24.sp,
        )
        val utility = NumberInputKeyStyle(backgroundColor = Color.Red)

        val resolved = utility.fallingBackTo(base)

        assertEquals(Color.Red, resolved.backgroundColor)
        assertEquals(Color.Black, resolved.contentColor)
        assertEquals(24.sp, resolved.textSize)
    }

    @Test
    fun state_merge_replaces_colours() {
        val pressed = NumberInputKeyStyle(backgroundColor = Color.Yellow, contentColor = Color.Blue)

        val merged = role.mergedWithState(pressed)

        assertEquals(Color.Yellow, merged.backgroundColor)
        assertEquals(Color.Blue, merged.contentColor)
    }

    @Test
    fun state_merge_keeps_the_roles_geometry() {
        val pressed = NumberInputKeyStyle(backgroundColor = Color.Yellow)

        val merged = role.mergedWithState(pressed)

        assertEquals(26.sp, merged.textSize)
        assertEquals(FontWeight.SemiBold, merged.fontWeight)
        assertEquals(Alignment.BottomCenter, merged.contentAlignment)
        assertEquals(10.dp, merged.contentBottomPadding)
    }

    @Test
    fun an_unset_state_colour_leaves_the_roles_colour_alone() {
        val merged = role.mergedWithState(NumberInputKeyStyle())

        assertEquals(Color.White, merged.backgroundColor)
        assertEquals(Color.Black, merged.contentColor)
    }

    /** Border width follows border colour, so a state without a border cannot erase the role's. */
    @Test
    fun border_width_travels_with_border_colour() {
        val bordered = role.copy(borderColor = Color.Green, borderWidth = 2.dp)

        val withStateBorder = bordered.mergedWithState(
            NumberInputKeyStyle(borderColor = Color.Magenta, borderWidth = 5.dp),
        )
        assertEquals(Color.Magenta, withStateBorder.borderColor)
        assertEquals(5.dp, withStateBorder.borderWidth)

        val withoutStateBorder = bordered.mergedWithState(
            NumberInputKeyStyle(backgroundColor = Color.Yellow),
        )
        assertEquals(Color.Green, withoutStateBorder.borderColor)
        assertEquals(2.dp, withoutStateBorder.borderWidth)
    }

    /** The lip is a resting affordance; a pressed key that still has one does not look pressed. */
    @Test
    fun the_shadow_lip_belongs_to_the_role_and_is_never_merged_from_a_state() {
        val merged = role.mergedWithState(
            NumberInputKeyStyle(backgroundColor = Color.Yellow, shadowColor = Color.Cyan),
        )

        assertEquals(Color.Gray, merged.shadowColor)
    }
}
