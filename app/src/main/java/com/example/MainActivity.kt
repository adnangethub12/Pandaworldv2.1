package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Locale

import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val avatarEmoji: String = "🐼",
    val status: String = "Hey there! I am using PandaWorld.",
    val passcode: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null
)

fun playNotificationSound(context: Context) {
    try {
        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, notificationUri)
        ringtone?.play()
    } catch (_: Exception) {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }
}

fun formatTimeOrDate(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val date = timestamp.toDate()
    val now = java.util.Date()
    val diffMs = now.time - date.time
    val diffHours = diffMs / (1000 * 60 * 60)
    return if (diffHours < 24) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(date)
    } else if (diffHours < 48) {
        "Yesterday"
    } else {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(date)
    }
}

fun formatLastSeenStatus(isOnline: Boolean, lastSeen: Timestamp?): String {
    if (isOnline) return "Online"
    if (lastSeen == null) return "Offline"
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return "Last seen at ${sdf.format(lastSeen.toDate())}"
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "",
    val status: String = "sent", // "sent", "delivered", "seen"
    val timestamp: Timestamp? = null
)

enum class Screen {
    SPLASH,
    LOGIN,
    USER_LIST,
    CHAT
}

object UserContactsManager {
    private const val PREF_NAME = "pandaworld_contacts"

    fun getConnectedUserIds(context: Context, currentUserId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("contacts_$currentUserId", emptySet()) ?: emptySet()
    }

    fun addConnectedUserId(context: Context, currentUserId: String, targetUserId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getConnectedUserIds(context, currentUserId).toMutableSet()
        current.add(targetUserId)
        prefs.edit().putStringSet("contacts_$currentUserId", current).apply()
    }

    fun removeConnectedUserId(context: Context, currentUserId: String, targetUserId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getConnectedUserIds(context, currentUserId).toMutableSet()
        current.remove(targetUserId)
        prefs.edit().putStringSet("contacts_$currentUserId", current).apply()
    }
}

object UserSessionManager {
    private const val PREF_NAME = "pandaworld_session"
    private const val KEY_USER_ID = "key_user_id"
    private const val KEY_NAME = "key_name"
    private const val KEY_EMAIL = "key_email"
    private const val KEY_AVATAR = "key_avatar"
    private const val KEY_STATUS = "key_status"
    private const val KEY_PASSCODE = "key_passcode"

