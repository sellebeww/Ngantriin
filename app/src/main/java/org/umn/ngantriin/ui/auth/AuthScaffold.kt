package org.umn.ngantriin.ui.auth

import androidx.compose.foundation.background
import org.umn.ngantriin.data.remote.demo.DemoSeed
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.umn.ngantriin.R
import org.umn.ngantriin.ui.components.NgantriinLogo
import org.umn.ngantriin.ui.theme.Spacing
import org.umn.ngantriin.ui.theme.balanced

/** Kerangka bersama untuk layar masuk dan daftar. */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
    ) {
        Spacer(Modifier.height(Spacing.lg))

        if (onBack != null) {
            Box(modifier = Modifier.fillMaxWidth().height(Spacing.touchTarget)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NgantriinLogo(modifier = Modifier.width(164.dp).height(132.dp))

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.balanced(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        content()

        Spacer(Modifier.height(Spacing.xxxl))
    }
}

/**
 * Mode demo tidak mengautentikasi siapa pun, dan mengatakannya terus terang
 * lebih baik daripada form login yang terlihat seolah mengecek sesuatu.
 */
@Composable
fun DemoModeNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.medium
            )
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = Icons.Filled.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = stringResource(R.string.auth_demo_mode_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.auth_demo_mode_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Login contoh admin dan staf, satu per restoran. Mengetuk baris mengisi email
 * ke form. Dilipat secara default supaya 22 baris tidak mendorong form ke bawah.
 */
@Composable
fun DemoStaffAccounts(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val names = DemoSeed.RESTAURANTS.associate { it.id to it.name }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.auth_demo_staff_title, DemoSeed.STAFF_ACCOUNTS.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded) {
            Text(
                text = stringResource(R.string.auth_demo_staff_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DemoSeed.ADMIN_EMAILS.forEach { email ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(email) }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(stringResource(R.string.auth_demo_admin_name), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DemoSeed.STAFF_ACCOUNTS.forEach { (email, restaurantId) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(email) }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(names[restaurantId].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
