@file:OptIn(ExperimentalLayoutApi::class)

package com.filexplor.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filexplor.app.data.remote.RemoteProtocol
import com.filexplor.app.data.remote.RemoteServer

/**
 * Connection form for one server.
 *
 * Saving does not connect, whatever this comment used to say. The first
 * attempt is the tap on the row that opens it, and that is where a wrong host
 * or password is reported: openServer makes the connection and the first
 * listing together, before the browse screen appears, so a server that refused
 * the password cannot be mistaken for an empty one.
 *
 * What the form itself can check, it checks. A blank host used to save quite
 * happily and arrive on the home screen as a row reading "SMB · " -- no
 * name, because displayName falls back to the host, and no way to work -- and
 * the only way to be rid of it was to work out that the nameless row was a
 * server at all.
 */
@Composable
fun ServerSetupDialog(
    protocol: RemoteProtocol,
    existing: RemoteServer?,
    busy: Boolean,
    onSubmit: (RemoteServer) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember {
        mutableStateOf((existing?.port ?: protocol.defaultPort).toString())
    }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var share by remember { mutableStateOf(existing?.share.orEmpty()) }
    var basePath by remember { mutableStateOf(existing?.basePath.orEmpty()) }

    // The least that could possibly connect. Username and password are left
    // out on purpose: an anonymous FTP server wants neither, and a form that
    // insisted on them would refuse a legitimate setup.
    val complete = host.isNotBlank() && (!protocol.usesShare || share.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Add ${protocol.label} server" else "Edit server") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (protocol == RemoteProtocol.FTP) {
                    // Worth saying outright: someone choosing FTP from a list of
                    // three has no particular reason to know it sends the
                    // password and every photo in the clear.
                    Text(
                        "FTP sends your password and your photos unencrypted. Prefer SFTP " +
                            "if the server offers it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    enabled = !busy
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.weight(2f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { new -> port = new.filter { it.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(if (protocol == RemoteProtocol.SMB) "Username (or DOMAIN\\user)" else "Username") },
                    singleLine = true,
                    enabled = !busy
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (protocol.usesShare) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = share,
                        onValueChange = { share = it },
                        label = { Text("Share name") },
                        singleLine = true,
                        enabled = !busy
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text("Start folder (optional)") },
                    singleLine = true,
                    enabled = !busy
                )

                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && complete,
                onClick = {
                    onSubmit(
                        RemoteServer(
                            id = existing?.id.orEmpty(),
                            protocol = protocol,
                            label = label.trim(),
                            host = host.trim(),
                            // Out of range falls back to the protocol's own
                            // port. The field allows five digits, which is one
                            // more than 65535 has room for -- and a port of
                            // 99999 fails inside the socket library with a
                            // message about an illegal argument that says
                            // nothing about what to fix.
                            port = port.toIntOrNull()?.takeIf { it in 1..65535 }
                                ?: protocol.defaultPort,
                            username = username.trim(),
                            password = password,
                            share = share.trim(),
                            basePath = basePath.trim()
                        )
                    )
                }
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}
