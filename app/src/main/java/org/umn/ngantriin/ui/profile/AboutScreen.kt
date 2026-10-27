package org.umn.ngantriin.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.umn.ngantriin.R
import org.umn.ngantriin.ui.theme.Spacing

/** Bantuan, privasi, dan ketentuan dalam satu halaman statis, dibuka dari Pengaturan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter)
        ) {
            Spacer(Modifier.height(Spacing.lg))

            AboutSection(R.string.about_help_heading, R.string.about_help_body)
            Spacer(Modifier.height(Spacing.lg))
            AboutSection(R.string.about_privacy_heading, R.string.about_privacy_body)
            Spacer(Modifier.height(Spacing.lg))
            AboutSection(R.string.about_terms_heading, R.string.about_terms_body)

            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun AboutSection(heading: Int, body: Int) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
