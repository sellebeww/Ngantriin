package org.umn.ngantriin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.ui.theme.Spacing

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Label di atas pendek dan tetap, `value` yang panjangnya berubah
        // (misalnya alamat lengkap), jadi ia yang dapat ruang fleksibel
        // berbobot dan membungkus ke dalamnya. Kalau bobot diberikan ke `label`,
        // `value` yang panjang bisa menekan lebar `label` mendekati nol sehingga
        // Compose membungkusnya satu karakter per baris.
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Aksi destruktif selalu bertanya dulu. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        shape = MaterialTheme.shapes.large,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

/**
 * Kolom setinggi layar yang menjaga konten paling bawah (biasanya
 * tombol utama layar) tetap di tepi bawah, tapi bisa di-scroll dan tidak
 * mendorongnya keluar jangkauan saat semuanya tidak muat: HP pendek,
 * skala font besar, atau keyboard terbuka.
 *
 * Pakai `Spacer(Modifier.weight(1f))` di dalamnya persis seperti Column biasa.
 */
@Composable
fun BottomAnchoredColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Memberi kolom tinggi minimum setinggi viewport membuat bobotnya
        // tetap bermakna, lewat itu kolom tumbuh dan bisa di-scroll.
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = viewportHeight),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/**
 * Membuat baris di dalam kolom yang diberi padding gutter layar bisa penuh
 * dari tepi ke tepi, seperti chip row yang di-scroll: ia diukur lebih lebar
 * [inset] di kedua sisi, jadi chip-nya meluncur keluar dari tepi layar dan
 * tidak terpotong di gutter.
 *
 * Tambahkan `.padding(horizontal = inset)` *setelah* `horizontalScroll` supaya chip
 * pertama dan terakhir tetap bersandar di garis gutter saat baris belum di-scroll.
 */
fun Modifier.fullBleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val wide = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}
