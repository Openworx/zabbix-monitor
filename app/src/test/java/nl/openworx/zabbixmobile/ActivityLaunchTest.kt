/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityLaunchTest {

    private fun seedServer() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("zabbix_widget", Context.MODE_PRIVATE).edit()
            .putString("url", "https://zabbix.example.com")
            .putString("token", "dummy")
            .commit()
    }

    @Test
    fun mainActivityStartsWithoutServer() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @Test
    fun mainActivityStartsWithServer() {
        seedServer()
        Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @Test
    fun configActivityStarts() {
        seedServer()
        Robolectric.buildActivity(ConfigActivity::class.java).setup()
    }

    @Test
    fun serverEditActivityStarts() {
        Robolectric.buildActivity(ServerEditActivity::class.java).setup()
    }
}
