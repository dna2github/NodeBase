package seven.drawalive.nodebase

import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout

object UserInterface {

    val buttonLeftStyle = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    )
    val buttonRightStyle = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    )
    val buttonFillStyle = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    )

    fun run(runnable: Runnable) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

    fun themeAppTitleButton(button: Button, running: Boolean) {
        if (running) {
            // light green
            button.setBackgroundColor(-0x300a33)
        } else {
            // light grey
            button.setBackgroundColor(-0x1d1d1e)
        }
    }

    init {
        buttonLeftStyle.setMargins(0, 0, 10, 3)
        buttonRightStyle.setMargins(10, 0, 0, 3)
        buttonFillStyle.setMargins(0, 0, 0, 0)
    }
}
