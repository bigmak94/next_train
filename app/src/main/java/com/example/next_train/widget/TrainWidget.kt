package com.example.next_train.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.next_train.R
import com.example.next_train.api.TrainApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TrainWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val ACTION_UPDATE = "com.example.next_train.UPDATE_WIDGET"

        private fun getBackgroundResource(waitingMinutes: Int): Int {
            return when {
                waitingMinutes >= 8 -> R.drawable.widget_background_green
                waitingMinutes in 3..7 -> R.drawable.widget_background_orange
                else -> R.drawable.widget_background_red
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.train_widget)

            // Créer l'intent pour la mise à jour
            val intent = Intent(context, TrainWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
            views.setTextViewText(R.id.widget_title, "RER A - Joinville-le-Pont")

            // Mettre à jour les données
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = TrainApiService.getNextTrains()
                    result.onSuccess { trains ->
                        if (trains.isNotEmpty()) {
                            val nextTrain = trains[0]
                            views.setTextViewText(R.id.next_train, "Prochain train : ${nextTrain.displayTime}")
                            views.setInt(R.id.widget_layout, "setBackgroundResource", getBackgroundResource(nextTrain.waitingMinutes))

                            if (trains.size > 1) {
                                val followingTrain = trains[1]
                                views.setTextViewText(R.id.following_train, "Train suivant : ${followingTrain.displayTime}")
                            }

                            views.setTextViewText(
                                R.id.last_update,
                                "Mis à jour à ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                            )
                        } else {
                            views.setTextViewText(R.id.next_train, "Aucun train prévu")
                            views.setTextViewText(R.id.following_train, "")
                            views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_background_red)
                        }
                    }.onFailure {
                        views.setTextViewText(R.id.next_train, "Erreur de chargement")
                        views.setTextViewText(R.id.following_train, "Veuillez réessayer")
                        views.setTextViewText(R.id.last_update, it.message ?: "Erreur inconnue")
                        views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_background_red)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.next_train, "Erreur de connexion")
                    views.setTextViewText(R.id.following_train, "Veuillez réessayer")
                    views.setTextViewText(R.id.last_update, e.message ?: "Erreur inconnue")
                    views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_background_red)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                intent.component
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
} 