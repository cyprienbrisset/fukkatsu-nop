package com.cyprienbrisset.fukkatsunop.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context

object AppWidgetHostHelper {
    private const val HOST_ID = 1024
    private var host: AppWidgetHost? = null

    fun get(context: Context): AppWidgetHost {
        if (host == null) host = AppWidgetHost(context.applicationContext, HOST_ID)
        return host!!
    }

    fun startListening(context: Context) = get(context).startListening()
    fun stopListening(context: Context) = get(context).stopListening()
    fun allocateId(context: Context): Int = get(context).allocateAppWidgetId()
    fun deleteId(context: Context, id: Int) = get(context).deleteAppWidgetId(id)

    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
        return get(context).createView(context, appWidgetId, info)
    }
}
