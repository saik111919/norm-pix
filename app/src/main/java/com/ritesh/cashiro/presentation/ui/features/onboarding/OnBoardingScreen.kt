package com.ritesh.cashiro.presentation.ui.features.onboarding

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import com.ritesh.cashiro.presentation.ui.theme.CashiroTheme
import androidx.work.WorkInfo
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.presentation.common.icons.IconResource
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.AccountCard
import com.ritesh.cashiro.presentation.ui.components.ColorPickerContent
import com.ritesh.cashiro.presentation.ui.components.CurrencyBottomSheet
import com.ritesh.cashiro.presentation.ui.components.IphoneFrame
import com.ritesh.cashiro.presentation.ui.components.SmsParsingProgressIndicator
import com.ritesh.cashiro.presentation.ui.components.TiledScrollingIconBackground
import com.ritesh.cashiro.presentation.ui.features.profile.EditProfileState
import com.ritesh.cashiro.presentation.ui.features.profile.PresetAvatarSelection
import com.ritesh.cashiro.presentation.ui.features.profile.ProfileCardPreview
import com.ritesh.cashiro.presentation.ui.icons.Cashiro
import com.ritesh.cashiro.presentation.ui.icons.CashiroOutline
import com.ritesh.cashiro.presentation.ui.icons.Edit2
import com.ritesh.cashiro.presentation.ui.icons.HierarchySquare3
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Information
import com.ritesh.cashiro.presentation.ui.icons.Messages
import com.ritesh.cashiro.presentation.ui.icons.Notification
import com.ritesh.cashiro.presentation.ui.icons.Wallet3
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import java.time.LocalDateTime
import java.math.BigDecimal
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnBoardingScreen(
    modifier: Modifier = Modifier,
    onOnBoardingComplete: () -> Unit,
    onBoardingViewModel: OnBoardingViewModel = hiltViewModel(),
) {
    val uiState by onBoardingViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.onboardingFinished) {
        if (uiState.onboardingFinished) {
            onOnBoardingComplete()
        }
    }

    val multiplePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (uiState.currentStep == 2) {
                val readSmsGranted = permissions[Manifest.permission.READ_SMS] == true
                if (readSmsGranted) {
                    onBoardingViewModel.onPermissionResult(true)
                    onBoardingViewModel.nextStep()
                } else {
                    onBoardingViewModel.onPermissionDenied()
                }
            } else {
                onBoardingViewModel.nextStep()
            }
        }

    OnBoardingScreenContent(
        modifier = modifier,
        uiState = uiState,
        onNext = { onBoardingViewModel.nextStep() },
        onPrevious = { onBoardingViewModel.previousStep() },
        onRequestPermissions = { permissions -> multiplePermissionLauncher.launch(permissions) },
        onStartScan = { onBoardingViewModel.startSmsScan() },
        onSkipSms = { onBoardingViewModel.skipSmsPermission() },
        onSkipSync = { onBoardingViewModel.skipSync() },
        onSaveManualAccount = { onBoardingViewModel.saveManualAccount() },
        onSaveProfile = { onBoardingViewModel.saveProfile() },
        onNameChange = { onBoardingViewModel.onNameChange(it) },
        onProfileImageChange = { onBoardingViewModel.onProfileImageChange(it) },
        onBackgroundColorChange = { onBoardingViewModel.onBackgroundColorChange(it) },
        onSetMainAccount = { b, a -> onBoardingViewModel.setAsMainAccount(b, a) },
        onUpdateManualAccountName = { onBoardingViewModel.updateManualAccountName(it) },
        onUpdateManualAccountBalance = { onBoardingViewModel.updateManualAccountBalance(it) },
        onUpdateManualAccountLast4 = { onBoardingViewModel.updateManualAccountLast4(it) },
        onToggleCurrencyBottomSheet = { onBoardingViewModel.toggleCurrencyBottomSheet(it) },
        onUpdateSelectedCurrency = { onBoardingViewModel.updateSelectedCurrency(it) },
        onToggleAccountSelectionForMerge = { onBoardingViewModel.toggleAccountSelectionForMerge(it) },
        onMergeSelectedAccounts = { onBoardingViewModel.mergeSelectedAccounts(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnBoardingScreenContent(
    modifier: Modifier = Modifier,
    uiState: OnBoardingUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit,
    onStartScan: () -> Unit,
    onSkipSms: () -> Unit,
    onSkipSync: () -> Unit,
    onSaveManualAccount: () -> Unit,
    onSaveProfile: () -> Unit,
    onNameChange: (String) -> Unit,
    onProfileImageChange: (Uri?) -> Unit,
    onBackgroundColorChange: (Color) -> Unit,
    onSetMainAccount: (String, String) -> Unit,
    onUpdateManualAccountName: (String) -> Unit,
    onUpdateManualAccountBalance: (String) -> Unit,
    onUpdateManualAccountLast4: (String) -> Unit,
    onToggleCurrencyBottomSheet: (Boolean) -> Unit,
    onUpdateSelectedCurrency: (String) -> Unit,
    onToggleAccountSelectionForMerge: (String) -> Unit,
    onMergeSelectedAccounts: (AccountBalanceEntity) -> Unit
) {

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StageProgressIndicator(
                currentStage = uiState.currentStep,
                totalStages = 5,
                modifier = Modifier.padding(Spacing.lg).statusBarsPadding()
            )
        },
        bottomBar = {
            OnBoardingBottomBar(
                currentStep = uiState.currentStep,
                onBack = onPrevious,
                onContinue = {
                    when (uiState.currentStep) {
                        1 -> onNext()
                        2 -> {
                            val permissions = arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                            onRequestPermissions(permissions)
                        }
                        3 -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                onRequestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            } else {
                                onNext()
                            }
                        }
                        4 -> {
                            // Account Phase
                            if (uiState.mainAccountKey != null) {
                                onNext() // To Profile
                            } else if (uiState.manualAccountName.isNotBlank() && 
                                       uiState.manualAccountBalance.isNotBlank() && 
                                       uiState.manualAccountLast4.length == 4) {
                                onSaveManualAccount()
                            } else if (uiState.scanWorkInfo?.state == WorkInfo.State.SUCCEEDED) {
                                // Handled via selection
                            } else {
                                onStartScan()
                            }
                        }
                        5 -> onSaveProfile()
                    }
                },
                isScanning = uiState.isScanning,
                isContinueEnabled =
                    when (uiState.currentStep) {
                        4 -> {
                            if (uiState.isScanning) false
                            else if (uiState.hasSkippedPermission || (uiState.accounts.isEmpty() && uiState.scanWorkInfo?.state == WorkInfo.State.FAILED)) {
                                uiState.manualAccountName.isNotBlank() && 
                                uiState.manualAccountBalance.isNotBlank() && 
                                uiState.manualAccountLast4.length == 4
                            } else if (uiState.accounts.isNotEmpty()) {
                                uiState.mainAccountKey != null
                            } else {
                                // Scanning not started yet
                                true 
                            }
                        }
                        5 -> uiState.profileState.editedUserName.isNotBlank()
                        else -> true
                    }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
                    .using(SizeTransform(clip = false))
            },
            label = "OnBoardingStepTransition"
        ) { step ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (step) {
                    1 -> WelcomeStep()
                    2 -> PermissionsStep(
                        isNotification = false,
                        onSkip = onSkipSms
                    )
                    3 -> PermissionsStep(
                        isNotification = true,
                        onSkip = onNext
                    )
                    4 -> {
                        // The account phase (Sync -> Result List / Manual)
                        BlurredAnimatedVisibility(
                            visible = uiState.hasSkippedPermission || (uiState.accounts.isEmpty() && uiState.scanWorkInfo?.state == WorkInfo.State.FAILED),
                            enter = fadeIn() + slideInVertically{it},
                            exit = fadeOut() + slideOutVertically{it}
                        ) {
                             ManualAccountEntryStep(
                                accountName = uiState.manualAccountName,
                                balance = uiState.manualAccountBalance,
                                accountLast4 = uiState.manualAccountLast4,
                                selectedCurrency = uiState.selectedCurrency,
                                onUpdateName = onUpdateManualAccountName,
                                onUpdateBalance = onUpdateManualAccountBalance,
                                onUpdateLast4 = onUpdateManualAccountLast4,
                                onSelectCurrency = { onToggleCurrencyBottomSheet(true) }
                            )
                        }
                        BlurredAnimatedVisibility(
                            visible = !(uiState.hasSkippedPermission || (uiState.accounts.isEmpty() && uiState.scanWorkInfo?.state == WorkInfo.State.FAILED)),
                            enter = fadeIn() + slideInVertically{it},
                            exit = fadeOut() + slideOutVertically{it}
                        ) {
                            SyncStep(
                                isScanning = uiState.isScanning,
                                workInfo = uiState.scanWorkInfo,
                                accounts = uiState.accounts,
                                mainAccountKey = uiState.mainAccountKey,
                                selectedForMerge = uiState.selectedAccountsForMerge,
                                onSetMain = onSetMainAccount,
                                onStartScan = onStartScan,
                                onSkip = onSkipSync,
                                onToggleMerge = onToggleAccountSelectionForMerge,
                                onMerge = onMergeSelectedAccounts,
                                isLoading = uiState.isLoading
                            )
                        }
                    }
                    5 -> ProfileStep(
                            state = uiState.profileState,
                            onNameChange = onNameChange,
                            onProfileImageChange = onProfileImageChange,
                            onBackgroundColorChange = onBackgroundColorChange
                        )
                }
            }
        }

        if (uiState.showCurrencyBottomSheet) {
            CurrencyBottomSheet(
                selectedCurrency = uiState.selectedCurrency,
                onCurrencySelected = {
                    onUpdateSelectedCurrency(it)
                    onToggleCurrencyBottomSheet(false)
                },
                onDismiss = { onToggleCurrencyBottomSheet(false) }
            )
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        IphoneFrame(
            modifier = Modifier
                .padding(top = Spacing.md)
                .align(Alignment.BottomCenter)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "logoAnimation")
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathingScale"
            )

            val rippleScale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rippleScale"
            )

            val rippleAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rippleAlpha"
            )


            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                //Banner image
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val primary = MaterialTheme.colorScheme.primary
                    val tertiary = MaterialTheme.colorScheme.tertiary
                    val alternatingIcons = remember {
                        listOf(
                            IconResource.VectorIcon(Iconax.Cashiro, primary),
                            IconResource.VectorIcon(Iconax.CashiroOutline, tertiary)
                        )
                    }

                    TiledScrollingIconBackground(
                        iconResources = alternatingIcons,
                        opacity = 0.05f,
                        iconSize = 56.dp
                    )
                }
                // Ripple effect
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(rippleScale)
                        .graphicsLayer { alpha = rippleAlpha }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scale)
                        .background(Color(0xFF1f1f1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cashiro),
                        contentDescription = stringResource(R.string.app_logo),
                        modifier = Modifier
                            .size(90.dp)
                            .scale(scale),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            BlurredAnimatedVisibility(visible = true) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            BlurredAnimatedVisibility(visible = true) {
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileStep(
    state: EditProfileState,
    onNameChange: (String) -> Unit,
    onProfileImageChange: (Uri?) -> Unit,
    onBackgroundColorChange: (Color) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Spacing.lg)
            .overScrollVertical()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            text = stringResource(R.string.set_up_your_profile),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )

        ProfileCardPreview(
            profileImageUri = state.editedProfileImageUri,
            backgroundColor = state.editedProfileBackgroundColor,
            bannerImageUri = state.editedBannerImageUri,
            modifier = Modifier.padding(vertical = Spacing.md,horizontal = Spacing.md)
        )

        TextField(
            value = state.editedUserName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.what_should_we_call_you)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(Dimensions.Radius.md),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            PresetAvatarSelection(
                selectedUri = state.editedProfileImageUri,
                onSelect = onProfileImageChange
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(Dimensions.Radius.md)
                )
        ) {
            ColorPickerContent(
                initialColor = state.editedProfileBackgroundColor.toArgb(),
                onColorChanged = { onBackgroundColorChange(Color(it)) }
            )
        }
    }
}

