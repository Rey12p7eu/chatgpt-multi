
package com.chatgpt.multisession.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatgpt.multisession.data.AccountProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerSheet(
    accounts: List<AccountProfile>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSelect: (AccountProfile) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (AccountProfile, String) -> Unit,
    onDelete: (AccountProfile) -> Unit,
    onClearCache: (AccountProfile) -> Unit,
    onClearSession: (AccountProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AccountProfile?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Accounts", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxWidth().weight(1f, false)) {
                items(accounts) { acc ->
                    val isActive = acc.id == activeId
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { onSelect(acc) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("profile: ${acc.profileName}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { renameTarget = acc; newName = acc.displayName }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { onDelete(acc) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                        // quick actions
                        Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onClearCache(acc) }) { Text("Clear cache") }
                            OutlinedButton(onClick = { onClearSession(acc) }) { Text("Clear session") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add Account")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Account") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Label (e.g. Personal)") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onAdd(newName.trim())
                        newName = ""
                        showAddDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Account") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onRename(target, newName.trim())
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}
