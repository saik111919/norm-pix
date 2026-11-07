package com.example.normpix.widgets

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.normpix.R
import com.example.normpix.finance.domain.TransactionType

/*
 * Adding a transaction without opening the app first (2026-11-07, suggested after the owner asked what to build next): press and
 * hold the app's icon for Add expense, Add income and Transfer, or add the "Add expense" tile to the Quick Settings. Both open the
 * app the way the Add widget's buttons do - an intent carrying a plain word (`WidgetRequest.Add`) that the app acts on once it is
 * unlocked (`ReceiveWidgetRequests`) - so a locked app stays locked and no ledger data is on the intent or the shortcut.
 */

/** The shortcuts on the app's icon. */
object QuickAddShortcuts {
    private class Entry(val type: TransactionType, val label: String, val icon: Int)

    private val entries = listOf(
        Entry(TransactionType.Expense, "Add expense", R.drawable.shortcut_ic_expense),
        Entry(TransactionType.Income, "Add income", R.drawable.shortcut_ic_income),
        Entry(TransactionType.Transfer, "Transfer", R.drawable.shortcut_ic_transfer),
    )

    /** The shortcut ids, one for each kind of transaction: "add-expense", "add-income", "add-transfer". */
    val ids: List<String> get() = entries.map { WidgetRequest.Add(it.type).key }

    /** Puts the three shortcuts on the icon (replacing what was there); a launcher that has none, or a failure, just leaves them out. */
    fun publish(context: Context) {
        runCatching {
            val shortcuts = entries.mapIndexed { rank, entry ->
                val request = WidgetRequest.Add(entry.type)
                ShortcutInfoCompat.Builder(context, request.key)
                    .setShortLabel(entry.label)
                    .setLongLabel(entry.label)
                    .setIcon(IconCompat.createWithResource(context, entry.icon))
                    .setIntent(WidgetIntents.intent(context, request))
                    .setRank(rank)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}

/**
 * The "Add expense" Quick Settings tile: the user adds it from the tile editor; a tap closes the panel and opens the new-expense sheet
 * (after the lock screen and the app's own lock, if either is up).
 */
class QuickAddTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Add expense"
            icon = Icon.createWithResource(this@QuickAddTileService, R.drawable.widget_ic_add)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = WidgetIntents.intent(this, WidgetRequest.Add(TransactionType.Expense))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Before Android 14 a tile can only launch with an Intent; from 14 on that form is deprecated and a PendingIntent is the way.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        } else {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }
}
