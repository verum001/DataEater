package com.dataeater.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dataeater.app.data.DatabaseLock
import com.dataeater.app.data.LicenceFiles
import com.dataeater.app.ui.theme.DataEaterCard

/**
 * Unlocking a database you have been sent.
 *
 * WHY THIS IS A SCREEN OF ITS OWN
 * -------------------------------
 * Until now this flow was rendered underneath the Search screen's title, which
 * meant the heading said "Search" while the page was asking for a licence. It
 * is its own screen now, with its own title and back button, because it is not
 * a search at all — it is the one thing standing between a customer and the
 * database they paid for.
 *
 * WHY THE TWO STEPS ARE NUMBERED AND SEPARATE
 * -------------------------------------------
 * The old version had two cards that happened to be in order. The steps are now
 * labelled, because this is the part of the app with the most confused
 * behaviour in the wild:
 *
 *   - people send the wrong thing, because there are two long strings and only
 *     one of them is theirs to share
 *   - people stop after step 1 and assume they have been refused
 *
 * So: the card that must be sent says "send this", the card that must be
 * received says "paste what they sent", and each says which step it is. The
 * Request Code also states plainly that it is safe to share, because assuming
 * otherwise is why people hesitate at exactly this point.
 *
 * WHY THE REQUEST CODE MAY BE COPIED AND THE UNLOCK CODE MAY NOT
 * -------------------------------------------------------------
 * The Request Code is a **public key**. It cannot open anything by itself: the
 * matching private key is created inside AndroidKeyStore, and there is no code
 * anywhere in this app that reads it out. So the customer can paste it into any
 * messaging app without hesitation, and that is exactly what they must do.
 *
 * The Unlock Code is the opposite — it carries the content key wrapped for this
 * phone. It is a secret, so this screen has **no** "paste from clipboard"
 * button for it: the clipboard is readable by other apps, and making the user
 * paste deliberately keeps a secret in a place meant for secrets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(
    status: DatabaseLock.Status.Locked,
    requestCode: String?,
    requestCodeError: String?,
    isPreparingRequestCode: Boolean,
    hasCopiedRequestCode: Boolean,
    onPrepareRequestCode: () -> Unit,
    onCopyRequestCode: () -> Unit,
    licenceFiles: List<LicenceFiles.LicenceFile>,
    activeFileName: String?,
    onUseLicenceFile: (LicenceFiles.LicenceFile) -> Unit,
    enteredUnlockCode: String,
    onUnlockCodeChange: (String) -> Unit,
    unlockProblem: String?,
    isCheckingUnlockCode: Boolean,
    onUnlock: () -> Unit,
    onShareRequestCode: () -> Unit,
    onSaveRequestCode: () -> Unit,
    onBack: () -> Unit,
) {
    val manifest = status.manifest

    // Whether the text-paste box is open. Off by default: the file is the way.
    var showPasteBox by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock database") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            // imePadding, not just navigationBarsPadding.
            //
            // Without it the keyboard covers the "Open the database" button, so
            // a customer who has pasted their code cannot press the one button
            // that finishes the job. Found by actually doing it on the phone.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // --- what this database is, which needs no key to read -------
            DataEaterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = manifest.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (manifest.description.isNotBlank()) {
                        Text(
                            text = manifest.description,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    // The reassuring line, first, before any of the work. The
                    // most common mistaken belief about a locked file is that
                    // something is wrong with it.
                    Text(
                        text = "This database is locked, not damaged. The text " +
                            "inside is encrypted, and it opens with an Unlock " +
                            "Code from whoever made it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Only what the creator actually wrote. An empty contact
                    // line would be worse than no line at all.
                    if (status.hasCreator) {
                        FactLine("Made by", manifest.creator)
                    }
                    if (status.hasContact) {
                        FactLine("Ask them", manifest.contact)
                    }
                    if (!status.hasCreator && !status.hasContact) {
                        Text(
                            text = "This database does not say who to ask. " +
                                "Whoever gave you the file should be able to tell you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FactLine("Encryption", status.scheme)
                    FactLine("Documents", manifest.documentCount.toString())
                    FactLine("Passages", manifest.chunkCount.toString())
                }
            }

            // --- step 1 --------------------------------------------------
            DataEaterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StepHeading(1, "Send them your Request Code")

                    Text(
                        text = "Whoever made this database needs a code from " +
                            "this phone before they can send you a licence. " +
                            "One button sends it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    when {
                        requestCodeError != null -> ProblemLine(requestCodeError)

                        // The code is never shown. The customer does not read
                        // it, does not copy it and cannot get it wrong — one
                        // button hands it to whichever app they use to talk to
                        // the creator.
                        //
                        // Showing it was a step that existed only because
                        // there was nothing else to do with the string. Now
                        // there is, and a 124-character public key on screen
                        // is one more thing to photograph, mistype or read
                        // aloud wrongly.
                        requestCode == null -> Button(
                            onClick = onPrepareRequestCode,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPreparingRequestCode,
                        ) {
                            Text(
                                if (isPreparingRequestCode) {
                                    "Working…"
                                } else {
                                    "Send my Request Code"
                                }
                            )
                        }

                        else -> Column {
                            Text(
                                text = "Request Code sent. They will send you " +
                                    "back a file ending in .lic.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )

                            Spacer(Modifier.height(12.dp))

                            // Sending it somewhere else — a second messenger,
                            // a colleague who can forward it — is an ordinary
                            // thing to want, so it stays available. Showing
                            // the code is not: there is no reason to.
                            Row {
                                Button(onClick = onShareRequestCode) {
                                    Text("Send again")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = onCopyRequestCode) {
                                    Text(if (hasCopiedRequestCode) "Copied" else "Copy")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = onSaveRequestCode) {
                                    Text("Save to a file")
                                }
                            }

                            Reassurance(
                                "Your phone made a one-off key pair. The " +
                                    "Request Code is the public half of it, so " +
                                    "it is safe to send to anyone — it cannot " +
                                    "open anything by itself."
                            )
                            Reassurance(
                                "This code belongs to your phone, not to this " +
                                    "database, so you only send it once — even if " +
                                    "you buy more from the same person."
                            )
                            Reassurance(
                                "Nothing private has left the phone. The private " +
                                    "half stays inside Android's secure hardware, " +
                                    "and this app has no way to read it."
                            )
                        }
                    }
                }
            }

            // --- step 2 --------------------------------------------------
            DataEaterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StepHeading(2, "Save their licence file")

                    Text(
                        text = "They will send you a file ending in .lic. Save " +
                            "it into the folder below, and this screen will " +
                            "find it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    FolderHint()

                    Spacer(Modifier.height(12.dp))

                    // A refusal has to be visible here, not only in the paste
                    // box.
                    //
                    // It was shown in one place only, so tapping a licence
                    // FILE that turned out to be expired did nothing visible
                    // at all: the database stayed shut and the screen looked
                    // exactly as it had before. The customer would assume the
                    // app had not noticed, and ask the creator for another
                    // file — for the same expired licence.
                    if (unlockProblem != null && activeFileName != null) {
                        ProblemLine(unlockProblem)
                    }

                    if (licenceFiles.isEmpty()) {
                        Text(
                            text = "No licence file found yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        licenceFiles.forEach { file ->
                            LicenceFileRow(
                                file = file,
                                isBusy = isCheckingUnlockCode && file.name == activeFileName,
                                enabled = !isCheckingUnlockCode,
                                onUse = { onUseLicenceFile(file) },
                            )
                            if (file != licenceFiles.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }

                    // The old way, kept but out of the way.
                    //
                    // Some people will still have a code as text — from an
                    // older email, or a creator who sends it that way — and
                    // removing this would strand them. It is folded away so the
                    // file route is what they see first.
                    Spacer(Modifier.height(12.dp))
                    if (showPasteBox) {
                        PasteCode(
                            value = enteredUnlockCode,
                            onValueChange = onUnlockCodeChange,
                            problem = unlockProblem,
                            isChecking = isCheckingUnlockCode,
                            onUnlock = onUnlock,
                            onHide = { showPasteBox = false },
                        )
                    } else {
                        TextButton(onClick = { showPasteBox = true }) {
                            Text("I have a code as text instead")
                        }
                    }

                    Reassurance(
                        "The licence is checked against the creator's signature " +
                            "and its expiry before anything is decrypted. A file " +
                            "that has been altered, has run out, or was made for a " +
                            "different phone is refused — with the reason."
                    )
                }
            }
        }
    }
}

/** The folder, spelled out, because "where do I put it" is the whole question. */
@Composable
private fun FolderHint() {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = LicenceFiles.folderPath(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One licence file, with what it claims and a button to try it.
 *
 * The name and the claim come from the file and are **not** evidence of
 * anything — the file came from an email. They are shown so the customer can
 * tell their new licence from last year's, and the app will say plainly if it
 * turns out to be refused.
 */
@Composable
private fun LicenceFileRow(
    file: LicenceFiles.LicenceFile,
    isBusy: Boolean,
    enabled: Boolean,
    onUse: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.claim,
                style = MaterialTheme.typography.labelSmall,
                color = if (file.looksValid) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onUse,
            enabled = enabled,
        ) {
            Text(if (isBusy) "Checking…" else "Use")
        }
    }
}

