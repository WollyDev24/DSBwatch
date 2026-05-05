package dev.wolly.dsbwatch.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.LABEL_SMALL
import androidx.wear.protolayout.material3.Typography.TITLE_MEDIUM
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import dev.wolly.dsbwatch.R
import com.google.common.util.concurrent.ListenableFuture
import dev.wolly.dsbwatch.data.DataStoreManager
import dev.wolly.dsbwatch.data.SubstitutionEntry
import dev.wolly.dsbwatch.presentation.MainActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RESOURCES_VERSION = "0"

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val dataStore = DataStoreManager(this@MainTileService)
            val json = dataStore.archiveFlow.first()
            val entries = if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<SubstitutionEntry>>() {}.type
                Gson().fromJson<List<SubstitutionEntry>>(json, type)
            } else emptyList()

            // Sort entries and pick top ones
            val sortedEntries = entries.take(3)

            tile(requestParams, this@MainTileService, sortedEntries)
        }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(resources(requestParams))
}

private fun resources(requestParams: ResourcesRequest): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    entries: List<SubstitutionEntry>
): TileBuilders.Tile {
    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    val clickAction = ModifiersBuilders.Clickable.Builder()
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setPackageName(context.packageName)
                                        .setClassName(MainActivity::class.java.name)
                                        .build()
                                )
                                .build()
                        )
                        .setId("launch_app")
                        .build()

                    val layout = primaryLayout(
                        mainSlot = {
                            LayoutElementBuilders.Column.Builder()
                                .apply {
                                    if (entries.isEmpty()) {
                                        addContent(
                                            text(
                                                context.getString(R.string.msg_no_substitutions).layoutString,
                                                typography = TITLE_MEDIUM
                                            )
                                        )
                                    } else {
                                        entries.forEach { entry ->
                                            addContent(
                                                LayoutElementBuilders.Column.Builder()
                                                    .addContent(
                                                        text(
                                                            "${entry.lesson}: ${entry.subject}".layoutString,
                                                            typography = BODY_MEDIUM,
                                                            color = LayoutColor(0xFF3DDC84.toInt())
                                                        )
                                                    )
                                                    .addContent(
                                                        text(
                                                            entry.art.layoutString,
                                                            typography = LABEL_SMALL
                                                        )
                                                    )
                                                    .build()
                                            )
                                            addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
                                        }
                                    }
                                }
                                .build()
                        },
                        bottomSlot = {
                            text(
                                (if (entries.isNotEmpty()) context.getString(R.string.action_view_substitutions) else context.getString(R.string.action_refresh)).layoutString,
                                typography = LABEL_SMALL
                            )
                        }
                    )

                    LayoutElementBuilders.Box.Builder()
                        .addContent(layout)
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(clickAction)
                                .build()
                        )
                        .build()
                }
            )
        )
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    tile(it, context, listOf(
        SubstitutionEntry("Mon", "Vertretung", "10a", "1-2", "Math", "R101", "", "", "", ""),
        SubstitutionEntry("Mon", "Entfall", "10a", "3", "Physics", "R102", "", "", "", "")
    ))
}
