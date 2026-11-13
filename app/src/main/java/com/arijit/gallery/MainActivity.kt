package com.arijit.gallery

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arijit.gallery.core.designsystem.GalleryTheme
import com.arijit.gallery.core.domain.model.MediaItem
import com.arijit.gallery.core.domain.model.UserSettings
import com.arijit.gallery.core.domain.repository.SettingsRepository
import com.arijit.gallery.core.media.MediaStoreDataSource
import com.arijit.gallery.core.navigation.GalleryRoot
import com.arijit.gallery.core.permissions.MediaAccessLevel
import com.arijit.gallery.core.permissions.MediaPermissionHelper
import com.arijit.gallery.core.permissions.PermissionRationaleScreen
import com.arijit.gallery.photos.PhotosViewModel
import com.arijit.gallery.viewer.ExternalMediaViewerScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed interface LaunchIntentRequest {
    data object Default : LaunchIntentRequest
    data class ViewMedia(val uri: Uri, val mimeType: String?, val mediaId: Long?) : LaunchIntentRequest
    data class PickMedia(val allowMultiple: Boolean, val mimeTypeFilter: String?) : LaunchIntentRequest
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var mediaStoreDataSource: MediaStoreDataSource

    @Inject
    lateinit var jankTracker: com.arijit.gallery.core.monitoring.JankTracker

    private var launchRequestState by mutableStateOf<LaunchIntentRequest>(LaunchIntentRequest.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jankTracker.startTracking(this)

        // Lock window to maximum display refresh rate (120Hz on Motorola Edge 70)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val attrs = window.attributes
            attrs.preferredRefreshRate = 120f
            display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { mode ->
                if (mode.refreshRate >= 115f) {
                    attrs.preferredDisplayModeId = mode.modeId
                }
            }
            window.attributes = attrs
        }

        launchRequestState = parseIntent(intent)
        enableEdgeToEdge()

        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.arijit.gallery.core.monitoring.LocalJankTracker provides jankTracker
            ) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val settings by settingsRepository.userSettings.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )

            var accessLevel by remember {
                mutableStateOf(MediaPermissionHelper.getAccessLevel(context))
            }
            var askedOnce by remember { mutableStateOf(false) }

            val photosViewModel: PhotosViewModel = hiltViewModel()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                askedOnce = true
                val updated = MediaPermissionHelper.getAccessLevel(context)
                accessLevel = updated
                if (updated != MediaAccessLevel.NONE) {
                    photosViewModel.refreshAfterPermission()
                }
            }

            // On resume: re-check permissions and immediately scan for newly captured camera photos
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val updated = MediaPermissionHelper.getAccessLevel(context)
                        accessLevel = updated
                        if (updated != MediaAccessLevel.NONE) {
                            photosViewModel.onAppResumed(context)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            GalleryTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                val request = launchRequestState
                when {
                    request is LaunchIntentRequest.ViewMedia && request.mediaId == null -> {
                        DisposableEffect(Unit) {
                            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                            onDispose { }
                        }
                        ExternalMediaViewerScreen(
                            uri = request.uri,
                            mimeType = request.mimeType,
                            onClose = { finish() }
                        )
                    }
                    accessLevel == MediaAccessLevel.NONE -> {
                        PermissionRationaleScreen(
                            showSettingsFallback = askedOnce,
                            onRequestPermission = {
                                permissionLauncher.launch(MediaPermissionHelper.requiredPermissions())
                            },
                            onOpenSettings = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                                startActivity(intent)
                            }
                        )
                    }
                    else -> {
                        val initialMediaId = (request as? LaunchIntentRequest.ViewMedia)?.mediaId
                        val picker = request as? LaunchIntentRequest.PickMedia
                        GalleryRoot(
                            accessLevel = accessLevel,
                            onRequestFullAccess = {
                                permissionLauncher.launch(MediaPermissionHelper.requiredPermissions())
                            },
                            photosViewModel = photosViewModel,
                            initialMediaId = initialMediaId,
                            isPickerMode = picker != null,
                            allowMultiPick = picker?.allowMultiple == true,
                            onPickMediaItems = ::deliverPickerResult,
                            onCancelPicker = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                }
            }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        jankTracker.stopTracking()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequestState = parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?): LaunchIntentRequest {
        if (intent == null) return LaunchIntentRequest.Default
        return when (intent.action) {
            Intent.ACTION_VIEW,
            MediaStore.ACTION_REVIEW,
            "com.android.camera.action.REVIEW" -> {
                val uri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    val mediaId = mediaStoreDataSource.resolveMediaIdFromUri(uri)
                    LaunchIntentRequest.ViewMedia(uri = uri, mimeType = intent.type, mediaId = mediaId)
                } else {
                    LaunchIntentRequest.Default
                }
            }
            Intent.ACTION_PICK,
            Intent.ACTION_GET_CONTENT -> {
                val allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                LaunchIntentRequest.PickMedia(allowMultiple = allowMultiple, mimeTypeFilter = intent.type)
            }
            Intent.ACTION_SET_WALLPAPER -> {
                LaunchIntentRequest.PickMedia(allowMultiple = false, mimeTypeFilter = "image/*")
            }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    val mediaId = mediaStoreDataSource.resolveMediaIdFromUri(uri)
                    LaunchIntentRequest.ViewMedia(uri = uri, mimeType = intent.type, mediaId = mediaId)
                } else {
                    LaunchIntentRequest.Default
                }
            }
            else -> LaunchIntentRequest.Default
        }
    }

    private fun deliverPickerResult(items: List<MediaItem>) {
        if (items.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val uris = items.map { it.contentUri.toUri() }
        val resultIntent = Intent().apply {
            if (uris.size == 1) {
                data = uris.first()
            } else {
                val clip = ClipData.newUri(contentResolver, "Selected media", uris.first())
                for (i in 1 until uris.size) {
                    clip.addItem(ClipData.Item(uris[i]))
                }
                clipData = clip
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
