package com.nexa.feature.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexa.core.network.AiMessage
import com.nexa.core.voice.VoiceCaptureController
import com.nexa.core.voice.VoiceCaptureState

@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel(), voiceController: VoiceCaptureController = remember { VoiceCaptureController() }) {
    var text by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val assistantName by viewModel.assistantName.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val voiceState by voiceController.state.collectAsState()
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)voiceController.start()}
    LaunchedEffect(voiceState){val captured=voiceState as? VoiceCaptureState.Captured ?: return@LaunchedEffect;viewModel.transcribe(captured.audioBase64,captured.mimeType);voiceController.clear()}
    LaunchedEffect(transcript){transcript?.let{text=it}}
    DisposableEffect(Unit){onDispose{voiceController.release()}}
    val listening=voiceState is VoiceCaptureState.Listening
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Assistant",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Chat with " + assistantName + " and ask it to act.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton({showHistory=true}){Icon(Icons.Default.History,"Chat history")};IconButton({viewModel.newChat();text=""}){Icon(Icons.Default.AddComment,"New chat")}}
        if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=4.dp))
        if(messages.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("What can " + assistantName + " help you with?",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text("Ask a question, create a reminder, add a task or take a note.",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Plan my day","Add a reminder","Take a note").forEach{AssistChip(onClick={text=it},label={Text(it)})}}}}} else LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(vertical=8.dp)){items(messages,key={it.id}){MessageBubble(it)}}
        transcript?.let{Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(10.dp)){Text("Review voice text",fontWeight=FontWeight.Bold);Text(it,maxLines=3);Text("Edit below if needed, then send.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.Bottom){OutlinedTextField(text,{text=it},modifier=Modifier.weight(1f),placeholder={Text("Message " + assistantName + "…)},shape=RoundedCornerShape(22.dp),maxLines=5,enabled=!busy&&!listening);Spacer(Modifier.width(6.dp));FilledTonalIconButton(onClick={if(listening)voiceController.stop() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)},enabled=!busy){Icon(if(listening)Icons.Default.Stop else Icons.Default.Mic,null)}}
        Spacer(Modifier.height(5.dp));Button(onClick={viewModel.ask(text);text=""},enabled=text.isNotBlank()&&!busy&&!listening,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(18.dp)){if(busy)CircularProgressIndicator(strokeWidth=2.dp)else Text("Send",fontWeight=FontWeight.Bold)}}
    if(showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Chat history") },
            text = {
                if(chats.isEmpty()) {
                    Text("No saved chats yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Card(
                                onClick = { viewModel.selectChat(chat.id); showHistory = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(chat.title, fontWeight = FontWeight.SemiBold)
                                        Text(chat.updated_at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        )
    }
}

@Composable private fun MessageBubble(message:AiMessage){val user=message.role=="user";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start){Surface(shape=RoundedCornerShape(18.dp),color=if(user)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.widthIn(max=320.dp)){Column(Modifier.padding(12.dp)){if(!user)Text("NEXA",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(message.content)}}}}