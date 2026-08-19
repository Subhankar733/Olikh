package com.subho.olikh

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun mainBrowserUiLoads() {
        onView(withId(R.id.webView))
            .check { view, noMatchException ->
                if (noMatchException != null) {
                    throw noMatchException
                }
                check(view.isShown) {
                    "WebView exists but is not shown"
                }
            }

        onView(withId(R.id.addressBar))
            .check { view, noMatchException ->
                if (noMatchException != null) {
                    throw noMatchException
                }
                check(view.isShown) {
                    "Address bar exists but is not shown"
                }
            }

        onView(withId(R.id.btnTabs))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnMenu))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btnNewTab))
            .check(matches(isDisplayed()))

        onView(withId(R.id.browserBottomDock))
            .check(matches(isDisplayed()))
    }
}
