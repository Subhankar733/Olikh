package com.subho.olikh

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.anyOf

@RunWith(AndroidJUnit4::class)
class BrowserChromeUiTest {

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun browserChromeControlsAreVisible() {
        onView(withId(R.id.browserTopBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.addressBar))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnReload))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnTabs))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnMenu))
            .check(matches(isDisplayed()))

        onView(withId(R.id.browserBottomDock))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnBack))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnForward))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnHome))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnBookmark))
            .check(matches(isDisplayed()))
    }

    @Test
    fun browserControlsKeepTheirAccessibilityLabels() {
        onView(withContentDescription("New Tab"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Reload"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Tabs"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Menu"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Back"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Forward"))
            .check(matches(isDisplayed()))

        onView(withContentDescription("Home"))
            .check(matches(isDisplayed()))

        onView(anyOf(
            withContentDescription("Add bookmark"),
            withContentDescription("Remove bookmark")
        )).check(matches(isDisplayed()))
    }
}
