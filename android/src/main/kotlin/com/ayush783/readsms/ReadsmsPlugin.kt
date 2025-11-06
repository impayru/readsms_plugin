package com.ayush783.readsms

import android.app.Activity
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SmsMessage
import android.provider.Telephony
import android.util.Log
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

class ReadsmsPlugin: FlutterPlugin, EventChannel.StreamHandler,BroadcastReceiver(), ActivityAware {
  private var channel : EventChannel? = null

  private var eventSink: EventChannel.EventSink? = null
  /**
   * context object to get the current context and register
   * the broadcast receiver
   */
  private lateinit var context: Context
  private lateinit var activity: Activity


  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    context.registerReceiver(this,IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    channel = EventChannel(flutterPluginBinding.binaryMessenger,"readsms")
    channel!!.setStreamHandler(this)
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }

  override fun onReceive(p0: Context?, p1: Intent?) {
    /**
     * Get the messages through the broadcast receiver
     * using the Telephony.Sms.Intent
     */
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
      val smsList = Telephony.Sms.Intents.getMessagesFromIntent(p1)
      val messagesGroupedByOriginatingAddress = smsList.groupBy { it.originatingAddress }
      messagesGroupedByOriginatingAddress.forEach { group ->
        val (subId, slotIndex) = extractSubIdAndSlot(context, p1, smsList)

        processIncomingSms(context, group.value, slotIndex)
      }
      
      // for (sms in smsList) {
      //   // Log.d("msg sender", sms.originatingAddress.toString())
      //   // Log.d("msg time",sms.timestampMillis.toString())
      //   var data = listOf(sms.displayMessageBody,sms.originatingAddress.toString(),sms.timestampMillis.toString(),)
      //   eventSink?.success(data)
      // }
    }
  }

  private fun processIncomingSms(context: Context, smsList: List<SmsMessage>, slotIndex: int) {
    val messageMap = smsList.first().toMutableMap()
    smsList.forEachIndexed { index, smsMessage ->
      if (index > 0) {
        messageMap["message_body"] = (messageMap["message_body"] as String)
          .plus(smsMessage.messageBody.trim())
        messageMap["sim_id"] = slotIndex
      }
    }

    var resultSms = listOf(messageMap["message_body"], messageMap["originating_address"], messageMap["timestamp"], messageMap["sim_id"])
    eventSink?.success(resultSms)
  }

  /**
   * Convert the [SmsMessage] to a [HashMap]
   */
  fun SmsMessage.toMap(): HashMap<String, Any?> {
    val smsMap = HashMap<String, Any?>()
    this.apply {
      smsMap["message_body"] = messageBody
      smsMap["timestamp"] = timestampMillis.toString()
      smsMap["originating_address"] = originatingAddress
      smsMap["status"] = status.toString()
      smsMap["service_center_address"] = serviceCenterAddress
    }
    return smsMap
  }

  fun extractSubIdAndSlot(context: Context?, intent: Intent?, smsList: Array<SmsMessage>): Pair<Int, Int> {
    val invalid = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    // 1) Пытаемся достать subId из интента (новые/старые ключи)
    val subIdFromIntent =
      intent?.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, invalid)
        .takeIf { it != invalid }
        ?: intent?.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_ID, invalid)
          .takeIf { it != invalid }
        ?: intent?.extras?.getInt("subscription", invalid)?.takeIf { it != invalid }

    // 2) Если не вышло — пробуем у самого сообщения
    val subId = subIdFromIntent
      ?: smsList.firstOrNull()?.let { msg ->
        // доступно не на всех API, но часто есть
        try { msg.subscriptionId } catch (_: Throwable) { invalid }
      } ?: invalid

    // 3) Преобразуем subId -> slotIndex (0/1/…)
    val slotIndex = if (subId != invalid) {
      SubscriptionManager.getSlotIndex(subId)
    } else {
      // на совсем старых девайсах бывает явный extra "slot"
      intent?.extras?.getInt("slot", -1) ?: -1
    }

    return subId to slotIndex // slotIndex: 0 -> SIM1, 1 -> SIM2
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel = null
    eventSink = null
  }

  override fun onAttachedToActivity(p0: ActivityPluginBinding) {
    activity = p0.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
  }

  override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) {
  }

  override fun onDetachedFromActivity() {
  }
}
