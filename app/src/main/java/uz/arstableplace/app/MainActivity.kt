package uz.arstableplace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ARPlacementScreen()
            }
        }
    }
}

@Composable
private fun ARPlacementScreen() {
    val accent = Color(0xFF28E0A7)
    val panel = Color(0xD9141A22)

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = rememberModelInstance(
        modelLoader = modelLoader,
        assetFileLocation = "models/stable_model.glb"
    )

    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var cameraTracking by remember { mutableStateOf(false) }
    var placementReady by remember { mutableStateOf(false) }
    val latestHit = remember { AtomicReference<HitResult?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            anchor?.detach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            planeRenderer = anchor == null,
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
            },
            onSessionUpdated = { _, frame ->
                val tracking = frame.camera.trackingState == TrackingState.TRACKING
                if (cameraTracking != tracking) cameraTracking = tracking

                if (anchor == null && tracking) {
                    val hit = frame.hitTest(frame.width / 2f, frame.height / 2f)
                        .firstOrNull { result ->
                            val plane = result.trackable as? Plane
                            plane != null &&
                                plane.trackingState == TrackingState.TRACKING &&
                                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.isPoseInPolygon(result.hitPose)
                        }
                    latestHit.set(hit)
                    val ready = hit != null
                    if (placementReady != ready) placementReady = ready
                } else {
                    latestHit.set(null)
                    if (placementReady) placementReady = false
                }
            }
        ) {
            anchor?.let { stableAnchor ->
                AnchorNode(anchor = stableAnchor) {
                    modelInstance?.let { instance ->
                        ModelNode(
                            modelInstance = instance,
                            scaleToUnits = 0.45f
                        )
                    }
                }
            }
        }

        StatusPanel(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            text = when {
                anchor != null -> "Model stabil joylashtirildi — atrofida yurishingiz mumkin"
                !cameraTracking -> "Kamerani pol yoki stol tomonga sekin harakatlantiring"
                placementReady -> "Joy tayyor — JOYLASHTIRISH tugmasini bosing"
                else -> "Tekis yuzani qidirmoqda…"
            },
            panel = panel,
            accent = accent,
            ready = anchor != null || placementReady
        )

        if (anchor == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .border(
                        width = 3.dp,
                        color = if (placementReady) accent else Color.White.copy(alpha = 0.75f),
                        shape = CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(7.dp)
                        .background(
                            color = if (placementReady) accent else Color.White,
                            shape = CircleShape
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (anchor == null) {
                    "Markazdagi halqani pol yoki stol ustiga qarating"
                } else {
                    "Model ARCore anchor bilan real joyga mahkamlangan"
                },
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            if (anchor == null) {
                Button(
                    onClick = {
                        latestHit.getAndSet(null)?.let { hit ->
                            anchor = hit.createAnchor()
                        }
                    },
                    enabled = placementReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF08110E),
                        disabledContainerColor = Color.White.copy(alpha = 0.15f),
                        disabledContentColor = Color.White.copy(alpha = 0.55f)
                    )
                ) {
                    Text("JOYLASHTIRISH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            anchor?.detach()
                            anchor = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF10161E)
                        )
                    ) {
                        Text("QAYTA JOYLASH", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    modifier: Modifier,
    text: String,
    panel: Color,
    accent: Color,
    ready: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = panel
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (ready) accent else Color.White.copy(alpha = 0.55f),
                        CircleShape
                    )
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
