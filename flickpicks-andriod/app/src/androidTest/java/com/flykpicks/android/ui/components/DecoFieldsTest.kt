package com.flykpicks.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import com.flykpicks.android.ui.theme.FlykPicksTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DecoFieldsTest {

    @get:Rule
    val compose = createComposeRule()

    private var email by mutableStateOf("")
    private var password by mutableStateOf("")
    private lateinit var inputModeManager: InputModeManager

    @Before
    fun setUp() {
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            FlykPicksTheme {
                Column {
                    DecoTextField(email, { email = it }, "Email", Modifier.testTag("email"), imeAction = ImeAction.Next)
                    DecoSecureField(password, { password = it }, "Password", Modifier.testTag("password"), imeAction = ImeAction.Done)
                }
            }
        }
    }

    /** The editable node inside the field tagged [tag]. */
    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun imeNext_movesToTheNextField() {
        field("email").performTextInput("ada@example.com")

        field("email").performImeAction()

        field("password").assertIsFocused()
    }

    // Regression: with a hardware keyboard (keyboard input mode, where clickables can take focus)
    // the clear (×) button sat between the fields, so Enter moved focus onto it and the next Enter
    // cleared the email instead of reaching the password.
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun hardwareEnter_skipsTheClearButton_andMovesToTheNextField() {
        field("email").performTextInput("ada@example.com")
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }

        field("email").performKeyInput { pressKey(Key.Enter) }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }

        field("password").assertIsFocused()
        assertEquals("ada@example.com", email)
    }

    @Test
    fun clearButton_stillWorksByTouch() {
        field("email").performTextInput("ada@example.com")

        compose.onAllNodesWithContentDescription("Clear text")[0].performClick()

        compose.runOnIdle { assertEquals("", email) }
    }
}
