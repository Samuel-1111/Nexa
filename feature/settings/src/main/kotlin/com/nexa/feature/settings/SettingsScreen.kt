package com.nexa.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.core.designsystem.NexaColors
import com.nexa.core.network.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _displayName=MutableStateFlow("NEXA user"); val displayName:StateFlow<String> = _displayName.asStateFlow()
    private val _assistantName=MutableStateFlow("NEXA"); val assistantName:StateFlow<String> = _assistantName.asStateFlow()
    private val _message=MutableStateFlow<String?>(null); val message:StateFlow<String?> = _message.asStateFlow()
    init { viewModelScope.launch { _displayName.value=auth.currentDisplayName() ?: "NEXA user"; _assistantName.value=auth.currentAssistantName() ?: "NEXA" } }
    fun saveProfile(name:String, pa:String, done:()->Unit) = viewModelScope.launch { try { auth.saveOnboardingProfile(name,pa); _displayName.value=name.trim(); _assistantName.value=pa.trim(); done() } catch(e:Exception){ _message.value=e.message ?: "Could not save profile." } }
    fun startPlan(plan:String, onRrr:(String)->Unit) = viewModelScope.launch { try { val r=auth.initiateSubscription(plan); val rrr = r.rrr
            if(!rrr.isNullOrBlank()) onRrr(rrr) else _message.value=r.error ?: "Payment could not be started." } catch(e:Exception){ _message.value="Payment could not be started. Please try again." } }
    fun clearMessage(){_message.value=null}
}

@Composable
fun SettingsScreen(onOpenMemoryCenter:()->Unit={}, onSignOut:()->Unit={}, onSubscription:()->Unit={}, viewModel:SettingsViewModel=hiltViewModel()) {
    val name by viewModel.displayName.collectAsState(); val pa by viewModel.assistantName.collectAsState(); val message by viewModel.message.collectAsState()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var editName by rememberSaveable(name) { mutableStateOf(name) }; var editPa by rememberSaveable(pa){mutableStateOf(pa)}
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Settings",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Make NEXA fit the way you work.",style=MaterialTheme.typography.bodySmall,color=NexaColors.OnSurfaceMuted)
        Card(shape=RoundedCornerShape(20.dp)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(50),color=NexaColors.EventBlueBg){Icon(Icons.Default.Person,null,Modifier.padding(13.dp),tint=NexaColors.Primary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.Bold);Text("PA: "+pa,style=MaterialTheme.typography.bodySmall,color=NexaColors.OnSurfaceMuted)};IconButton({dialog="profile"}){Icon(Icons.Default.Edit,null)}}}
        SettingItem(Icons.Default.Person,"Account & Profile","Change your name and PA name"){dialog="profile"}
        SettingItem(Icons.Default.Memory,"Memory","Review, approve, edit or reject saved memories"){onOpenMemoryCenter()}
        SettingItem(Icons.Default.CreditCard,"Subscription","View plans, features and pricing"){onSubscription()}
        SettingItem(Icons.Default.Apps,"Connected Apps","Review integrations and permissions"){dialog="apps"}
        SettingItem(Icons.Default.Notifications,"Notifications","Open Android notification settings"){context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
        SettingItem(Icons.Default.Lock,"Privacy & Permissions","Open NEXA app permissions"){context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)))}
        SettingItem(Icons.Default.HelpOutline,"Help & Support","Get help, privacy information and feedback"){dialog="help"}
        SettingItem(Icons.Default.Info,"About NEXA","Version 0.1.0"){dialog="about"}
        OutlinedButton(onClick=onSignOut,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(16.dp)){Text("Log out",fontWeight=FontWeight.SemiBold)}
    }
    if(dialog=="profile") AlertDialog(onDismissRequest={dialog=null},title={Text("Account & Profile")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(editName,{editName=it},label={Text("Your name")},singleLine=true);OutlinedTextField(editPa,{editPa=it},label={Text("What would you like to call your PA?")},singleLine=true)}},confirmButton={Button(onClick={viewModel.saveProfile(editName,editPa){dialog=null}}){Text("Save")}},dismissButton={TextButton({dialog=null}){Text("Cancel")}})
    if(dialog=="apps") InfoDialog("Connected Apps","NEXA currently uses Android notifications, alarms, microphone and your NEXA account. More integrations appear here as they are connected."){dialog=null}
    if(dialog=="help") HelpSupportDialog(
        onDismiss = { dialog = null },
        onWhatsApp = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/2349042987385?text=Hello%20NEXA%20Support%2C%20I%20need%20help%20with%20the%20NEXA%20app."))
            context.startActivity(intent)
        }
    )
    if(dialog=="about") InfoDialog("About NEXA","NEXA — Your Personal Assistant. Built for fast, private and permission-based assistance."){dialog=null}
}

@Composable private fun SettingItem(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String,onClick:()->Unit){Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=NexaColors.EventBlueBg){Icon(icon,null,Modifier.padding(9.dp),tint=NexaColors.Primary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=NexaColors.OnSurfaceMuted)};Icon(Icons.Default.ChevronRight,null,tint=NexaColors.OnSurfaceMuted)}}}
@Composable private fun InfoDialog(title:String,body:String,onDismiss:()->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={Button(onClick=onDismiss){Text("Done")}})}
@Composable
private fun HelpSupportDialog(onDismiss: () -> Unit, onWhatsApp: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help & Support") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Need help, want to report a problem, or have feedback? Our support line is available on WhatsApp.")
                Text("WhatsApp: 0904 298 7385", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = onWhatsApp) {
                Icon(Icons.Default.Chat, null)
                Spacer(Modifier.width(6.dp))
                Text("Chat on WhatsApp")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}


@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsState()
    val plans = listOf(
        Triple("Essential", "₦1,000 / month", listOf("150 AI requests / month", "75 voice requests / month", "20 active automations", "250 memories")),
        Triple("Pro", "₦3,000 / month", listOf("750 AI requests / month", "300 voice requests / month", "100 active automations", "1,000 memories")),
        Triple("Executive", "₦5,000 / month", listOf("Unlimited AI requests", "Unlimited voice requests", "Unlimited automations", "Unlimited memories")),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("NEXA Subscription", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Choose the plan that fits how you use NEXA.", style = MaterialTheme.typography.bodySmall, color = NexaColors.OnSurfaceMuted)
            }
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = NexaColors.EventBlueBg)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("3-day free trial", fontWeight = FontWeight.Bold)
                Text("Try NEXA before you pay. Your trial includes the core assistant, organizer and automation features.", style = MaterialTheme.typography.bodySmall)
                Text("After the trial, choose a monthly plan to continue.", style = MaterialTheme.typography.labelSmall, color = NexaColors.OnSurfaceMuted)
            }
        }
        plans.forEach { (name, price, features) ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(price, color = NexaColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    features.forEach { feature ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = NexaColors.Primary)
                            Spacer(Modifier.width(7.dp))
                            Text(feature, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.startPlan(name.uppercase()) { rrr ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://login.remita.net/remita/ecomm/finalize.reg?rrr=" + rrr)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue with $name") }
                }
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
