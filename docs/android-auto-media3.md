La implementación más limpia y con menor fricción en Android moderno es utilizar la biblioteca **AndroidX Media3**. Esta arquitectura unifica `ExoPlayer` y `MediaSession`, eliminando el código repetitivo de sincronización de estados, callbacks de transporte y manejo manual de foco de audio que exigían las implementaciones heredadas.

**1. Dependencias (build.gradle)**

```gradle
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-session:1.2.1")

```

**2. Configuración del Manifiesto (AndroidManifest.xml)**
Se debe declarar el soporte automotriz y el servicio de medios.

```xml
<!-- Archivo: res/xml/automotive_app_desc.xml -->
<automotiveApp>
    <uses name="media" />
</automotiveApp>

```

Dentro de `<application>`:

```xml
<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc.xml" />

<service
    android:name=".AudioAutoService"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService" />
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>

```

**3. Servicio Principal (MediaLibraryService)**
Media3 automatiza el puente entre el reproductor y la interfaz del vehículo. Solo es necesario proveer la estructura de datos (el árbol de navegación).

```kotlin
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class AudioAutoService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        
        // Configurar player con manejo automático de foco de audio (GPS, llamadas)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        val callback = object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return Futures.immediateFuture(
                    LibraryResult.ofItem(MediaItem.Builder().setMediaId("root").build(), params)
                )
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                
                // Metadatos visibles en la pantalla del vehículo (título e imagen)
                val metadata = MediaMetadata.Builder()
                    .setTitle("Título de la pista")
                    .setArtist("Nombre del Artista")
                    .setArtworkUri(Uri.parse("https://url-a-tu-imagen.jpg"))
                    .setIsPlayable(true)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId("pista_1")
                    .setUri("https://url-a-tu-audio.mp3")
                    .setMediaMetadata(metadata)
                    .build()

                return Futures.immediateFuture(
                    LibraryResult.ofItemList(listOf(mediaItem), params)
                )
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }
}

```

**Ventajas operativas de este modelo:**

* **Cero manejo de estado manual:** Al inyectar la URL y los metadatos en el `MediaItem`, Media3 expone automáticamente el título, el artista y la imagen en la pantalla del auto, y procesa los toques en "Play/Pause" sin programar callbacks adicionales.
* **Gestión de interrupciones:** El parámetro `setAudioAttributes(..., true)` en el builder del `ExoPlayer` delega al sistema operativo la pausa y reanudación del audio cuando ingresan llamadas o notificaciones del GPS.