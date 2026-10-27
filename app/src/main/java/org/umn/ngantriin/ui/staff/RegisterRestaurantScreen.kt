package org.umn.ngantriin.ui.staff

import android.content.Context
import org.umn.ngantriin.domain.model.ProofType
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Phone
import androidx.compose.foundation.layout.width
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.ngantriin.R
import org.umn.ngantriin.di.containerViewModel
import org.umn.ngantriin.ui.components.NgantriinTextField
import org.umn.ngantriin.ui.components.PrimaryButton
import org.umn.ngantriin.ui.components.SecondaryButton
import org.umn.ngantriin.ui.search.ChoiceChip
import org.umn.ngantriin.ui.staff.RegisterRestaurantUiState.Field
import org.umn.ngantriin.ui.theme.Spacing
import java.io.ByteArrayOutputStream
import kotlin.math.max

private val CATEGORIES = listOf(
    "Indonesian", "Coffee", "Japanese", "Korean", "Chinese", "Mexican",
    "Western", "Seafood", "Bakery", "Dessert", "Other"
)

/**
 * Di sini staf memasukkan restoran ke aplikasi. Tidak ada yang tampil ke pelanggan
 * sampai ini berhasil, dan akun menjadi staf tepat restoran yang dibuatnya.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegisterRestaurantScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    modifier: Modifier = Modifier,
    resubmitId: String? = null
) {
    val viewModel = containerViewModel { c ->
        RegisterRestaurantViewModel(
            authRepository = c.authRepository,
            restaurantRepository = c.restaurantRepository,
            locationProvider = c.locationProvider,
            preferences = c.userPreferencesRepository,
            resubmitId = resubmitId
        )
    }
    val isResubmit = resubmitId != null
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = readPhoto(context, uri)
            photoUri = uri.takeIf { bytes != null }
            viewModel.onPhoto(bytes)
        }
    }

    var proofTooLarge by remember { mutableStateOf(false) }
    val proofMimeTypes = arrayOf("application/pdf", "image/jpeg", "image/png")
    val licensePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = readProof(context, uri, ProofType.BUSINESS_LICENSE)
            proofTooLarge = picked == null
            if (picked != null) viewModel.onLicense(picked, displayName(context, uri))
        }
    }
    val ownershipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = readProof(context, uri, ProofType.OWNERSHIP_PROOF)
            proofTooLarge = picked == null
            if (picked != null) viewModel.onOwnershipProof(picked, displayName(context, uri))
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Penolakan ditangani provider, yang melaporkannya sebagai error.
        viewModel.useCurrentLocation()
    }

    LaunchedEffect(state.registered) {
        if (state.registered) onRegistered()
    }

    val busy = state.isSubmitting

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isResubmit) R.string.resubmit_restaurant_title else R.string.register_restaurant_title
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            Text(
                text = stringResource(
                    if (isResubmit) R.string.resubmit_restaurant_subtitle else R.string.register_restaurant_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xl))

            if (!isResubmit) {
            PhotoPicker(
                photoUri = photoUri,
                enabled = !busy,
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            Spacer(Modifier.height(Spacing.lg))

            NgantriinTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = stringResource(R.string.register_restaurant_name_label),
                leadingIcon = Icons.Filled.Storefront,
                errorText = state.errors[Field.NAME]?.let { stringResource(it) },
                enabled = !busy
            )
            Spacer(Modifier.height(Spacing.lg))

            NgantriinTextField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = stringResource(R.string.register_restaurant_address_label),
                leadingIcon = Icons.Filled.LocationOn,
                errorText = state.errors[Field.ADDRESS]?.let { stringResource(it) },
                enabled = !busy
            )
            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(R.string.register_restaurant_category_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                CATEGORIES.forEach { category ->
                    ChoiceChip(
                        label = category,
                        selected = state.category == category,
                        onClick = { if (!busy) viewModel.onCategory(category) }
                    )
                }
            }
            FieldError(state.errors[Field.CATEGORY])
            Spacer(Modifier.height(Spacing.lg))

            NgantriinTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = stringResource(R.string.register_restaurant_description_label),
                singleLine = false,
                enabled = !busy
            )
            Spacer(Modifier.height(Spacing.xl))

            SectionLabel(stringResource(R.string.register_restaurant_location_title))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                NgantriinTextField(
                    value = state.latitude,
                    onValueChange = viewModel::onLatitude,
                    label = stringResource(R.string.register_restaurant_latitude_label),
                    keyboardType = KeyboardType.Decimal,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
                NgantriinTextField(
                    value = state.longitude,
                    onValueChange = viewModel::onLongitude,
                    label = stringResource(R.string.register_restaurant_longitude_label),
                    keyboardType = KeyboardType.Decimal,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
            }
            FieldError(state.errors[Field.LOCATION])
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(
                text = stringResource(R.string.register_restaurant_use_my_location),
                onClick = {
                    if (viewModel.hasLocationPermission()) {
                        viewModel.useCurrentLocation()
                    } else {
                        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                enabled = !busy && !state.isLocating,
                icon = Icons.Filled.LocationOn
            )
            Spacer(Modifier.height(Spacing.xl))

            SectionLabel(stringResource(R.string.register_restaurant_hours_title))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.open24Hours, onCheckedChange = viewModel::onOpen24Hours, enabled = !busy)
                Text(
                    text = stringResource(R.string.register_restaurant_open_24h),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (!state.open24Hours) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    NgantriinTextField(
                        value = state.openingTime,
                        onValueChange = viewModel::onOpening,
                        label = stringResource(R.string.register_restaurant_opens_label),
                        placeholder = "10:00",
                        keyboardType = KeyboardType.Number,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    )
                    NgantriinTextField(
                        value = state.closingTime,
                        onValueChange = viewModel::onClosing,
                        label = stringResource(R.string.register_restaurant_closes_label),
                        placeholder = "22:00",
                        keyboardType = KeyboardType.Number,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    )
                }
                FieldError(state.errors[Field.HOURS])
            }
            Spacer(Modifier.height(Spacing.xl))

            SectionLabel(stringResource(R.string.register_restaurant_queue_title))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                NgantriinTextField(
                    value = state.serviceMinutes,
                    onValueChange = viewModel::onServiceMinutes,
                    label = stringResource(R.string.register_restaurant_service_label),
                    keyboardType = KeyboardType.Number,
                    errorText = state.errors[Field.SERVICE]?.let { stringResource(it) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
                NgantriinTextField(
                    value = state.capacity,
                    onValueChange = viewModel::onCapacity,
                    label = stringResource(R.string.register_restaurant_capacity_label),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    errorText = state.errors[Field.CAPACITY]?.let { stringResource(it) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.xl))

            }

            SectionLabel(stringResource(R.string.register_restaurant_verification_title))
            Text(
                text = stringResource(R.string.register_restaurant_verification_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))

            NgantriinTextField(
                value = state.contactPhone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.register_restaurant_phone_label),
                placeholder = "+62 812 3456 7890",
                leadingIcon = Icons.Filled.Phone,
                keyboardType = KeyboardType.Phone,
                errorText = state.errors[Field.PHONE]?.let { stringResource(it) },
                enabled = !busy
            )
            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = stringResource(R.string.register_restaurant_role_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ApplicantRole.entries.forEach { role ->
                    ChoiceChip(
                        label = stringResource(role.labelRes()),
                        selected = state.applicantRole == role,
                        onClick = { if (!busy) viewModel.onRole(role) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            ProofPickerRow(
                title = stringResource(R.string.register_restaurant_license_title),
                hint = stringResource(R.string.register_restaurant_license_hint),
                fileName = state.licenseName,
                errorRes = state.errors[Field.LICENSE],
                enabled = !busy,
                onPick = { licensePicker.launch(proofMimeTypes) },
                onClear = { viewModel.onLicense(null, null) }
            )
            Spacer(Modifier.height(Spacing.md))
            ProofPickerRow(
                title = stringResource(R.string.register_restaurant_ownership_title),
                hint = stringResource(R.string.register_restaurant_ownership_hint),
                fileName = state.ownershipProofName,
                errorRes = null,
                enabled = !busy,
                onPick = { ownershipPicker.launch(proofMimeTypes) },
                onClear = { viewModel.onOwnershipProof(null, null) }
            )
            if (proofTooLarge) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.register_restaurant_proof_unreadable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(Spacing.xl))

            state.error?.let {
                Text(
                    text = stringResource(it.messageRes, *it.formatArgs.toTypedArray()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(Spacing.md))
            }

            PrimaryButton(
                text = stringResource(
                    if (isResubmit) R.string.resubmit_restaurant_submit else R.string.register_restaurant_submit
                ),
                onClick = viewModel::submit,
                loading = busy
            )
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = Spacing.sm)
    )
}

@Composable
private fun FieldError(messageRes: Int?) {
    if (messageRes == null) return
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun PhotoPicker(photoUri: Uri?, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.register_restaurant_photo_add),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Memperkecil ke 1600px di sisi terpanjang lalu di-encode ulang sebagai JPEG,
 * dengan memperhatikan rotasi EXIF (foto galeri sering tersimpan menyamping).
 * Null kalau file tidak bisa di-decode.
 */
