package com.flykpicks.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flykpicks.android.ui.theme.DS
import com.flykpicks.android.ui.theme.FlykColors
import com.flykpicks.android.ui.theme.FlykPicksTheme
import com.flykpicks.android.ui.theme.FlykType
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.ceil

// Pieces the sign-in screens share (iOS defines them inside LoginView / RegistrationView /
// ForgotPasswordVerifyOTPView).

private val BannerShape = RoundedCornerShape(14.dp)

/** Neutral confirmation in jade, shaped like [ErrorBanner] (iOS `InfoBanner`). */
@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(FlykColors.Jade.copy(alpha = 0.12f))
            .border(1.dp, FlykColors.Jade.copy(alpha = 0.35f), BannerShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = FlykColors.Jade, modifier = Modifier.size(18.dp))
        Text(message, style = FlykType.Footnote, color = FlykColors.Jade)
    }
}

/** Small ember message under a field (iOS `fieldError` / the OTP inline error). */
@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = FlykColors.Ember, modifier = Modifier.size(12.dp))
        Text(message, style = FlykType.Footnote, color = FlykColors.Ember)
    }
}

/** "—— OR ——" between the primary action and the alternative. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BrassRule(Modifier.weight(1f))
        Text("OR", style = FlykType.Caption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp), color = FlykColors.Brass)
        BrassRule(Modifier.weight(1f))
    }
}

/** A plain-text button: brass footnote by default, as the iOS auth links are. */
@Composable
fun DecoTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FlykColors.Brass,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        style = FlykType.Footnote.copy(fontWeight = fontWeight),
        color = color,
        textAlign = TextAlign.Center,
    )
}

/**
 * Whole seconds left until [deadline], rounded up and ticking once a second; 0 once it has
 * passed or when there is none. Drives the "Try again in Ns" countdowns.
 */
@Composable
fun rememberSecondsUntil(deadline: Instant?): Int {
    val seconds by produceState(initialValue = secondsUntil(deadline), deadline) {
        while (true) {
            value = secondsUntil(deadline)
            if (value == 0) break
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return seconds
}

private fun secondsUntil(deadline: Instant?): Int {
    if (deadline == null) return 0
    val millis = deadline.toEpochMilli() - System.currentTimeMillis()
    return if (millis <= 0) 0 else ceil(millis / 1_000.0).toInt()
}

/**
 * Six digit boxes over a hidden numeric field (iOS `OTPCodeField`): non-digits are dropped and
 * input stops at [length]. The box where the next digit goes is outlined in brass. Accepts
 * SMS one-time-code autofill.
 */
@Composable
fun OTPCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(length)) },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = "Verification code, $length digits"
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(length) { index ->
                        val isCursor = focused && index == code.length
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .size(width = 46.dp, height = 58.dp)
                                .background(FlykColors.Surface, shape)
                                .border(
                                    width = if (isCursor) 2.dp else 1.dp,
                                    color = if (isCursor) FlykColors.Brass else FlykColors.Ink.copy(alpha = 0.35f),
                                    shape = shape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = code.getOrNull(index)?.toString().orEmpty(),
                                style = FlykType.Title3.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = FlykColors.Ink,
                            )
                        }
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F0)
@Composable
private fun AuthPartsPreview() {
    FlykPicksTheme {
        Column(Modifier.padding(DS.SpacingXL), verticalArrangement = Arrangement.spacedBy(DS.SpacingL)) {
            InfoBanner("Verification email sent.")
            InlineError("Code is incorrect.")
            OrDivider()
            DecoTextLink("Create account", onClick = {}, fontWeight = FontWeight.SemiBold)
            OTPCodeField("123", onCodeChange = {})
            Box(Modifier.height(1.dp))
        }
    }
}
