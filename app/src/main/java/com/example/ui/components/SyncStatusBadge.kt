package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyncStatus
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.OnLimeText
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending

@Composable
fun SyncStatusBadge(
    status: SyncStatus,
    isOffline: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, contentColor, text, icon) = when {
        isOffline -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Offline Mode",
            Icons.Default.CloudOff
        )
        status == SyncStatus.SYNCED -> Quad(
            ElectricLime.copy(alpha = 0.22f),
            OnLimeText, // High contrast: Dark Navy on Electric Lime
            "Synced",
            Icons.Default.CloudDone
        )
        status == SyncStatus.SYNC_PENDING -> Quad(
            StatusPending.copy(alpha = 0.22f),
            Color(0xFFB06800),
            "Syncing...",
            Icons.Default.Sync
        )
        status == SyncStatus.SAVED_LOCALLY -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Saved Locally",
            Icons.Default.CloudQueue
        )
        status == SyncStatus.SYNC_FAILED -> Quad(
            StatusAlert.copy(alpha = 0.18f),
            StatusAlert,
            "Sync Failed",
            Icons.Default.ErrorOutline
        )
        else -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Local",
            Icons.Default.CloudDone
        )
    }

    Box(
        modifier = modifier
            .testTag("sync_status_badge")
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status == SyncStatus.SYNC_PENDING && !isOffline) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