private suspend fun readPhoto(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_EDGE * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = MAX_PHOTO_EDGE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        ByteArrayOutputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
    }.getOrNull()
}

private const val MAX_PHOTO_EDGE = 1600


@StringRes
private fun ApplicantRole.labelRes(): Int = when (this) {
    ApplicantRole.OWNER -> R.string.applicant_role_owner
    ApplicantRole.MANAGER -> R.string.applicant_role_manager
    ApplicantRole.AUTHORIZED_STAFF -> R.string.applicant_role_authorized
}

/** Satu slot dokumen: untuk apa, berkas yang dipilih, dan cara menukar atau membuangnya. */
@Composable
private fun ProofPickerRow(
    title: String,
    hint: String,
    fileName: String?,
    errorRes: Int?,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (fileName != null) Icons.Filled.Description else Icons.Filled.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = fileName ?: stringResource(R.string.register_restaurant_no_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPick, enabled = enabled) {
                    Text(
                        stringResource(
                            if (fileName == null) R.string.register_restaurant_choose_file
                            else R.string.register_restaurant_change_file
                        )
                    )
                }
                if (fileName != null) {
                    TextButton(onClick = onClear, enabled = enabled) {
                        Text(stringResource(R.string.register_restaurant_remove_file))
                    }
                }
            }
            FieldError(errorRes)
        }
    }
}

private const val MAX_PROOF_BYTES = 5 * 1024 * 1024

private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

/**
 * PDF dikirim apa adanya (batas 5 MB, ditegakkan lagi oleh bucket), gambar
 * diperkecil dan di-encode ulang jadi JPEG seperti foto restoran. Null kalau
 * file tidak terbaca atau terlalu besar.
 */
private suspend fun readProof(context: Context, uri: Uri, type: ProofType): ProofDocument? {
    val isPdf = context.contentResolver.getType(uri) == "application/pdf"
    if (!isPdf) return readPhoto(context, uri)?.let { ProofDocument(type, it, isPdf = false) }
        ?.takeIf { it.bytes.size <= MAX_PROOF_BYTES }
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PROOF_BYTES) return@use null
                    out.write(chunk, 0, read)
                }
                ProofDocument(type, out.toByteArray(), isPdf = true)
            }
        }.getOrNull()
    }
}
