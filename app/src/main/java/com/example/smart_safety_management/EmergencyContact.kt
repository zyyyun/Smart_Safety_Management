package com.example.smart_safety_management

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smart_safety_management.ui.theme.Smart_Safety_ManagementTheme

data class Contact(val name: String, val phoneNumber: String)

val mockContacts = listOf(
    Contact("아이유", "010-1234-5678"),
    Contact("유재석", "010-3456-7890"),
    Contact("정해인", "010-9012-3456"),
    Contact("한지민", "010-0123-4567")
)

@Preview(
    showBackground = true,
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

    Smart_Safety_ManagementTheme {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        backgroundColor = MaterialTheme.colors.surface,
                        elevation = 0.dp,
                        modifier = Modifier.height(40.dp),
                        title = {
                            Text(
                                text = "비상연락",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier.offset(x = (-24).dp)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.backicon),
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colors.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                if (isSearchActive) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Search",
                                        tint = MaterialTheme.colors.onSurface
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.search),
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                    )

                    AnimatedVisibility(visible = isSearchActive) {
                        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.surface) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                                    .fillMaxWidth(),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colors.onSurface
                                ),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "이름 또는 전화번호를 입력하세요.",
                                                fontSize = 16.sp,
                                                color = Color(0xFFB1B8BE)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
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
}

@Composable
fun ContactListItem(contact: Contact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(51.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = contact.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colors.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = contact.phoneNumber, fontSize = 14.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = { }) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE6E8EA),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.callbox),
                    contentDescription = "Call ${contact.name}",
                    tint = Color(0xFFF97316)
                )
            }
        }
    }
    Divider(color = Color(0xFFF4F5F6))
}
