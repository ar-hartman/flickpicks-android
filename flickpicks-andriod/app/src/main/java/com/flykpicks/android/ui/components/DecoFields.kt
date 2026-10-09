package com.flykpicks.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType

// iOS DecoTextField / DecoSecureField / DecoSearchField (blueprint §11.1–11.3).

/**
 * Single-line text field with an optional leading icon and a clear button.
 *
 * [onImeAction] runs when the keyboard action key is pressed, before the default behavior
 * (Next moves focus, Done hides the keyboard).
 */
@Composable
fun DecoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    contentType: ContentType? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    /** Above 1, the field grows to fit wrapped text and has no clear button. */
    minLines: Int = 1,
) {
    DecoField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        enabled = enabled,
        focusRequester = focusRequester,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        onImeAction = onImeAction,
        contentType = contentType,
        leading = leadingIcon?.let { { FieldIcon(it, FlykColors.Ink.copy(alpha = 0.55f)) } },
        trailing = {
            if (value.isNotEmpty() && minLines == 1) ClearButton(label = "Clear text") { onValueChange("") }
        },
    )
}

/** Password field with a show/hide toggle. Defaults to the password autofill type. */
@Composable
fun DecoSecureField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = Icons.Outlined.Lock,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    contentType: ContentType? = ContentType.Password,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    DecoField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        enabled = enabled,
        focusRequester = focusRequester,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        onImeAction = onImeAction,
        contentType = contentType,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        leading = leadingIcon?.let { { FieldIcon(it, FlykColors.Ink.copy(alpha = 0.55f)) } },
        trailing = {
            FieldIconButton(
                icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                label = if (revealed) "Hide password" else "Show password",
                tint = FlykColors.Ink.copy(alpha = 0.45f),
            ) { revealed = !revealed }
        },
    )
}

/** Search input: magnifying glass, clear button, and the Search key calls [onSearch]. */
@Composable
fun DecoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DecoField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        enabled = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        onImeAction = onSearch,
        contentType = null,
        leading = { FieldIcon(Icons.Outlined.Search, FlykColors.Ink) },
        trailing = {
            if (value.isNotEmpty()) ClearButton(label = "Clear search") { onValueChange("") }
        },
    )
}

private val FieldShape = RoundedCornerShape(14.dp)
private val FieldIconSize = 20.dp

@Composable
private fun DecoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    onImeAction: (() -> Unit)?,
    contentType: ContentType?,
    leading: (@Composable () -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    minLines: Int = 1,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) FlykColors.Brass.copy(alpha = 0.7f) else FlykColors.Ink.copy(alpha = 0.2f),
        animationSpec = tween(durationMillis = 150),
        label = "fieldBorder",
    )
    Row(
        modifier = modifier
            .dropShadow(FieldShape, Shadow(radius = 10.dp, color = FlykColors.Ink.copy(alpha = 0.06f), offset = DpOffset(0.dp, 8.dp)))
            .background(FlykColors.Surface, FieldShape)
            .border(1.dp, borderColor, FieldShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
    ) {
        leading?.invoke()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .then(if (contentType != null) Modifier.semantics { this.contentType = contentType } else Modifier),
            enabled = enabled,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = FlykType.Body.copy(color = FlykColors.Ink),
            cursorBrush = SolidColor(FlykColors.Brass),
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(
                onAny = {
                    onImeAction?.invoke()
                    defaultKeyboardAction(keyboardOptions.imeAction)
                },
            ),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(contentAlignment = if (minLines > 1) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        // iOS `.placeholderText` is a translucent gray; ink at 30% reads the same on cream.
                        Text(
                            placeholder,
                            style = FlykType.Body,
                            color = FlykColors.Ink.copy(alpha = 0.3f),
                            maxLines = if (minLines > 1) Int.MAX_VALUE else 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        trailing()
    }
}

@Composable
private fun FieldIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(FieldIconSize))
}

@Composable
private fun FieldIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .size(FieldIconSize + 4.dp)
            .clip(CircleShape)
            // Out of the keyboard focus order, so the IME's Next goes to the next field rather
            // than this button. Touch and TalkBack still reach it.
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(2.dp),
    )
}

@Composable
private fun ClearButton(label: String, onClick: () -> Unit) {
    FieldIconButton(Icons.Filled.Cancel, label, FlykColors.Ink.copy(alpha = 0.25f), onClick)
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun DecoFieldsPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(DS.SpacingXL), verticalArrangement = Arrangement.spacedBy(DS.SpacingL)) {
            DecoTextField("", {}, "Email", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Email)
            DecoTextField("reader@flykpicks.com", {}, "Email", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Email)
            DecoSecureField("hunter22", {}, "Password", Modifier.fillMaxWidth())
            DecoSearchField("", {}, "Search films & TV", onSearch = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
