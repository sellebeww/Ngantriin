package org.umn.ngantriin.ui.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.umn.ngantriin.R
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.ui.components.ConfirmDialog
import org.umn.ngantriin.ui.components.FullScreenLoader
import org.umn.ngantriin.ui.components.NgantriinTextField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.components.dateTimeText
import org.umn.ngantriin.ui.theme.Spacing

/** One application with its proof files, and the decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApplicationScreen(
    restaurantId: String,
    status: VerificationStatus,
    onBack: () -> Unit,
    onMessage: (AppError) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = containerViewModel(key = restaurantId) { c ->
        AdminApplicationViewModel(c.adminRepository, restaurantId, status)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmApprove by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }

    LaunchedEffect(state.decided) { if (state.decided) onBack() }
    LaunchedEffect(state.error) {
        state.error?.let {
            onMessage(it)
            viewModel.dismissError()
        }
    }

    if (confirmApprove) {
        ConfirmDialog(
            title = stringResource(R.string.admin_approve_title),
            message = stringResource(R.string.admin_approve_message),
            confirmLabel = stringResource(R.string.admin_approve),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmApprove = false
                viewModel.approve()
            },
            onDismiss = { confirmApprove = false }
        )
    }
    if (rejecting) {
        RejectDialog(
            onConfirm = { reason ->
                rejecting = false
                viewModel.reject(reason)
            },
            onDismiss = { rejecting = false }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_application_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        val application = state.application
        when {
            state.isLoading -> FullScreenLoader(Modifier.padding(padding))
            application == null -> Unit // The error snackbar says why; nothing to decide on.
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Details(application)
                Documents(
                    documents = state.documents,
                    imageUrls = state.imageUrls,
                    onOpenPdf = { document ->
                        viewModel.openDocument(document) { url ->
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: ActivityNotFoundException) {
                                onMessage(AppError.Unknown("no viewer"))
                            }
                        }
                    }
                )

                // A decision exists only for a pending application; the others are history.
                if (application.status == VerificationStatus.PENDING_VERIFICATION) {
                    PrimaryButton(
                        text = stringResource(R.string.admin_approve),
                        onClick = { confirmApprove = true },
                        enabled = !state.isWorking,
                        loading = state.isWorking
                    )
                    SecondaryButton(
                        text = stringResource(R.string.admin_reject),
                        onClick = { rejecting = true },
                        enabled = !state.isWorking
                    )
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Composable
private fun Details(application: RestaurantApplication) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = application.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Label(R.string.admin_section_restaurant)
        Field(R.string.admin_label_address, application.address)
        Field(R.string.admin_label_category, application.category)
        if (application.description.isNotBlank()) Text(
            text = application.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(Spacing.md))
        Label(R.string.admin_section_applicant)
        Field(R.string.admin_label_name, application.applicantName)
        Field(R.string.admin_label_email, application.applicantEmail)
        Field(R.string.admin_label_phone, application.contactPhone)
        application.applicantRole?.let { Field(R.string.admin_label_role, stringResource(it.label())) }
        application.submittedAt?.let {
            Text(
                text = stringResource(R.string.admin_submitted, dateTimeText(it)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (application.status == VerificationStatus.REJECTED) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    R.string.verification_rejected_body,
                    application.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.verification_no_reason)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun ApplicantRole.label(): Int = when (this) {
    ApplicantRole.OWNER -> R.string.applicant_role_owner
    ApplicantRole.MANAGER -> R.string.applicant_role_manager
    ApplicantRole.AUTHORIZED_STAFF -> R.string.applicant_role_authorized
}

@Composable
private fun Documents(
    documents: List<ApplicationDocument>,
    imageUrls: Map<String, String>,
    onOpenPdf: (ApplicationDocument) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label(R.string.admin_section_documents)
        if (documents.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_no_documents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        documents.forEach { document ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(
                        if (document.type == ProofType.BUSINESS_LICENSE) R.string.admin_doc_license
                        else R.string.admin_doc_ownership
                    ) + " · " + stringResource(R.string.admin_doc_round, document.round),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (document.isPdf) {
                    SecondaryButton(text = stringResource(R.string.admin_doc_open_pdf), onClick = { onOpenPdf(document) })
                } else {
                    val url = imageUrls[document.id]
                    if (url == null) {
                        Text(
                            text = stringResource(R.string.admin_doc_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Field(label: Int, value: String) {
    Text(
        text = stringResource(label) + ": " + value.ifBlank { "-" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** The owner reads this reason, so it cannot be empty. */
@Composable
private fun RejectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_reject_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.admin_reject_message), style = MaterialTheme.typography.bodyMedium)
                NgantriinTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = stringResource(R.string.admin_reject_reason),
                    singleLine = false
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text(stringResource(R.string.admin_reject_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
