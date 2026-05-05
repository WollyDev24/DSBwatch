package dev.wolly.dsbwatch.tile

import android.content.Context
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography.BODY_LARGE
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import java.time.LocalDate
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

            // Count substitutions for today
            val count = entries.size 

            tile(requestParams, this@MainTileService, count)
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
    count: Int
): TileBuilders.Tile {
    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        mainSlot = {
                            text(
                                (if (count > 0) "$count Substitutions" else "No Substitutions").layoutString,
                                typography = TITLE_MEDIUM
                            )
                        },
                        bottomSlot = {
                            text(
                                "DSBwatch".layoutString,
                                typography = BODY_LARGE
                            )
                        }
                    )
                }
            )
        )
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    tile(it, context, 3)
}