/**
 * The fallback: pasting a code as text.
 *
 * Kept because it still works and some people only have that. Fenced behind a
 * button so the file route is what a new customer sees.
 *
 * Still has **no** paste-from-clipboard button: the code carries the content
 * key wrapped for this phone, and the clipboard is readable by other apps.
 */
@Composable
private fun PasteCode(
    value: String,
    onValueChange: (String) -> Unit,
    problem: String?,
    isChecking: Boolean,
    onUnlock: () -> Unit,
    onHide: () -> Unit,
) {
    Column {
        Text(
            text = "Paste the whole code. It is one long line, so check you " +
                "have both the beginning and the end.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text("Unlock Code") },
            minLines = 3,
            maxLines = 6,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            isError = problem != null,
        )

        if (problem != null) {
            ProblemLine(problem)
        }

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = onUnlock,
                modifier = Modifier.weight(1f),
                enabled = value.isNotBlank() && !isChecking,
            ) {
                Text(if (isChecking) "Checking…" else "Open the database")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onHide) { Text("Cancel") }
        }
    }
}

/**
 * A numbered step, beside its heading.
 *
 * The number is a filled circle in the accent colour rather than a bullet or a
 * "1." prefix, so it stays a circle at the largest text size instead of
 * drifting out of line with the heading beside it.
 */
@Composable
private fun StepHeading(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A label and a value, for the facts about the database. */
@Composable
private fun FactLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A quiet note explaining why something is safe or what happens next.
 *
 * Muted on purpose. These lines are reassurance for somebody who is already
 * anxious about handing a stranger a long string, and making them prominent
 * would compete with the two things they actually have to do.
 */
@Composable
private fun Reassurance(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Something went wrong, in the one colour that means it. */
@Composable
private fun ProblemLine(text: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
