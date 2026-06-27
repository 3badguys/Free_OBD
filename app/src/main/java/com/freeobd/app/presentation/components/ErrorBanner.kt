package com.freeobd.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freeobd.app.presentation.theme.*

/**
 * A dismissible error banner suitable for inline error display.
 *
 * @param message The error message to display.
 * @param isVisible Whether the banner is currently shown.
 * @param onDismiss Called when the user taps the close button.
 * @param onRetry Optional retry action — shown as a text button if provided.
 */
@Composable
fun ErrorBanner(
    message: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Surface(
            color = StatusRed.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = StatusRed,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRed,
                    modifier = Modifier.weight(1f)
                )

                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Retry", color = StatusRed, style = MaterialTheme.typography.labelMedium)
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