    fun saveUser(context: Context, user: UserProfile) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, user.userId)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_AVATAR, user.avatarEmoji)
            .putString(KEY_STATUS, user.status)
            .putString(KEY_PASSCODE, user.passcode)
            .apply()
    }

    fun getUser(context: Context): UserProfile? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_USER_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, uid) ?: uid
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val avatar = prefs.getString(KEY_AVATAR, "🐼") ?: "🐼"
        val status = prefs.getString(KEY_STATUS, "Hey there! I am using PandaWorld.") ?: ""
        val passcode = prefs.getString(KEY_PASSCODE, "") ?: ""
        return UserProfile(userId = uid, name = name, email = email, avatarEmoji = avatar, status = status, passcode = passcode)
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    val options = FirebaseOptions.Builder()
                        .setProjectId("pandaworld-6ba55")
                        .setApplicationId("1:502334935634:android:deed1bbf951ce045af058b")
                        .setApiKey("AIzaSyBVqekjn6JSTzhnTu0f8ev6R9qC3a8jzVM")
                        .setStorageBucket("pandaworld-6ba55.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PandaWorldApp()
            }
        }
    }
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
        )
        kotlinx.coroutines.delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Welcome to PandaWorld",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp)
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(380.dp)
                    .graphicsLayer(alpha = alpha.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.panda),
                    contentDescription = "Panda Welcome Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Loading safe 1-on-1 chats...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PandaWorldApp() {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    val savedUser = remember { UserSessionManager.getUser(context) }
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    var currentUser by remember { mutableStateOf(savedUser) }
    var chatPartner by remember { mutableStateOf<UserProfile?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                Screen.SPLASH -> SplashScreen(
                    onSplashFinished = {
                        currentScreen = if (currentUser != null) Screen.USER_LIST else Screen.LOGIN
                    }
                )

                Screen.LOGIN -> AuthScreen(
                    db = db,
                    onLoginSuccess = { user ->
                        currentUser = user
                        UserSessionManager.saveUser(context, user)
                        currentScreen = Screen.USER_LIST
                    }
                )

                Screen.USER_LIST -> UserListScreen(
                    currentUser = currentUser!!,
                    onUpdateCurrentUser = { updated ->
                        currentUser = updated
                        UserSessionManager.saveUser(context, updated)
                    },
                    db = db,
                    onSelectUserToChat = { selectedUser ->
                        chatPartner = selectedUser
                        currentScreen = Screen.CHAT
                    },
                    onLogout = {
                        UserSessionManager.clearSession(context)
                        currentUser = null
                        currentScreen = Screen.LOGIN
                    }
                )

                Screen.CHAT -> ChatScreen(
                    currentUser = currentUser!!,
                    chatPartner = chatPartner!!,
                    db = db,
                    onBack = {
                        currentScreen = Screen.USER_LIST
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    db: FirebaseFirestore,
    onLoginSuccess: (UserProfile) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }

    var userIdInput by remember { mutableStateOf("") }
    var passcodeInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🐼") }
    var isPasscodeVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val avatarOptions = listOf("🐼", "🦊", "🐯", "🦁", "🐻", "🐰", "🐨", "🐸")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Login to PandaWorld", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = selectedEmoji, fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Login to PandaWorld",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Enter your User ID and Passcode to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector Tabs
            TabRow(
                selectedTabIndex = if (isRegisterMode) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = !isRegisterMode,
                    onClick = {
                        isRegisterMode = false
                        errorMessage = null
                    },
                    text = { Text("Login", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = isRegisterMode,
                    onClick = {
                        errorMessage = "You are not allowed"
                    },
                    text = { Text("Register", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isRegisterMode) {
                // Choose Avatar
                Text(
                    text = "Choose Your Avatar:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    avatarOptions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedEmoji == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = userIdInput,
                onValueChange = {
                    userIdInput = it.lowercase().replace(" ", "_")
                    errorMessage = null
                },
                label = { Text("User ID (e.g., adnan_99)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (isRegisterMode) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Adnan Khan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = passcodeInput,
                onValueChange = {
                    passcodeInput = it
                    errorMessage = null
                },
                label = { Text("Secret Passcode / PIN") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasscodeVisible = !isPasscodeVisible }) {
                        Icon(
                            imageVector = if (isPasscodeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Passcode Visibility"
                        )
                    }
                },
                visualTransformation = if (isPasscodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val uid = userIdInput.trim()
                    val passcode = passcodeInput.trim()

                    if (uid.isBlank()) {
                        errorMessage = "Please enter a valid User ID"
                        return@Button
                    }
                    if (passcode.isBlank()) {
                        errorMessage = "Please enter your Secret Passcode"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    if (isRegisterMode) {
                        // Registration Logic
                        val displayName = nameInput.ifBlank { uid.replaceFirstChar { it.uppercase() } }
                        val userRef = db.collection("users").document(uid)

                        userRef.get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                isLoading = false
                                errorMessage = "User ID '@$uid' is already taken. Please login or choose a different ID."
                            } else {
                                val newProfile = UserProfile(
                                    userId = uid,
                                    name = displayName,
                                    email = "$uid@pandaworld.app",
                                    avatarEmoji = selectedEmoji,
                                    status = "Hey there! I am using PandaWorld.",
                                    passcode = passcode
                                )

                                userRef.set(
                                    mapOf(
                                        "userId" to newProfile.userId,
                                        "name" to newProfile.name,
                                        "email" to newProfile.email,
                                        "avatarEmoji" to newProfile.avatarEmoji,
                                        "status" to newProfile.status,
                                        "passcode" to newProfile.passcode,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                ).addOnSuccessListener {
                                    isLoading = false
                                    onLoginSuccess(newProfile)
                                }.addOnFailureListener { err ->
                                    isLoading = false
                                    // Fallback if firestore rules prevent writing directly
                                    onLoginSuccess(newProfile)
                                }
                            }
                        }.addOnFailureListener {
                            isLoading = false
                            // If Firestore permission denied, still allow offline/local register
                            val newProfile = UserProfile(
                                userId = uid,
                                name = displayName,
                                email = "$uid@pandaworld.app",
                                avatarEmoji = selectedEmoji,
                                passcode = passcode
                            )
                            onLoginSuccess(newProfile)
                        }
                    } else {
                        // Login Logic
                        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                            isLoading = false
                            if (doc.exists()) {
                                val storedPasscode = doc.getString("passcode") ?: ""
                                if (storedPasscode.isEmpty()) {
                                    // Set & save passcode for existing user who didn't have one set in Firebase yet
                                    db.collection("users").document(uid).update("passcode", passcode)
                                    val userProfile = UserProfile(
                                        userId = uid,
                                        name = doc.getString("name") ?: uid,
                                        email = doc.getString("email") ?: "$uid@pandaworld.app",
                                        avatarEmoji = doc.getString("avatarEmoji") ?: "🐼",
                                        status = doc.getString("status") ?: "PandaWorld Member",
                                        passcode = passcode
                                    )
                                    onLoginSuccess(userProfile)
                                } else if (storedPasscode == passcode) {
                                    val userProfile = UserProfile(
                                        userId = uid,
                                        name = doc.getString("name") ?: uid,
                                        email = doc.getString("email") ?: "$uid@pandaworld.app",
                                        avatarEmoji = doc.getString("avatarEmoji") ?: "🐼",
                                        status = doc.getString("status") ?: "PandaWorld Member",
                                        passcode = passcode
                                    )
                                    onLoginSuccess(userProfile)
                                } else {
                                    errorMessage = "Incorrect Passcode for User ID '@$uid'. Please try again."
                                }
                            } else {
                                errorMessage = "User ID '@$uid' not found in Firebase. Please click Register tab to create account."
                            }
                        }.addOnFailureListener {
                            isLoading = false
                            // Local fallback login
                            val userProfile = UserProfile(
                                userId = uid,
                                name = uid.replaceFirstChar { it.uppercase() },
                                email = "$uid@pandaworld.app",
                                avatarEmoji = selectedEmoji,
                                passcode = passcode
                            )
                            onLoginSuccess(userProfile)
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isRegisterMode) "Register & Start Chatting" else "Login to PandaWorld",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    currentUser: UserProfile,
    onUpdateCurrentUser: (UserProfile) -> Unit,
    db: FirebaseFirestore,
    onSelectUserToChat: (UserProfile) -> Unit,
    onLogout: () -> Unit
) {
    var firestoreError by remember { mutableStateOf<String?>(null) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var newPasscodeInput by remember { mutableStateOf(currentUser.passcode) }
    var passcodeUpdateMsg by remember { mutableStateOf<String?>(null) }
    var latestNotificationMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Connected user contacts
    var connectedUserIds by remember {
        mutableStateOf(UserContactsManager.getConnectedUserIds(context, currentUser.userId))
    }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var addContactInput by remember { mutableStateOf("") }
    var addContactError by remember { mutableStateOf<String?>(null) }
    var isAddingContact by remember { mutableStateOf(false) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(currentUser.name) }
    var editAvatarEmoji by remember { mutableStateOf(currentUser.avatarEmoji) }
    var isSavingProfile by remember { mutableStateOf(false) }

    // Sync connected contacts from Firestore
    LaunchedEffect(currentUser.userId) {
        db.collection("users").document(currentUser.userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val contactsList = snapshot.get("connected_contacts") as? List<String>
                    if (contactsList != null) {
                        val newSet = connectedUserIds.toMutableSet()
                        newSet.addAll(contactsList)
                        connectedUserIds = newSet
                        contactsList.forEach { uid ->
                            UserContactsManager.addConnectedUserId(context, currentUser.userId, uid)
                        }
                    }
                }
            }
    }

    // Update current user's online status
    DisposableEffect(currentUser.userId) {
        db.collection("users").document(currentUser.userId).update(
            mapOf(
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp()
            )
        )
        onDispose {
            db.collection("users").document(currentUser.userId).update(
                mapOf(
                    "isOnline" to false,
                    "lastSeen" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // Add Contact Dialog (+ button)
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddContactDialog = false
                addContactError = null
                addContactInput = ""
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Connect User by Username", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the exact User ID (Username) of the person you want to chat with:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = addContactInput,
                        onValueChange = {
                            addContactInput = it
                            addContactError = null
                        },
                        label = { Text("User ID (Username)") },
                        placeholder = { Text("e.g. alex") },
                        singleLine = true,
                        leadingIcon = { Text("@", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addContactError != null) {
                        Text(
                            text = addContactError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = addContactInput.isNotBlank() && !isAddingContact,
                    onClick = {
                        val targetUid = addContactInput.trim().lowercase()
                        if (targetUid == currentUser.userId.lowercase()) {
                            addContactError = "You cannot add yourself as a contact."
                            return@Button
                        }

                        isAddingContact = true
                        addContactError = null

                        db.collection("users").document(targetUid).get()
                            .addOnSuccessListener { doc ->
                                isAddingContact = false
                                if (doc.exists()) {
                                    val foundUid = doc.getString("userId") ?: targetUid
                                    val newSet = connectedUserIds.toMutableSet()
                                    newSet.add(foundUid)
                                    connectedUserIds = newSet
                                    UserContactsManager.addConnectedUserId(context, currentUser.userId, foundUid)

                                    db.collection("users").document(currentUser.userId)
                                        .update("connected_contacts", FieldValue.arrayUnion(foundUid))
                                    db.collection("users").document(foundUid)
                                        .update("connected_contacts", FieldValue.arrayUnion(currentUser.userId))

                                    showAddContactDialog = false
                                    addContactInput = ""
                                } else {
                                    addContactError = "User ID '@$targetUid' not found in PandaWorld."
                                }
                            }
                            .addOnFailureListener {
                                isAddingContact = false
                                addContactError = "Network error searching user ID."
                            }
                    }
                ) {
                    if (isAddingContact) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Connect")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddContactDialog = false
                    addContactError = null
                    addContactInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Avatar:", style = MaterialTheme.typography.labelMedium)
                    val avatarOptions = listOf("🐼", "🦊", "🐯", "🦁", "🐻", "🐰", "🐨", "🐸")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(avatarOptions) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (editAvatarEmoji == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable { editAvatarEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = "@${currentUser.userId}",
                        onValueChange = {},
                        enabled = false,
                        label = { Text("User ID (Username - Cannot be changed)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = editNameInput.isNotBlank() && !isSavingProfile,
                    onClick = {
                        isSavingProfile = true
                        val updatedName = editNameInput.trim()
                        val updatedUser = currentUser.copy(name = updatedName, avatarEmoji = editAvatarEmoji)

                        db.collection("users").document(currentUser.userId)
                            .update(mapOf("name" to updatedName, "avatarEmoji" to editAvatarEmoji))
                            .addOnCompleteListener {
                                isSavingProfile = false
                                onUpdateCurrentUser(updatedUser)
                                showEditProfileDialog = false
                            }
                    }
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Save Profile")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = { Text("Update Passcode / PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set a secret Passcode for '@${currentUser.userId}' to secure your account login.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPasscodeInput,
                        onValueChange = { newPasscodeInput = it },
                        label = { Text("New Passcode") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                    if (passcodeUpdateMsg != null) {
                        Text(
                            text = passcodeUpdateMsg!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPasscodeInput.isNotBlank()) {
                        val updatedPasscode = newPasscodeInput.trim()
                        db.collection("users").document(currentUser.userId)
                            .update("passcode", updatedPasscode)
                        val updatedUser = currentUser.copy(passcode = updatedPasscode)
                        onUpdateCurrentUser(updatedUser)
                        passcodeUpdateMsg = "Passcode updated successfully!"
                        showPasscodeDialog = false
                    }
                }) {
                    Text("Save Passcode")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fetch registered users from Firestore 'users' collection
    val usersFlow: Flow<List<UserProfile>> = remember {
        callbackFlow {
            val query = db.collection("users")
            val listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    firestoreError = error.message
                    return@addSnapshotListener
                }
                firestoreError = null
                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("userId") ?: doc.id
                        val name = doc.getString("name") ?: uid
                        val email = doc.getString("email") ?: ""
                        val avatar = doc.getString("avatarEmoji") ?: "🐼"
                        val status = doc.getString("status") ?: "PandaWorld Member"
                        val passcode = doc.getString("passcode") ?: ""
                        val isOnline = doc.getBoolean("isOnline") ?: false
                        val lastSeen = doc.getTimestamp("lastSeen")
                        UserProfile(
                            userId = uid,
                            name = name,
                            email = email,
                            avatarEmoji = avatar,
                            status = status,
                            passcode = passcode,
                            isOnline = isOnline,
                            lastSeen = lastSeen
                        )
                    }
                    trySend(users)
                }
            }
            awaitClose { listener.remove() }
        }
    }

    val usersState = usersFlow.collectAsState(initial = emptyList())
    val realFirebaseUsers = usersState.value
        .distinctBy { it.userId }
        .filter { it.userId != currentUser.userId && (it.userId.lowercase() in connectedUserIds.map { id -> id.lowercase() }) }

    // Map to hold last message for each user conversation
    val lastMessagesMap = remember { mutableStateMapOf<String, ChatMessage>() }

    // Listen to last messages for each chat user
    LaunchedEffect(realFirebaseUsers.map { it.userId }) {
        realFirebaseUsers.forEach { partner ->
            val chatRoomId = listOf(currentUser.userId, partner.userId).sorted().joinToString("_")
            db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && !snapshot.isEmpty) {
                        val doc = snapshot.documents.first()
                        val msg = ChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            receiverId = doc.getString("receiverId") ?: "",
                            message = doc.getString("message") ?: "",
                            mediaUrl = doc.getString("mediaUrl") ?: "",
                            mediaType = doc.getString("mediaType") ?: "",
                            status = doc.getString("status") ?: "sent",
                            timestamp = doc.getTimestamp("timestamp")
                        )

                        val prevMsg = lastMessagesMap[partner.userId]
                        if (prevMsg != null && prevMsg.id != msg.id && msg.senderId != currentUser.userId) {
                            playNotificationSound(context)
                            latestNotificationMsg = "New message from ${partner.name}: ${msg.message.ifEmpty { "📷 Photo" }}"
                        }
                        lastMessagesMap[partner.userId] = msg
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PandaWorld Chats", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Contact"
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Logged-in Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = currentUser.avatarEmoji, fontSize = 26.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                text = "@${currentUser.userId} • Online",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            editNameInput = currentUser.name
                            editAvatarEmoji = currentUser.avatarEmoji
                            showEditProfileDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            newPasscodeInput = currentUser.passcode
                            passcodeUpdateMsg = null
                            showPasscodeDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Passcode Settings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Notification alert banner if new message arrives
            if (latestNotificationMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { latestNotificationMsg = null },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔔", fontSize = 18.sp)
                        Text(
                            text = latestNotificationMsg!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { latestNotificationMsg = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (realFirebaseUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "🐼", fontSize = 48.sp)
                            Text(
                                text = "No Chats Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap the + button to enter a User ID (Username) and start chatting privately!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showAddContactDialog = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add User")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(realFirebaseUsers, key = { it.userId }) { user ->
                        val lastMsg = lastMessagesMap[user.userId]
                        WhatsAppChatItem(
                            user = user,
                            lastMessage = lastMsg,
                            onClick = { onSelectUserToChat(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppChatItem(
    user: UserProfile,
    lastMessage: ChatMessage?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasUnread = lastMessage != null && lastMessage.senderId == user.userId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (hasUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar with Online Status Indicator
            Box(modifier = Modifier.size(50.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = user.avatarEmoji, fontSize = 28.sp)
                }
                // Online Dot Badge
                if (user.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            // Chat User Details + Last Message Preview
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hasUnread) FontWeight.ExtraBold else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (lastMessage?.timestamp != null) {
                        Text(
                            text = formatTimeOrDate(lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewText = when {
                        lastMessage == null -> "Tap to start conversation..."
                        lastMessage.mediaUrl.isNotEmpty() -> "📷 Photo ${if (lastMessage.message.isNotEmpty()) "• ${lastMessage.message}" else ""}"
                        else -> lastMessage.message
                    }

                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (hasUnread) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUser: UserProfile,
    chatPartner: UserProfile,
    db: FirebaseFirestore,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<String?>(null) }
    var selectedMediaType by remember { mutableStateOf("IMAGE") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showPresetMediaDialog by remember { mutableStateOf(false) }
    var previewMediaUrl by remember { mutableStateOf<String?>(null) }

    var firestoreError by remember { mutableStateOf<String?>(null) }
    var localFallbackMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var partnerLiveProfile by remember { mutableStateOf(chatPartner) }

    val context = LocalContext.current

    // Listen to real-time presence & status of chat partner
    DisposableEffect(chatPartner.userId) {
        val listener = db.collection("users").document(chatPartner.userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastSeen = snapshot.getTimestamp("lastSeen")
                    partnerLiveProfile = partnerLiveProfile.copy(
                        isOnline = isOnline,
                        lastSeen = lastSeen
                    )
                }
            }
        onDispose { listener.remove() }
    }

    // Media Gallery Launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri.toString()
            selectedMediaType = "IMAGE"
        }
    }

    // Dynamic 1-on-1 Chat Room ID: sorted(user1, user2).joinToString("_")
    val chatRoomId = remember(currentUser.userId, chatPartner.userId) {
        listOf(currentUser.userId, chatPartner.userId).sorted().joinToString("_")
    }

    val messagesFlow: Flow<List<ChatMessage>> = remember(chatRoomId) {
        callbackFlow {
            val query = db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    firestoreError = error.message ?: "Firestore permission notice"
                    return@addSnapshotListener
                }
                firestoreError = null
                if (snapshot != null) {
                    val messages = snapshot.documents.map { doc ->
                        val sender = doc.getString("senderId") ?: ""
                        val status = doc.getString("status") ?: "sent"

                        // Auto mark incoming message as 'seen' while viewing chat
                        if (sender == chatPartner.userId && status != "seen") {
                            doc.reference.update("status", "seen")
                        }

                        ChatMessage(
                            id = doc.id,
                            senderId = sender,
                            receiverId = doc.getString("receiverId") ?: "",
                            message = doc.getString("message") ?: "",
                            mediaUrl = doc.getString("mediaUrl") ?: "",
                            mediaType = doc.getString("mediaType") ?: "",
                            status = status,
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    }
                    trySend(messages)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    val remoteMessagesState = messagesFlow.collectAsState(initial = emptyList())
    val messages = if (firestoreError != null) localFallbackMessages else remoteMessagesState.value

    // Notification sound on new incoming message inside ChatScreen
    var previousMessageCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages.size) {
        if (messages.size > previousMessageCount && previousMessageCount > 0) {
            val latest = messages.firstOrNull()
            if (latest != null && latest.senderId == chatPartner.userId) {
                playNotificationSound(context)
            }
        }
        previousMessageCount = messages.size
    }

    // Full screen image preview dialog
    if (previewMediaUrl != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { previewMediaUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { previewMediaUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewMediaUrl,
                    contentDescription = "Full Screen Media Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewMediaUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Preset Media Sharing Modal Dialog
    if (showPresetMediaDialog) {
        AlertDialog(
            onDismissRequest = { showPresetMediaDialog = false },
            title = { Text("Share Media & Photos", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showPresetMediaDialog = false
                            mediaPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Photo from Gallery")
                    }

                    HorizontalDivider()

                    Text(
                        text = "Or choose Panda stickers & media:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val presetMedias = listOf(
                        "Panda Sticker" to "https://images.unsplash.com/photo-1564349683136-77e08dba1ef9?w=500",
                        "Cute Red Panda" to "https://images.unsplash.com/photo-1546182990-dffeafbe841d?w=500",
                        "Nature Forest" to "https://images.unsplash.com/photo-1448375240586-882707db888b?w=500",
                        "Sunset Breeze" to "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?w=500",
                        "Coffee Vibe" to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500",
                        "Cute Kitten" to "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=500"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetMedias) { (title, url) ->
                            Card(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clickable {
                                        selectedMediaUri = url
                                        selectedMediaType = "IMAGE"
                                        showPresetMediaDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetMediaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(40.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = partnerLiveProfile.avatarEmoji, fontSize = 22.sp)
                            }
                            if (partnerLiveProfile.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = partnerLiveProfile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (partnerLiveProfile.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                }
                                Text(
                                    text = formatLastSeenStatus(partnerLiveProfile.isOnline, partnerLiveProfile.lastSeen),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (partnerLiveProfile.isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (partnerLiveProfile.isOnline) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages List (Reverse scroll)
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = chatPartner.avatarEmoji, fontSize = 48.sp)
                        Text(
                            text = "No messages yet with ${chatPartner.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Send a message or share a photo to start chatting!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isSender = msg.senderId == currentUser.userId
                        MessageBubble(
                            message = msg,
                            isSender = isSender,
                            onMediaClick = { url -> previewMediaUrl = url }
                        )
                    }
                }
            }

            // Quick Emoji Reaction Bar above input
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickEmojis = listOf("❤️", "👍", "😂", "🔥", "🙌", "🐼", "😍", "✨", "🎉", "💯", "🙏", "🤣")
                items(quickEmojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                messageText += emoji
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }

            // Selected Media Attachment Preview
            if (selectedMediaUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            AsyncImage(
                                model = selectedMediaUri,
                                contentDescription = "Attached Media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Media Attached",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Ready to send with message",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = { selectedMediaUri = null }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Media",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Media Attach Button
                        IconButton(
                            onClick = { showPresetMediaDialog = true },
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Attach Media",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Emoji Picker Toggle Button
                        IconButton(
                            onClick = { showEmojiPicker = !showEmojiPicker },
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (showEmojiPicker) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Emoji Options",
                                tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Message Text Field
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4
                        )

                        // Send Button
                        val canSend = messageText.isNotBlank() || selectedMediaUri != null
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    val textToSend = messageText.trim()
                                    val mediaToSend = selectedMediaUri ?: ""
                                    val typeToSend = if (mediaToSend.isNotEmpty()) selectedMediaType else ""

                                    messageText = ""
                                    selectedMediaUri = null

                                    val initialStatus = if (partnerLiveProfile.isOnline) "delivered" else "sent"
                                    val messageMap = hashMapOf(
                                        "senderId" to currentUser.userId,
                                        "receiverId" to chatPartner.userId,
                                        "message" to textToSend,
                                        "mediaUrl" to mediaToSend,
                                        "mediaType" to typeToSend,
                                        "status" to initialStatus,
                                        "timestamp" to FieldValue.serverTimestamp()
                                    )

                                    val newMsg = ChatMessage(
                                        id = System.currentTimeMillis().toString(),
                                        senderId = currentUser.userId,
                                        receiverId = chatPartner.userId,
                                        message = textToSend,
                                        mediaUrl = mediaToSend,
                                        mediaType = typeToSend,
                                        status = initialStatus,
                                        timestamp = Timestamp.now()
                                    )

                                    localFallbackMessages = listOf(newMsg) + localFallbackMessages

                                    db.collection("chat_rooms")
                                        .document(chatRoomId)
                                        .collection("messages")
                                        .add(messageMap)
                                        .addOnFailureListener { exc ->
                                            firestoreError = exc.message
                                        }
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Expandable Emoji Keyboard Panel
                    AnimatedVisibility(visible = showEmojiPicker) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            var selectedTab by remember { mutableStateOf(0) }
                            val tabs = listOf("Smiley 😊", "Panda 🐼", "Hearts ❤️", "Food 🍕")

                            val emojiCategories = listOf(
                                listOf("😀","😃","😄","😁","😆","😅","😂","🤣","🥲","🥹","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😜","🤪","🤓","😎","🥳","😏","😒","😞","😔","🥺","😢","😭","😮‍💨","😤","😠","😡","🤬","🤯","😳","😱","🤗","🫡","🤔","🫢","🤭","🤫","🔥","✨","💖","🎉","💯"),
                                listOf("🐼","🦊","🐯","🦁","🐻","🐰","🐨","🐸","🐶","🐱","🐒","🦄","🐴","🐮","🐷","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐝","🐛","🦋","🐌","🐞","🐢","🐍","🦎","🐙","🐬","🐳","🐋","🦈","🦭","🐊","🐅","🐆","🦓","🦍","🦧","🐘","🦛","🦏","🐪","🐫","🦒","🦘"),
                                listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","👍","👎","👏","🙌","👐","🤲","🤝","🙏","💪","👈","👉","👆","👇","✌️","🤞","🫡","🎯","🏆","🥇","🥈","🥉","🎉","🎊","🎈","🔥","✨","🌟","💫","⚡","💥"),
                                listOf("🍕","🍔","🍟","🌭","🍿","🍩","🎂","☕","🧋","🥤","🍎","🍓","🥑","🌮","🍣","🍜","🍉","🍇","🍊","🍋","🍌","🍍","🥭","🍳","🥐","🥯","🥖","🧀","🥗","🍦","🍧","🍨","🥧","🧁","🍰","🍫","🍬","🍭","🍻","🥂")
                            )

                            Column(modifier = Modifier.fillMaxSize()) {
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                                        )
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(7),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(emojiCategories[selectedTab]) { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    messageText += emoji
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = emoji, fontSize = 22.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isSender: Boolean,
    onMediaClick: (String) -> Unit = {}
) {
    val bubbleShape = if (isSender) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    val bubbleColor = if (isSender) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val textColor = if (isSender) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val formattedTime = remember(message.timestamp) {
        message.timestamp?.toDate()?.let { date ->
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } ?: "Sending..."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // If message contains shared Media/Photo
                if (message.mediaUrl.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { onMediaClick(message.mediaUrl) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Shared Media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "Tap to view",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (message.message.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Text Content
                if (message.message.isNotEmpty()) {
                    Text(
                        text = message.message,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formattedTime,
                        color = textColor.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (isSender) {
                        val (statusIcon, statusColor) = when (message.status) {
                            "seen" -> "✓✓" to Color(0xFF00E5FF)
                            "delivered" -> "✓✓" to textColor.copy(alpha = 0.85f)
                            else -> "✓" to textColor.copy(alpha = 0.65f)
                        }
                        Text(
                            text = statusIcon,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