@Composable
fun PermissionsStep(
    isNotification: Boolean,
    onSkip: () -> Unit
) {
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationTriggered = true
    }

    // Common sliding icon animation
    val progress by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "commonIcon"
    )

    // Derived values for the common icon
    // Starts at center (0), slides to top (-205dp), scales down 1.2 -> 0.8
    val iconTranslateY = lerp(0f, -190f, progress)
    val iconScale = lerp(1.2f, 0.8f, progress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        IphoneFrame(
            modifier = Modifier
                .padding(top = Spacing.md)
                .align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                // Background Animations
                if (isNotification) {
                    NotificationHeroAnimation(animationTriggered)
                } else {
                    PermissionsBackgroundAnimation(isNotification)
                }

                // Common Sliding Icon (Higher Z-Index)
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            translationY = iconTranslateY.dp.toPx()
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .zIndex(2f),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = if (isNotification) Iconax.Notification else Iconax.Messages,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            Text(
                text = if (isNotification) stringResource(R.string.stay_updated) else stringResource(R.string.automatic_tracking),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isNotification) 
                    stringResource(R.string.allow_notifications_desc)
                    else stringResource(R.string.automatic_tracking_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isNotification) {
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Text(stringResource(R.string.skip_for_now), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SyncStep(
    isScanning: Boolean,
    workInfo: WorkInfo?,
    accounts: List<AccountBalanceEntity>,
    mainAccountKey: String?,
    selectedForMerge: Set<String>,
    onSetMain: (String, String) -> Unit,
    onStartScan: () -> Unit,
    onSkip: () -> Unit,
    onToggleMerge: (String) -> Unit,
    onMerge: (AccountBalanceEntity) -> Unit,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {

        BlurredAnimatedVisibility(
            visible = accounts.isNotEmpty() && !isScanning,
            enter = fadeIn() + slideInVertically{it},
            exit = fadeOut() + scaleOut()
        ) {
            // Full screen accounts list
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.verify_accounts),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                AccountSetupStep(
                    accounts = accounts,
                    mainAccountKey = mainAccountKey,
                    selectedForMerge = selectedForMerge,
                    onSetMain = onSetMain,
                    onToggleMerge = onToggleMerge,
                    onMerge = onMerge,
                    isLoading = isLoading
                )
            }
        }

        BlurredAnimatedVisibility(
            visible = !(accounts.isNotEmpty() && !isScanning),
            enter = fadeIn() + slideInVertically{it},
            exit = fadeOut() + scaleOut()
        ) {
            IphoneFrame(
                modifier = Modifier
                    .padding(top = Spacing.md)
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        SmsParsingProgressIndicator(
                            workInfo = workInfo,
                            modifier = Modifier.size(150.dp)
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(24.dp).fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            if (isScanning || accounts.isEmpty()) {
                Text(
                    text = if (isScanning) stringResource(R.string.scanning_messages) else stringResource(R.string.ready_to_sync),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = if (isScanning) stringResource(R.string.this_will_only_take_a_moment)
                       else if (accounts.isNotEmpty()) stringResource(R.string.found_accounts_select_primary_format, accounts.size)
                       else stringResource(R.string.start_scanning_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isScanning && accounts.isEmpty()) {
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(bottom = Spacing.sm)
                ) {
                    Text(stringResource(R.string.skip_for_now), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AccountSetupStep(
    accounts: List<AccountBalanceEntity>,
    mainAccountKey: String?,
    selectedForMerge: Set<String>,
    onSetMain: (String, String) -> Unit,
    onToggleMerge: (String) -> Unit,
    onMerge: (AccountBalanceEntity) -> Unit,
    isLoading: Boolean
) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical().clip(RoundedCornerShape(28.dp)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(accounts) { account ->
                val key = "${account.bankName}_${account.accountLast4}"
                val isMain = mainAccountKey == key
                val isSelectedForMerge = selectedForMerge.contains(key)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    border = if (isMain) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    onClick = { onSetMain(account.bankName, account.accountLast4) }
                ) {
                    Box {
                        AccountCard(
                            account = account,
                            showMoreOptions = false,
                            onClick = { onSetMain(account.bankName, account.accountLast4) }
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMain) {
                                Badge(containerColor = Color(0xFFFFD700).copy(alpha = 0.15f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(Spacing.xs))
                                        Text(
                                            text = stringResource(R.string.main),
                                            color = Color(0xFFFFD700),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(Spacing.sm))
                            }

                            IconButton(onClick = { onToggleMerge(key) }) {
                                Icon(
                                    imageVector = if (isSelectedForMerge) Iconax.HierarchySquare3 else Iconax.HierarchySquare3,
                                    contentDescription = stringResource(R.string.merge),
                                    tint = if (isSelectedForMerge) MaterialTheme.colorScheme.primary 
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedForMerge.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Button(
                        onClick = { 
                            // Merge into the main account if it's part of selection, otherwise the first selected account
                            val targetKey = if (selectedForMerge.contains(mainAccountKey)) {
                                mainAccountKey
                            } else {
                                selectedForMerge.firstOrNull()
                            }
                            
                            val targetAccount = targetKey?.let { key ->
                                accounts.find { "${it.bankName}_${it.accountLast4}" == key }
                            }
                            targetAccount?.let { onMerge(it) }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Iconax.HierarchySquare3, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.merge_selected_accounts))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(Spacing.md))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Iconax.Information,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.main_account_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item{
                Spacer(modifier = Modifier.height(Spacing.xxl))
            }
        }

}

@Composable
fun ManualAccountEntryStep(
    accountName: String,
    balance: String,
    accountLast4: String,
    selectedCurrency: String,
    onUpdateName: (String) -> Unit,
    onUpdateBalance: (String) -> Unit,
    onUpdateLast4: (String) -> Unit,
    onSelectCurrency: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(R.string.add_your_main_account),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.enter_primary_account_details),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        TextField(
            value = accountName,
            onValueChange = onUpdateName,
            label = { Text(stringResource(R.string.bank_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.Radius.md),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = { Icon(Iconax.Edit2, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            TextField(
                value = balance,
                onValueChange = onUpdateBalance,
                label = { Text(stringResource(R.string.balance)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Dimensions.Radius.md),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = { Icon(Iconax.Wallet3, contentDescription = null) },
                singleLine = true
            )

            Card(
                onClick = onSelectCurrency,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(Dimensions.Radius.md),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedCurrency,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        TextField(
            value = accountLast4,
            onValueChange = onUpdateLast4,
            label = { Text(stringResource(R.string.last_4_digits_hint)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.last_4_digits_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(Dimensions.Radius.md),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StageProgressIndicator(
    currentStage: Int,
    totalStages: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = currentStage.toFloat() / totalStages.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    LinearWavyProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun OnBoardingBottomBar(
    currentStep: Int,
    isScanning: Boolean = false,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    isContinueEnabled: Boolean
) {
    Surface(
        tonalElevation = 3 .dp,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg).navigationBarsPadding(),
            horizontalArrangement = if (currentStep > 1) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 1) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(end = Spacing.md).height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            }

            Button(
                onClick = onContinue,
                enabled = isContinueEnabled,
                modifier = Modifier.height(56.dp).fillMaxWidth(),
                shape = RoundedCornerShape(Dimensions.Radius.md)
            ) {
                Text(
                    text =
                        when (currentStep) {
                            1 -> stringResource(R.string.get_started)
                            2 -> stringResource(R.string.enable_tracking)
                            3 -> stringResource(R.string.stay_informed)
                            4 -> if (isScanning) stringResource(R.string.scanning_ellipsis) else stringResource(R.string.continue_action)
                            5 -> stringResource(R.string.finish_setup)
                            else -> stringResource(R.string.continue_action)
                        }
                )
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

// Previews
@Preview(showBackground = true, name = "1. Welcome")
@Composable
fun OnBoardingWelcomePreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 1),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "2. SMS Permission")
@Composable
fun OnBoardingSmsPermissionPreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 2),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "3. Notification Permission")
@Composable
fun OnBoardingNotificationPermissionPreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 3),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "4. Syncing (Scanning)")
@Composable
fun OnBoardingSyncingPreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 4, isScanning = true),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "4. Account Verification")
@Composable
fun OnBoardingAccountVerificationPreview() {
    val mockAccount = AccountBalanceEntity(
        bankName = "HDFC Bank",
        accountLast4 = "1234",
        balance = BigDecimal("50000.00"),
        currency = "INR",
        timestamp = LocalDateTime.now(),
    )
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(
                currentStep = 4, 
                isScanning = false,
                accounts = listOf(mockAccount)
            ),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "4. Manual Account Entry")
@Composable
fun OnBoardingManualAccountPreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 4, hasSkippedPermission = true),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

@Preview(showBackground = true, name = "5. Profile Setup")
@Composable
fun OnBoardingProfilePreview() {
    CashiroTheme {
        OnBoardingScreenContent(
            uiState = OnBoardingUiState(currentStep = 5),
            onNext = {}, onPrevious = {}, onRequestPermissions = {}, onStartScan = {},
            onSkipSms = {}, onSkipSync = {}, onSaveManualAccount = {}, onSaveProfile = {},
            onNameChange = {}, onProfileImageChange = {}, onBackgroundColorChange = {},
            onSetMainAccount = { _, _ -> }, onUpdateManualAccountName = {},
            onUpdateManualAccountBalance = {}, onUpdateManualAccountLast4 = {},
            onToggleCurrencyBottomSheet = {}, onUpdateSelectedCurrency = {},
            onToggleAccountSelectionForMerge = {}, onMergeSelectedAccounts = {}
        )
    }
}

data class MessageData(val sender: String, val text: String, val isA: Boolean)

@Composable
fun PermissionsBackgroundAnimation(isNotification: Boolean) {
    val messages = remember(isNotification) {
        if (isNotification) {
            listOf(
                MessageData("A", "Electric bill due: ₹2,450", true), // These could use string resources inside remember if context is passed, but hard to do elegantly without Composable context. So we can map them inside the UI loop or pass from strings.
                MessageData("B", "Transaction of ₹500 at Starbucks", false),
                MessageData("A", "Reminder: Credit card due tomorrow", true),
                MessageData("B", "Cashback of ₹50 credited", false)
            )
        } else {
            listOf(
                MessageData("A", "HDFC: ₹1,500 spent at Amazon", true),
                MessageData("B", "SBI: ₹10,000 Salary Credited", false),
                MessageData("A", "ICICI: Low Balance Alert: ₹450", true),
                MessageData("B", "Axis: ₹200 spent at Zomato", false)
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.Center
    ) {
        messages.forEachIndexed { index, message ->
            AnimatedMessageBubble(message, index * 200)
            if (index < messages.size - 1) {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    }
}

@Composable
fun AnimatedMessageBubble(message: MessageData, delay: Int) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 600, delayMillis = delay)
        ) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 600, delayMillis = delay)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (message.isA) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            MessageBubble(message, useSkeleton = true)
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageData,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    useSkeleton: Boolean = false,
    showLogo: Boolean = false
) {
    val backgroundColor = if (message.isA) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    }
    
    val contentColor = if (message.isA) {
        MaterialTheme.colorScheme.onPrimary 
    } else {
        MaterialTheme.colorScheme.onTertiary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLarge) 20.dp else 16.dp))
            .background(backgroundColor)
            .padding(
                horizontal = if (isLarge) 20.dp else 16.dp, 
                vertical = if (isLarge) 14.dp else 10.dp
            )
            .widthIn(max = if (isLarge) 300.dp else 220.dp), // wider
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLogo) {
            Box(
                modifier = Modifier
                    .size(if (isLarge) 36.dp else 28.dp)
                    .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.15f), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cashiro),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(contentColor)
                )
            }
            Spacer(modifier = Modifier.width(if (isLarge) 14.dp else 10.dp))
        }

        Column(modifier = Modifier.weight(1f, fill = false)) {
            if (useSkeleton) {
                // Line-based text pattern
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (message.isA) 0.6f else 0.8f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (message.isA) 0.8f else 0.6f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.4f))
                )
            } else {
                Text(
                    text = message.sender,
                    style = if (isLarge) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(if (isLarge) 4.dp else 2.dp))
                Text(
                    text = message.text,
                    style = if (isLarge) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    lineHeight = if (isLarge) 20.sp else 16.sp
                )
            }
        }
    }
}

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

@Composable
fun NotificationHeroAnimation(animationTriggered: Boolean) {
    var showBubble by remember { mutableStateOf(false) }
    LaunchedEffect(animationTriggered) {
        if (animationTriggered) {
            delay(300) // Start slightly after the main icon moves up
            showBubble = true
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (showBubble) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "heroSlide"
    )

    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    
    LaunchedEffect(showBubble) {
        if (showBubble) {
            delay(1200) // Wait for the slide-in to complete
            // Vibrate a few times then stop
            repeat(3) {
                shakeX.animateTo(1.5f, tween(40, easing = LinearEasing))
                shakeX.animateTo(-1.5f, tween(40, easing = LinearEasing))
                shakeY.animateTo(1f, tween(30, easing = LinearEasing))
                shakeY.animateTo(-1f, tween(30, easing = LinearEasing))
            }
            shakeX.animateTo(0f, tween(40))
            shakeY.animateTo(0f, tween(40))
        }
    }

    // Derived values for the hero bubble
    // Slides from notch (roughly -260dp) to center (0)
    // Scales from 0 to 1
    val translateY = lerp(-260f, 0f, progress)
    val scale = lerp(0f, 1f, progress)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .graphicsLayer {
                    this.scaleX = scale
                    this.scaleY = scale
                    // Use the values from Animatable
                    this.translationY = translateY.dp.toPx() + shakeY.value.dp.toPx()
                    this.translationX = shakeX.value.dp.toPx()
                }
                .zIndex(1f) // Just below the main sliding icon
        ) {
            MessageBubble(
                message = MessageData(stringResource(R.string.notification), stringResource(R.string.mock_msg_salary_credited), false),
                isLarge = true,
                useSkeleton = false,
                showLogo = true
            )
        }
    }
}

