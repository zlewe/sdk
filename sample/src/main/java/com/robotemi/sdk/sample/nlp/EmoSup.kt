package com.robotemi.sdk.sample.nlp

import android.annotation.SuppressLint
import android.content.*
import android.util.Log
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import java.lang.Exception
import java.sql.Timestamp
import java.text.SimpleDateFormat

import android.widget.*
import android.graphics.*
import com.robotemi.sdk.*
import com.robotemi.sdk.Robot.*
import com.robotemi.sdk.constants.*
import com.robotemi.sdk.listeners.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import org.eclipse.paho.client.mqttv3.*
import java.util.*

import android.view.*
import com.robotemi.sdk.sample.R

class EmoSup (arg_robot: Robot, arg_applicationContext: Context){

    // Init from arguments
    private var robot = arg_robot;
    private var applicationContext = arg_applicationContext;

    // DataIO
    private val responseCallback = label@ DataIO.Callback { data: JSONObject? ->
        if (data == null) {
            robot.askQuestion(applicationContext.getString(R.string.nlp_server_error))
            Toast.makeText(
                applicationContext,
                applicationContext.getString(R.string.nlp_server_error),
                Toast.LENGTH_SHORT
            ).show()
            Log.e("EmoSupActivity", "Data is null")
        }
        var msg: String? = ""
        try {
            if (data != null) {
                msg = data.getString("return_message")
            }
            println(msg)
            if (msg != null) {
                robot.askQuestion(msg)
            }
        } catch (e: JSONException) {
            Log.e("EmoSupActivity", "Parsing error")
            e.printStackTrace()
            robot.askQuestion(applicationContext.getString(R.string.nlp_response_error))
        }
        robot.finishConversation()
    }
    private val dataIO: DataIO = DataIO(applicationContext, this.responseCallback);

    public fun nextSentence(userUtterance: String) {
        Log.d("EmoSupActivity", "EmoSupActivity: nextSentence()")

        //for EmoChat
        if (userUtterance != "") {
            val data: JSONObject = createJson(userUtterance)
            dataIO.prepareRequest(data, "emo_sup")
            dataIO.submitData(null)
        }
    }

    private fun createJson(query: String): JSONObject {
        val ts = Timestamp(System.currentTimeMillis())
        @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        var tsStr: String? = ""
        try {
            tsStr = sdf.format(ts)
            println(tsStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val obj = JSONObject()
        try {
            obj.put("msg", query)
            obj.put("post_time", tsStr)
        } catch (e: JSONException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        println(obj.toString())
        return obj
    }
}