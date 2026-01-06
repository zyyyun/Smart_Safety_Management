package com.example.smart_safety_management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Contact(val name: String, val phoneNumber: String)

val mockContacts = listOf(
    Contact("김철수", "010-1234-5678"),
    Contact("김영희", "010-8765-4321"),
    Contact("박현장", "010-1111-2222")
)

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun EmergencyContactScreen() {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = if (searchQuery.isBlank()) {
        mockContacts
    } else {
        mockContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.replace("-", "").contains(searchQuery.replace("-", ""), ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp,
                    title = {
                        Text(
                            text = "비상연락",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface,
                            modifier = Modifier.offset(x = (-16).dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* Handle back navigation */ }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search",
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    }
                )

                AnimatedVisibility(visible = isSearchActive) {
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.surface) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("이름 또는 전화번호를 입력하세요.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                cursorColor = MaterialTheme.colors.onSurface,
                                backgroundColor = MaterialTheme.colors.surface
                            ),
                            maxLines = 1,
                            singleLine = true
                        )
                    }
                }
                Divider()
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(filteredContacts) { contact ->
                ContactListItem(contact = contact)
            }
        }
    }
}

@Composable
fun ContactListItem(contact: Contact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = contact.name, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colors.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = contact.phoneNumber, fontSize = 14.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = { /* Handle call */ }) {
            Icon(Icons.Default.Call, contentDescription = "Call ${contact.name}", tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}
