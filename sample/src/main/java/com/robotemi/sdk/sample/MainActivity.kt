package com.robotemi.sdk.sample

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.AsyncTask
import android.os.RemoteException
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.graphics.*
import android.media.MediaPlayer
import android.util.Size
import androidx.annotation.CheckResult
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.robotemi.sdk.*
import com.robotemi.sdk.Robot.*
import com.robotemi.sdk.Robot.Companion.getInstance
import com.robotemi.sdk.TtsRequest.Companion.create
import com.robotemi.sdk.activitystream.ActivityStreamObject
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage
import com.robotemi.sdk.constants.*
import com.robotemi.sdk.exception.OnSdkExceptionListener
import com.robotemi.sdk.exception.SdkException
import com.robotemi.sdk.face.ContactModel
import com.robotemi.sdk.face.OnContinuousFaceRecognizedListener
import com.robotemi.sdk.face.OnFaceRecognizedListener
import com.robotemi.sdk.listeners.*
import com.robotemi.sdk.map.MapDataModel
import com.robotemi.sdk.map.MapModel
import com.robotemi.sdk.map.OnLoadMapStatusChangedListener
import com.robotemi.sdk.model.CallEventModel
import com.robotemi.sdk.model.DetectionData
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener
import com.robotemi.sdk.navigation.listener.OnDistanceToLocationChangedListener
import com.robotemi.sdk.navigation.listener.OnReposeStatusChangedListener
import com.robotemi.sdk.navigation.model.Position
import com.robotemi.sdk.navigation.model.SafetyLevel
import com.robotemi.sdk.navigation.model.SpeedLevel
import com.robotemi.sdk.permission.OnRequestPermissionResultListener
import com.robotemi.sdk.permission.Permission
import com.robotemi.sdk.sequence.OnSequencePlayStatusChangedListener
import com.robotemi.sdk.sequence.SequenceModel
import com.robotemi.sdk.voice.ITtsService
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import org.eclipse.paho.client.mqttv3.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import android.graphics.BitmapFactory

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.rtp.AudioCodec
import android.net.rtp.AudioGroup
import android.net.rtp.AudioStream
import android.net.rtp.RtpStream
import android.view.*
import android.widget.LinearLayout
import java.net.InetAddress
import android.media.AudioManager
import android.media.AudioManager.GET_DEVICES_INPUTS
import androidx.activity.result.contract.ActivityResultContracts

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

class MainActivity : AppCompatActivity(), NlpListener, OnRobotReadyListener,
    ConversationViewAttachesListener, WakeupWordListener, ActivityStreamPublishListener,
    TtsListener, OnBeWithMeStatusChangedListener, OnGoToLocationStatusChangedListener,
    OnLocationsUpdatedListener, OnConstraintBeWithStatusChangedListener,
    OnDetectionStateChangedListener, AsrListener, OnTelepresenceEventChangedListener,
    OnRequestPermissionResultListener, OnDistanceToLocationChangedListener,
    OnCurrentPositionChangedListener, OnSequencePlayStatusChangedListener, OnRobotLiftedListener,
    OnDetectionDataChangedListener, OnUserInteractionChangedListener, OnFaceRecognizedListener,
    OnConversationStatusChangedListener, OnTtsVisualizerWaveFormDataChangedListener,
    OnTtsVisualizerFftDataChangedListener, OnReposeStatusChangedListener,
    OnLoadMapStatusChangedListener, OnDisabledFeatureListUpdatedListener,
    OnMovementVelocityChangedListener, OnMovementStatusChangedListener,
    OnContinuousFaceRecognizedListener, ITtsService,
    TextToSpeech.OnInitListener, OnSdkExceptionListener {

    private lateinit var robot: Robot

    private val executorService = Executors.newSingleThreadExecutor()
    //private val audioTask = AudioTask()

    private var tts: TextToSpeech? = null

    private var mqttEn = false
    private lateinit var mqttClient : MQTTClient

    var filename = ""
    private var imageResource = 0

    var name = "abc"
    var clicktime = 1
    var moving = false

    @Volatile
    private var mapDataModel: MapDataModel? = null

    private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private fun startAudioStream(sampleRate:Int, channel:Int, format:Int, buffer_size:Int)
    {
        //initialize the audio streaming
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        Log.e("Avaliable Devices Num:", audio.getDevices(GET_DEVICES_INPUTS).size.toString())
        audio.mode = AudioManager.MODE_IN_COMMUNICATION

        var buffer = ByteArray(buffer_size)
        var recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, format, buffer_size)

        recorder.startRecording()
        var audioThread = Thread(Runnable {
            while(true){
                buffer = ByteArray(buffer_size)
                recorder.read(buffer, 0, buffer_size, AudioRecord.READ_BLOCKING)
                publishMessage("audio", buffer)
            }
        })
        audioThread.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        verifyStoragePermissions(this)
        verifyCameraPermissions(this)
        //取得相機權限
        //permission.launch(Manifest.permission.CAMERA)

        robot = getInstance()
        initOnClickListener()
        tvLog.movementMethod = ScrollingMovementMethod.getInstance()
        robot.addOnRequestPermissionResultListener(this)
        robot.addOnTelepresenceEventChangedListener(this)
        robot.addOnFaceRecognizedListener(this)
        robot.addOnContinuousFaceRecognizedListener(this)
        robot.addOnLoadMapStatusChangedListener(this)
        robot.addOnDisabledFeatureListUpdatedListener(this)
        robot.addOnSdkExceptionListener(this)
        robot.addOnMovementStatusChangedListener(this)
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        if (appInfo.metaData != null
            && appInfo.metaData.getBoolean(SdkConstants.METADATA_OVERRIDE_TTS, false)
        ) {
            printLog("Override tts")
            tts = TextToSpeech(this, this)
            robot.setTtsService(this)
        }
        startMQTT()
        startCamera()
        startAudioStream(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 44100)
    }

    /**
     * Setting up all the event listeners
     */
    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addNlpListener(this)
        robot.addOnBeWithMeStatusChangedListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addConversationViewAttachesListener(this)
        robot.addWakeupWordListener(this)
        robot.addTtsListener(this)
        robot.addOnLocationsUpdatedListener(this)
        robot.addOnConstraintBeWithStatusChangedListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addAsrListener(this)
        robot.addOnDistanceToLocationChangedListener(this)
        robot.addOnCurrentPositionChangedListener(this)
        robot.addOnSequencePlayStatusChangedListener(this)
        robot.addOnRobotLiftedListener(this)
        robot.addOnDetectionDataChangedListener(this)
        robot.addOnUserInteractionChangedListener(this)
        robot.addOnConversationStatusChangedListener(this)
        robot.addOnTtsVisualizerWaveFormDataChangedListener(this)
        robot.addOnTtsVisualizerFftDataChangedListener(this)
        robot.addOnReposeStatusChangedListener(this)
        robot.addOnMovementVelocityChangedListener(this)
        robot.showTopBar()
    }

    /**
     * Removing the event listeners upon leaving the app.
     */
    override fun onStop() {
        robot.removeOnRobotReadyListener(this)
        robot.removeNlpListener(this)
        robot.removeOnBeWithMeStatusChangedListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeConversationViewAttachesListener(this)
        robot.removeWakeupWordListener(this)
        robot.removeTtsListener(this)
        robot.removeOnLocationsUpdateListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        robot.removeAsrListener(this)
        robot.removeOnDistanceToLocationChangedListener(this)
        robot.removeOnCurrentPositionChangedListener(this)
        robot.removeOnSequencePlayStatusChangedListener(this)
        robot.removeOnRobotLiftedListener(this)
        robot.removeOnDetectionDataChangedListener(this)
        robot.addOnUserInteractionChangedListener(this)
        robot.stopMovement()
        if (robot.checkSelfPermission(Permission.FACE_RECOGNITION) == Permission.GRANTED) {
            robot.stopFaceRecognition()
        }
        robot.removeOnConversationStatusChangedListener(this)
        robot.removeOnTtsVisualizerWaveFormDataChangedListener(this)
        robot.removeOnTtsVisualizerFftDataChangedListener(this)
        robot.removeOnReposeStatusChangedListener(this)
        robot.removeOnMovementVelocityChangedListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        mqttClient.disconnect()
        robot.removeOnRequestPermissionResultListener(this)
        robot.removeOnTelepresenceEventChangedListener(this)
        robot.removeOnFaceRecognizedListener(this)
        robot.removeOnContinuousFaceRecognizedListener(this)
        robot.removeOnSdkExceptionListener(this)
        robot.removeOnLoadMapStatusChangedListener(this)
        robot.removeOnDisabledFeatureListUpdatedListener(this)
        robot.removeOnMovementStatusChangedListener(this)
        if (!executorService.isShutdown) {
            executorService.shutdownNow()
        }
        tts?.shutdown()
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        if (appInfo.metaData != null
            && appInfo.metaData.getBoolean(SdkConstants.METADATA_OVERRIDE_TTS, false)
        ) {
            printLog("Unbind TTS service")
            tts = TextToSpeech(this, this)
            robot.setTtsService(null)
        }
        super.onDestroy()
    }

    private fun initOnClickListener() {
//        btnSpeak.setOnClickListener { speak() }
//        btnSaveLocation.setOnClickListener { saveLocation() }
//        btnGoTo.setOnClickListener { goTo() }
//        btnStopMovement.setOnClickListener { stopMovement() }
//        btnFollow.setOnClickListener { followMe() }
//        btnskidJoy.setOnClickListener { skidJoy() }
//        btnTiltAngle.setOnClickListener { tiltAngle() }
//        btnTiltBy.setOnClickListener { tiltBy() }
//        btnTurnBy.setOnClickListener { turnBy() }
//        btnBatteryInfo.setOnClickListener { getBatteryData() }
//        btnSavedLocations.setOnClickListener { savedLocationsDialog() }
//        btnCallOwner.setOnClickListener { callOwner() }
//        btnPublish.setOnClickListener { publishToActivityStream() }
//        btnHideTopBar.setOnClickListener { hideTopBar() }
//        btnShowTopBar.setOnClickListener { showTopBar() }
//        btnDisableWakeup.setOnClickListener { disableWakeup() }
//        btnEnableWakeup.setOnClickListener { enableWakeup() }
//        btnToggleNavBillboard.setOnClickListener { toggleNavBillboard() }
//        btnTogglePrivacyModeOn.setOnClickListener { privacyModeOn() }
//        btnTogglePrivacyModeOff.setOnClickListener { privacyModeOff() }
//        btnGetPrivacyMode.setOnClickListener { getPrivacyModeState() }
//        btnEnableHardButtons.setOnClickListener { enableHardButtons() }
//        btnDisableHardButtons.setOnClickListener { disableHardButtons() }
//        btnIsHardButtonsDisabled.setOnClickListener { isHardButtonsEnabled() }
//        btnGetOSVersion.setOnClickListener { getOSVersion() }
//        btnCheckFace.setOnClickListener { requestFace() }
//        btnCheckMap.setOnClickListener { requestMap() }
//        btnCheckSettings.setOnClickListener { requestSettings() }
//        btnCheckSequence.setOnClickListener { requestSequence() }
//        btnCheckAllPermission.setOnClickListener { requestAll() }
//        btnStartFaceRecognition.setOnClickListener { startFaceRecognition() }
//        btnStopFaceRecognition.setOnClickListener { stopFaceRecognition() }
//        btnSetGoToSpeed.setOnClickListener { setGoToSpeed() }
//        btnSetGoToSafety.setOnClickListener { setGoToSafety() }
//        btnToggleTopBadge.setOnClickListener { toggleTopBadge() }
//        btnToggleDetectionMode.setOnClickListener { toggleDetectionMode() }
//        btnToggleAutoReturn.setOnClickListener { toggleAutoReturn() }
//        btnTrackUser.setOnClickListener { toggleTrackUser() }
//        btnGetVolume.setOnClickListener { getVolume() }
//        btnSetVolume.setOnClickListener { setVolume() }
//        btnRequestToBeKioskApp.setOnClickListener { requestToBeKioskApp() }
//        btnStartDetectionModeWithDistance.setOnClickListener { startDetectionWithDistance() }
//        btnFetchSequence.setOnClickListener { getAllSequences() }
//        btnPlayFirstSequence.setOnClickListener { playFirstSequence() }
//        btnPlayFirstSequenceWithoutPlayer.setOnClickListener { playFirstSequenceWithoutPlayer() }
//        btnFetchMap.setOnClickListener { getMap() }
//        btnClearLog.setOnClickListener { clearLog() }
//        btnNlu.setOnClickListener { startNlu() }
//        btnGetAllContacts.setOnClickListener { getAllContacts() }
//        btnGoToPosition.setOnClickListener { goToPosition() }
//        btnStartTelepresenceToCenter.setOnClickListener { startTelepresenceToCenter() }
//        btnStartPage.setOnClickListener { startPage() }
//        btnRestart.setOnClickListener { restartTemi() }
//        btnGetMembersStatus.setOnClickListener { getMembersStatus() }
//        btnRepose.setOnClickListener { repose() }
//        btnGetMapList.setOnClickListener { getMapListBtn() }
//        btnLoadMap.setOnClickListener { loadMap() }
//        btnLock.setOnClickListener { lock() }
//        btnUnlock.setOnClickListener { unlock() }
//        btnMuteAlexa.setOnClickListener { muteAlexa() }
//        btnShutdown.setOnClickListener { shutdown() }
//        btnLoadMapWithPosition.setOnClickListener { loadMapWithPosition() }
//        btnLoadMapWithReposePosition.setOnClickListener { loadMapWithReposePosition() }
//        btnLoadMapWithRepose.setOnClickListener { loadMapWithRepose() }
//        btnSetSoundMode.setOnClickListener { setSoundMode() }
//        btnSetHardBtnMainMode.setOnClickListener { setHardBtnMainMode() }
//        btnToggleHardBtnPower.setOnClickListener { toggleHardBtnPower() }
//        btnToggleHardBtnVolume.setOnClickListener { toggleHardBtnVolume() }
//        btnGetNickName.setOnClickListener { getNickName() }
//        btnSetMode.setOnClickListener { setMode() }
//        btnGetMode.setOnClickListener { getMode() }
//        btnToggleKioskMode.setOnClickListener { toggleKiosk() }
////        btnIsKioskModeOn.setOnClickListener { isKioskModeOn() }
//        btnIsKioskModeOn.setOnClickListener { sendMap() }
//        btnEnabledLatinKeyboards.setOnClickListener { enabledLatinKeyboards() }
//        btnGetSupportedKeyboard.setOnClickListener { getSupportedLatinKeyboards() }
//        btnEnableCamera.setOnClickListener { startCamera() }
//        btnMQTTStart.setOnClickListener { startMQTT() }
//        btnMQTTSub.setOnClickListener { subscribeCMD() }
        startgame2.setOnClickListener{ startplaygame()}
        Register.setOnClickListener{ Register()}
        yes.setOnClickListener{ PlayAgain()}
        no.setOnClickListener{ NotPlayAgain()}
    }

    //game
    //music
    fun music(filename: Int, sleeptime: Long){
        singleThreadExecutor.execute{
            var mediaPlayer = MediaPlayer.create(this, filename)
            mediaPlayer.start()
            Thread.sleep(sleeptime)
        }
    }

    //picture
    fun picture(filename: String){
        var uri = "@drawable/" + filename  //圖片路徑和名稱
        var imageResource = resources.getIdentifier(uri, null, packageName)
        imageView2.setImageResource(imageResource);
    }

    //startgame
    private fun startplaygame(){

        var c = findViewById<TextView>(R.id.textView)
        c.visibility = View.VISIBLE
        c.setText("Face Training Time~~")
        var e = findViewById<View>(R.id.imageView2)
        e.visibility = View.VISIBLE
        var b = findViewById<Button>(R.id.startgame2)
        b.visibility = View.GONE
        var d = findViewById<Button>(R.id.Register)
        d.visibility = View.GONE
        var f = findViewById<EditText>(R.id.etYaw2)
        f.visibility = View.GONE

        val pv = findViewById<View>(R.id.previewView) as PreviewView
        val width = 1
        val height = 1
        val parms = LinearLayout.LayoutParams(width, height)
        pv.setLayoutParams(parms)

        var s = "start game"
        publishMessage("game",s.toByteArray())

        picture("cover")
    }

    //Register
    private fun Register(){

        var c = findViewById<TextView>(R.id.textView)
        c.visibility = View.GONE
        var d = findViewById<View>(R.id.imageView2)
        d.visibility = View.GONE
        var e = findViewById<EditText>(R.id.etYaw2)
        e.visibility = View.VISIBLE


        val pv = findViewById<View>(R.id.previewView) as PreviewView
        val width = 1920
        val height = 1080
        val parms = LinearLayout.LayoutParams(width, height)
        pv.setLayoutParams(parms)

        if(clicktime == 1){
            clicktime = 2
        }

        else{
            if (e.text.toString() != "" && e.text.toString() != name){
                var b = findViewById<Button>(R.id.Register)
                b.visibility = View.GONE
                var s = "Register," + e.text.toString()
                name = e.text.toString()
                publishMessage("game",s.toByteArray())
            }
            else{
                c.visibility = View.VISIBLE
                c.text = "PLEASE INPUT YOUR NAME"
            }
        }

        music(R.raw.register, 5200)
    }

    private fun TakePicture(){


        var c = findViewById<TextView>(R.id.textView)
        c.visibility = View.GONE
        var d = findViewById<View>(R.id.imageView2)
        d.visibility = View.GONE
        var e = findViewById<EditText>(R.id.etYaw2)
        e.visibility = View.GONE
        var b = findViewById<Button>(R.id.Register)
        b.visibility = View.GONE
        var j = findViewById<Button>(R.id.startgame2)
        j.visibility = View.GONE
        var f = findViewById<EditText>(R.id.etYaw2)
        f.visibility = View.GONE
        var g = findViewById<Button>(R.id.yes)
        g.visibility = View.GONE
        var h = findViewById<Button>(R.id.no)
        h.visibility = View.GONE
        var i = findViewById<LinearLayout>(R.id.yesorno)
        i.visibility = View.GONE


        val pv = findViewById<View>(R.id.previewView) as PreviewView
        val width = 1920
        val height = 1080
        val parms = LinearLayout.LayoutParams(width, height)
        pv.setLayoutParams(parms)

        //music(R.raw.num54321, 5200)

        singleThreadExecutor.execute{
            for (i in 5 downTo 1) {
                //robot.askQuestion(i.toString())
                //robot.finishConversation()
                robot.speak(TtsRequest.create(i.toString(),false))
                Thread.sleep(1500)
            }
            robot.speak(TtsRequest.create("誰氣死",false))
            Thread.sleep(1500)
            robot.speak(TtsRequest.create("喀嚓",false))
            var s = "take_one_picture"
            publishMessage("game",s.toByteArray())
        }


    }


    //Return initial screen
    private fun ReturnInitialscreen(){

        var c = findViewById<View>(R.id.textView)
        c.visibility = View.GONE
        var d = findViewById<View>(R.id.imageView2)
        d.visibility = View.VISIBLE
        val width2 = 1920
        val height2 = 1080
        val parms2 = LinearLayout.LayoutParams(width2, height2)
        d.setLayoutParams(parms2)
        var b = findViewById<Button>(R.id.Register)
        b.visibility = View.VISIBLE
        b.setText("Register")
        var e = findViewById<Button>(R.id.startgame2)
        e.visibility = View.VISIBLE
        var f = findViewById<EditText>(R.id.etYaw2)
        f.visibility = View.GONE
        var g = findViewById<Button>(R.id.yes)
        g.visibility = View.GONE
        var h = findViewById<Button>(R.id.no)
        h.visibility = View.GONE
        var i = findViewById<LinearLayout>(R.id.yesorno)
        i.visibility = View.GONE

        clicktime = 1

        val pv = findViewById<View>(R.id.previewView) as PreviewView
        val width = 1
        val height = 1
        val parms = LinearLayout.LayoutParams(width, height)
        pv.setLayoutParams(parms)

        picture("circle")
    }

    fun PlayAgain(){
        var s = "start game"
        publishMessage("game",s.toByteArray())
        var d = findViewById<ImageView>(R.id.imageView2) as ImageView
        val width = 1920
        val height = 1080
        val parms = LinearLayout.LayoutParams(width, height)
        d.setLayoutParams(parms)
        var c = findViewById<View>(R.id.textView)
        c.visibility = View.GONE
        var g = findViewById<Button>(R.id.yes)
        g.visibility = View.GONE
        var h = findViewById<Button>(R.id.no)
        h.visibility = View.GONE
        var i = findViewById<LinearLayout>(R.id.yesorno)
        i.visibility = View.GONE
        picture("cover")
    }

    fun NotPlayAgain(){
        ReturnInitialscreen()
    }



    /**
     * MQTT PART START
     */
    private fun startMQTT(){
        val serverURI = "tcp://"+etSaveLocation.text.toString()+":1883"
        //val serverURI   = MQTT_SERVER_URI
        val clientId    = MQTT_CLIENT_ID
        val username    = MQTT_USERNAME
        val pwd         = MQTT_PWD

        if (etSaveLocation.text.isEmpty())
            mqttClient = MQTTClient(applicationContext, "tcp://192.168.50.197:1883", clientId)
        else
            mqttClient = MQTTClient(applicationContext, serverURI, clientId)
        mqttEn = true

        // Connect and login to MQTT Broker
        mqttClient.connect( username,
            pwd,
            object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(this.javaClass.name, "Connection success")

                    Toast.makeText(applicationContext, "MQTT Connection success", Toast.LENGTH_SHORT).show()
                    subscribeCMD()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(this.javaClass.name, "Connection failure: ${exception.toString()}")

                    Toast.makeText(applicationContext, "MQTT Connection fails: ${exception.toString()}", Toast.LENGTH_SHORT).show()

                }


            },
            object : MqttCallback {
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == "cmd"){
                        val msg = "Receive message: ${message.toString()} from topic: $topic"
                        Log.d(this.javaClass.name, msg)

                        //Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

                        val messageArr = message.toString().split(',').toTypedArray()
                        if (messageArr[0] == "already_to_take_one_picture"){
                            TakePicture()
                        }

                        if (messageArr[0] == "skidJoy"){
                            val x = messageArr[1].toFloat()
                            val y = messageArr[2].toFloat()

                            skidJoy(x, y)
                        }
                        if (messageArr[0] == "goToPosition"){
                            val x = messageArr[1].toFloat()
                            val y = messageArr[2].toFloat()
                            val yaw = messageArr[3].toFloat()
                            goToPosition(x, y, yaw)
                        }
                        if (messageArr[0] == "tiltAngle"){
                            val x = messageArr[1].toInt()
                            robot.tiltAngle(x)
                        }
                        if (messageArr[0] == "loadMap"){
                            sendMap()
                        }

                        //game start

                        if (messageArr[0] == "origin_position"){
                            music(R.raw.startsong, 13000)
                        }

                        if (messageArr[0] == "countdown"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.GONE
                            var g = findViewById<Button>(R.id.yes)
                            g.visibility = View.GONE
                            var h = findViewById<Button>(R.id.no)
                            h.visibility = View.GONE
                            var i = findViewById<LinearLayout>(R.id.yesorno)
                            i.visibility = View.GONE
                            var d = findViewById<ImageView>(R.id.imageView2) as ImageView
                            val width = 1920
                            val height = 1080
                            val parms = LinearLayout.LayoutParams(width, height)
                            d.setLayoutParams(parms)
                            if(messageArr[1] == "3"){
                                picture("num3")
                                music(R.raw.countdown3, 1500)
                            }
                            else if(messageArr[1] == "2"){
                                picture("num2")
                                music(R.raw.countdown2, 1500)
                            }
                            else if(messageArr[1] == "1"){
                                picture("num1")
                                music(R.raw.countdown1, 2300)
                            }
                        }
                        if (messageArr[0] == "register_done"){
                            var b = findViewById<Button>(R.id.Register)
                            b.visibility = View.VISIBLE
                            b.setText("Next")
                        }

                        if (messageArr[0] == "imitate"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.GONE
                            var g = findViewById<Button>(R.id.yes)
                            g.visibility = View.GONE
                            var h = findViewById<Button>(R.id.no)
                            h.visibility = View.GONE
                            var i = findViewById<LinearLayout>(R.id.yesorno)
                            i.visibility = View.GONE
                            var d = findViewById<ImageView>(R.id.imageView2) as ImageView
                            val width = 1920
                            val height = 1080
                            val parms = LinearLayout.LayoutParams(width, height)
                            d.setLayoutParams(parms)

                            picture(messageArr[1])
                            music(R.raw.num54321, 5200)
                        }

                        if (messageArr[0] == "turnback"){
                            turnBy(-180,1.0)
                            picture("turnback")
                            music(R.raw.turnback, 6000)
                        }

                        if (messageArr[0] == "turn"){
                            turnBy(180,1.0)
                            picture("turn")
                            music(R.raw.gamesong, 5000)
                        }

                        if (messageArr[0] == "scan"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.GONE
                            picture("detect")
                            music(R.raw.detect, 7000)
                        }

                        if (messageArr[0] == "no_face"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.VISIBLE
                            c.text = "Please be CLOSER !!!"
                        }

                        if (messageArr[0] == "yes_face"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.VISIBLE
                            c.text = "I think this is " + messageArr[1]
                        }

                        if (messageArr[0] == "out"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.VISIBLE
                            c.text = messageArr[1] + " OUT !!!"
                            var random = Random().nextInt()
                            if(messageArr[1] == "Nobody"){
                                picture("nobody")
                            }
                            else{
                                if(random % 2 == 0){
                                    picture("gun")
                                    music(R.raw.gun1, 2500)
                                }
                                else{
                                    picture("dog")
                                    music(R.raw.dog, 2500)
                                }
                            }
                        }

                        if (messageArr[0] == "scan_another"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.GONE
                            Thread.sleep(3000)
                            picture("detect")
                        }

                        if (messageArr[0] == "end"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.VISIBLE
                            c.text = messageArr[1] + " WIN !!!"
                            picture("money")
                            music(R.raw.end, 5000)
                        }

                        if (messageArr[0] == "play_again"){
                            var c = findViewById<TextView>(R.id.textView)
                            c.visibility = View.GONE
                            var i = findViewById<LinearLayout>(R.id.yesorno)
                            i.visibility = View.VISIBLE
                            var g = findViewById<Button>(R.id.yes)
                            g.visibility = View.VISIBLE
                            var h = findViewById<Button>(R.id.no)
                            h.visibility = View.VISIBLE
                            var d = findViewById<ImageView>(R.id.imageView2) as ImageView
                            val width = 1920
                            val height = 720
                            val parms = LinearLayout.LayoutParams(width, height)
                            d.setLayoutParams(parms)
                            picture("play_again")
                            music(R.raw.end, 5000)
                        }


                        if (messageArr[0] == "return_initial"){
                            ReturnInitialscreen()
                        }
                    }
                    else if (topic == "imagestream"){
                        val pv = findViewById<View>(R.id.previewView) as PreviewView
                        val width = 1
                        val height = 1
                        val parms = LinearLayout.LayoutParams(width, height)
                        pv.setLayoutParams(parms)

                        var d = findViewById<View>(R.id.imageView2)
                        d.visibility = View.VISIBLE
                        val width2 = 1920
                        val height2 = 1080
                        val parms2 = LinearLayout.LayoutParams(width2, height2)
                        d.setLayoutParams(parms2)

                        val bytearray = message!!.payload.toUByteArray().toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(bytearray, 0, bytearray.size)
                        imageView2.setImageBitmap(bmp)
                    }
                }
                override fun connectionLost(cause: Throwable?) {
                    Log.d(this.javaClass.name, "Connection lost ${cause.toString()}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d(this.javaClass.name, "Delivery complete")
                }
            })
//        var subscribed = false
//        while (!subscribed) {
//            subscribed = subscribeCMD()
//        }
    }

    private fun subscribeCMD(): Boolean{
        if (mqttEn) {
            if (mqttClient.isConnected()) {
                mqttClient.subscribe("cmd",
                    1,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            val msg = "Subscribed to: cmd"
                            Log.d(this.javaClass.name, msg)

                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Log.d(this.javaClass.name, "Failed to subscribe: cmd")
                        }
                    })
                mqttClient.subscribe("imagestream",
                    1,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            val msg = "Subscribed to: imagestream"
                            Log.d(this.javaClass.name, msg)

                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Log.d(this.javaClass.name, "Failed to subscribe: imagestream")
                        }
                    })
            } else {
                Log.d(this.javaClass.name, "Impossible to subscribe, no server connected")
                return false
            }
        }
        return true
    }

    private fun publishMessage(topic: String, message: ByteArray){
        if (mqttEn) {
            if (mqttClient.isConnected()) {
                mqttClient.publish(topic,
                    message,
                    1,
                    false,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            val msg ="Publish message: $message to topic: $topic"
                            Log.d(this.javaClass.name, msg)

                            //Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.d(this.javaClass.name, "Failed to publish message to topic")
                        }
                    })
            }
        }
    }
    /**
     * MQTT PART END
     */

    private fun sendMap(){
        singleThreadExecutor.execute {
            val mapDataModel = Robot.getInstance().getMapData() ?: return@execute
            val mapImage = mapDataModel!!.mapImage
            // Log.i("Map-mapImage", mapDataModel!!.mapImage.typeId)
            val mapInfo = mapDataModel!!.mapInfo
            val mapId = mapDataModel!!.mapId
            var mapdata = MapDataModel(mapImage, mapId, mapInfo)

            val jsonString = Gson().toJson(mapdata)
            // Log.i("Map-mapImage", jsonString)

            val sendData = jsonString.toByteArray(Charsets.UTF_8);
            publishMessage("map",sendData)
        }
    }

    /**
     * Camera Part START
     */
    //內部subclass，專門在其他執行續處理socket的傳輸，這樣就不會盯著螢幕矇逼幾小時ㄌ
    private fun startCamera() {
        val cameraFuture = ProcessCameraProvider.getInstance(this)
        cameraFuture.addListener(
            Runnable {
                try {
                    val cameraProvider: ProcessCameraProvider = cameraFuture.get()
                    bindImageAnalysis(cameraProvider)

                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindImageAnalysis(provider: ProcessCameraProvider) {
        val preview = findViewById<PreviewView>(R.id.previewView)
        val ana = ImageAnalysis.Builder().setTargetResolution(Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        ana.setAnalyzer(ContextCompat.getMainExecutor(this),
            ImageAnalysis.Analyzer { image ->

                val planes = image.planes
                val yBuf = planes[0].buffer
                val vuBuf = planes[2].buffer
                val ySize = yBuf.remaining()
                val vuSize = vuBuf.remaining()
                val nv21 = ByteArray(ySize + vuSize)
                yBuf.get(nv21, 0, ySize)
                vuBuf.get(nv21, ySize, vuSize)
                val img = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                img.compressToJpeg(Rect(0, 0, img.width, img.height), 75, out)
                val mybytearray = out.toByteArray()

                //啟動新的執行續，丟入圖片位元陣列~
                publishMessage("image",mybytearray)
                image.close()
            })
        val prev = Preview.Builder().build()
        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        prev.setSurfaceProvider(preview.surfaceProvider);
        provider.bindToLifecycle((this as LifecycleOwner)!!, selector, ana, prev)
    }
    /**
     * Camera Part END
     */

    private fun getSupportedLatinKeyboards() {
        val supportedLatinKeyboards = robot.getSupportedLatinKeyboards()
        var count = 0
        supportedLatinKeyboards.iterator()
            .forEach {
                printLog("No.${++count} Latin keyboard: ${it.key}, enabled: ${it.value}")
            }
    }

    private fun enabledLatinKeyboards() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        /**
         * list should be got from the keys of the map via method [com.robotemi.sdk.Robot.getSupportedLatinKeyboards]
         */
        robot.enabledLatinKeyboards(robot.getSupportedLatinKeyboards().keys.toList().subList(0, 5))
    }

    private fun isKioskModeOn() {
        printLog("Is kiosk mode on: ${robot.isKioskModeOn()}")
    }

    private fun toggleKiosk() {
        robot.setKioskModeOn(!robot.isKioskModeOn())
    }

    private fun getMode() {
        printLog("System mode: ${robot.getMode()}")
    }

    private fun setMode() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val modes: MutableList<Mode> = ArrayList()
        modes.add(Mode.DEFAULT)
        modes.add(Mode.GREET)
        modes.add(Mode.PRIVACY)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, modes)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Mode")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.setMode(adapter.getItem(position)!!)
                printLog("Set Mode to: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun getNickName() {
        printLog("temi's nick name: ${robot.getNickName()}")
    }

    private fun toggleHardBtnVolume() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val currentMode = robot.getHardButtonMode(HardButton.VOLUME)
        robot.setHardButtonMode(
            HardButton.VOLUME,
            if (currentMode == HardButton.Mode.ENABLED) HardButton.Mode.DISABLED
            else HardButton.Mode.ENABLED
        )
        printLog("Set hard button volume: ${robot.getHardButtonMode(HardButton.VOLUME)}")
    }

    private fun toggleHardBtnPower() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val currentMode = robot.getHardButtonMode(HardButton.POWER)
        robot.setHardButtonMode(
            HardButton.POWER,
            if (currentMode == HardButton.Mode.ENABLED) HardButton.Mode.DISABLED
            else HardButton.Mode.ENABLED
        )
        printLog("Set hard button power: ${robot.getHardButtonMode(HardButton.POWER)}")
    }

    private fun setHardBtnMainMode() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val modes: MutableList<HardButton.Mode> = ArrayList()
        modes.add(HardButton.Mode.ENABLED)
        modes.add(HardButton.Mode.DISABLED)
        modes.add(HardButton.Mode.MAIN_BLOCK_FOLLOW)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, modes)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Main Hard Button Mode")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.setHardButtonMode(HardButton.MAIN, adapter.getItem(position)!!)
                printLog("Set Main Hard Button Mode to: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun setSoundMode() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val soundModes: MutableList<SoundMode> = ArrayList()
        soundModes.add(SoundMode.NORMAL)
        soundModes.add(SoundMode.VIDEO_CALL)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, soundModes)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Sound Mode")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.setSoundMode(adapter.getItem(position)!!)
                printLog("Set Sound Mode to: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Places this application in the top bar for a quick access shortcut.
     */
    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            try {
                val activityInfo =
                    packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
                // Robot.getInstance().onStart() method may change the visibility of top bar.
                robot.onStart(activityInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * Have the robot speak while displaying what is being said.
     */
    private fun speak() {
        val text = etSpeak.text.toString()
        val languages = ArrayList<TtsRequest.Language>()
        languages.add(TtsRequest.Language.SYSTEM)
        languages.add(TtsRequest.Language.EN_US)
        languages.add(TtsRequest.Language.ZH_CN)
        languages.add(TtsRequest.Language.ZH_HK)
        languages.add(TtsRequest.Language.ZH_TW)
        languages.add(TtsRequest.Language.TH_TH)
        languages.add(TtsRequest.Language.HE_IL)
        languages.add(TtsRequest.Language.KO_KR)
        languages.add(TtsRequest.Language.JA_JP)
        languages.add(TtsRequest.Language.IN_ID)
        languages.add(TtsRequest.Language.ID_ID)
        languages.add(TtsRequest.Language.DE_DE)
        languages.add(TtsRequest.Language.FR_FR)
        languages.add(TtsRequest.Language.FR_CA)
        languages.add(TtsRequest.Language.PT_BR)
        languages.add(TtsRequest.Language.AR_EG)
        languages.add(TtsRequest.Language.AR_AE)
        languages.add(TtsRequest.Language.AR_XA)
        languages.add(TtsRequest.Language.RU_RU)
        languages.add(TtsRequest.Language.IT_IT)
        languages.add(TtsRequest.Language.PL_PL)
        languages.add(TtsRequest.Language.ES_ES)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, languages)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Speaking Language")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val ttsRequest = create(text, language = adapter.getItem(position)!!)
                robot.speak(ttsRequest)
                printLog("Speak: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
        hideKeyboard()
    }

    /**
     * This is an example of saving locations.
     */
    private fun saveLocation() {
        val location =
            etSaveLocation.text.toString().toLowerCase(Locale.getDefault()).trim { it <= ' ' }
        val result = robot.saveLocation(location)
        if (result) {
            robot.speak(create("I've successfully saved the $location location.", true))
        } else {
            robot.speak(create("Saved the $location location failed.", true))
        }
        hideKeyboard()
    }

    /**
     * goTo checks that the location sent is saved then goes to that location.
     */
    private fun goTo() {
        for (location in robot.locations) {
            if (location == etGoTo.text.toString().toLowerCase(Locale.getDefault())
                    .trim { it <= ' ' }
            ) {
                robot.goTo(
                    etGoTo.text.toString().toLowerCase(Locale.getDefault()).trim { it <= ' ' })
                hideKeyboard()
            }
        }
    }

    /**
     * stopMovement() is used whenever you want the robot to stop any movement
     * it is currently doing.
     */
    private fun stopMovement() {
        robot.stopMovement()
        robot.speak(create("And so I have stopped", true))
    }

    /**
     * Simple follow me example.
     */
    private fun followMe() {
        robot.beWithMe()
        hideKeyboard()
    }

    /**
     * Manually navigate the robot with skidJoy, tiltAngle, turnBy and tiltBy.
     * skidJoy moves the robot exactly forward for about a second. It controls both
     * the linear and angular velocity. Float numbers must be between -1.0 and 1.0
     */
    private fun skidJoy() {
        val t = System.currentTimeMillis()
        val end = t + 500
        val speedX = try {
            etX.text.toString().toFloat()
        } catch (e: Exception) {
            1f
        }
        val speedY = try {
            etY.text.toString().toFloat()
        } catch (e: Exception) {
            0f
        }
        printLog("speedX: $speedX, speedY: $speedY")
        while (System.currentTimeMillis() < end) {
            robot.skidJoy(speedX, speedY)
        }
    }
    private fun skidJoy(x: Float, y: Float) {
        val t = System.currentTimeMillis()
        val end = t + 8
        printLog("speedX: $x, speedY: $y")
        while (System.currentTimeMillis() < end) {
            robot.skidJoy(x, y)
        }
    }

    /**
     * tiltAngle controls temi's head by specifying which angle you want
     * to tilt to and at which speed.
     */
    private fun tiltAngle() {
        val speed = try {
            etDistance.text.toString().toInt()
        } catch (e: Exception) {
            1
        }
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(applicationContext, etDistance.text.toString(), duration)
        toast.show()
        robot.tiltAngle(speed, 0.5f)
    }

    /**
     * turnBy allows for turning the robot around in place. You can specify
     * the amount of degrees to turn by and at which speed.
     */
    private fun turnBy() {
        val speed = try {
            etDistance.text.toString().toFloat()
        } catch (e: Exception) {
            1f
        }
        robot.turnBy(90, speed)
    }
    private fun turnBy(deg: Int, speed: Double) {
        robot.turnBy(deg, speed.toFloat())
    }

    /**
     * tiltBy is used to tilt temi's head from its current position.
     */
    private fun tiltBy() {
        val speed = try {
            etDistance.text.toString().toFloat()
        } catch (e: Exception) {
            1f
        }
        robot.tiltBy(70, speed)
    }

    /**
     * getBatteryData can be used to return the current battery status.
     */
    private fun getBatteryData() {
        val batteryData = robot.batteryData
        if (batteryData == null) {
            printLog("getBatteryData()", "batteryData is null")
            return
        }
        if (batteryData.isCharging) {
            val ttsRequest =
                create(batteryData.level.toString() + " percent battery and charging.", true)
            robot.speak(ttsRequest)
        } else {
            val ttsRequest =
                create(batteryData.level.toString() + " percent battery and not charging.", true)
            robot.speak(ttsRequest)
        }
    }

    /**
     * Display the saved locations in a dialog
     */
    private fun savedLocationsDialog() {
        hideKeyboard()
        val locations = robot.locations.toMutableList()
        val locationAdapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, locations)
        val versionsDialog = AlertDialog.Builder(this@MainActivity)
        versionsDialog.setTitle("Saved Locations: (Click to delete the location)")
        versionsDialog.setPositiveButton("OK", null)
        versionsDialog.setAdapter(locationAdapter, null)
        val dialog = versionsDialog.create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setMessage("Delete location \"" + locationAdapter.getItem(position) + "\" ?")
                builder.setPositiveButton("No thanks") { _: DialogInterface?, _: Int -> }
                builder.setNegativeButton("Yes") { _: DialogInterface?, _: Int ->
                    val location = locationAdapter.getItem(position) ?: return@setNegativeButton
                    val result = robot.deleteLocation(location)
                    if (result) {
                        locations.removeAt(position)
                        robot.speak(create(location + "delete successfully!", false))
                        locationAdapter.notifyDataSetChanged()
                    } else {
                        robot.speak(create(location + "delete failed!", false))
                    }
                }
                val deleteDialog: Dialog = builder.create()
                deleteDialog.show()
            }
        dialog.show()
    }

    /**
     * When adding the Nlp Listener to your project you need to implement this method
     * which will listen for specific intents and allow you to respond accordingly.
     *
     *
     * See AndroidManifest.xml for reference on adding each intent.
     */
    override fun onNlpCompleted(nlpResult: NlpResult) {
        //do something with nlp result. Base the action specified in the AndroidManifest.xml
        Toast.makeText(this@MainActivity, nlpResult.action, Toast.LENGTH_SHORT).show()
        printLog("NlpCompleted: $nlpResult")
        when (nlpResult.action) {
            ACTION_HOME_WELCOME -> robot.tiltAngle(23)
            ACTION_HOME_DANCE -> {
                val t = System.currentTimeMillis()
                val end = t + 5000
                while (System.currentTimeMillis() < end) {
                    robot.skidJoy(0f, 1f)
                }
            }
            ACTION_HOME_SLEEP -> robot.goTo(HOME_BASE_LOCATION)
        }
    }

    /**
     * callOwner is an example of how to use telepresence to call an individual.
     */
    private fun callOwner() {
        val admin = robot.adminInfo
        if (admin == null) {
            printLog("callOwner()", "adminInfo is null.")
            return
        }
        robot.startTelepresence(admin.name, admin.userId)
    }

    /**
     * publishToActivityStream takes an image stored in the resources folder
     * and uploads it to the mobile application under the Activities tab.
     */
    private fun publishToActivityStream() {
        val activityStreamObject: ActivityStreamObject
        val fileName = "puppy.png"
        //val bm = BitmapFactory.decodeResource(resources, R.drawable.puppy)
        val puppiesFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            fileName
        )
        val fileOutputStream: FileOutputStream
        try {
            fileOutputStream = FileOutputStream(puppiesFile)
            //bm.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activityStreamObject = ActivityStreamObject.builder()
            .activityType(ActivityStreamObject.ActivityType.PHOTO)
            .title("Puppy")
            .media(MediaObject.create(MediaObject.MimeType.IMAGE, puppiesFile))
            .build()
        try {
            robot.shareActivityObject(activityStreamObject)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        robot.speak(create("Uploading Image", false))
    }

    private fun hideTopBar() {
        robot.hideTopBar()
    }

    private fun showTopBar() {
        robot.showTopBar()
    }

    override fun onWakeupWord(wakeupWord: String, direction: Int) {
        // Do anything on wakeup. Follow, go to location, or even try creating dance moves.
        printLog("onWakeupWord", "$wakeupWord, $direction")
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        // Do whatever you like upon the status changing. after the robot finishes speaking
        printLog("onTtsStatusChanged: $ttsRequest")
    }

    override fun onBeWithMeStatusChanged(status: String) {
        //  When status changes to "lock" the robot recognizes the user and begin to follow.
        printLog("BeWithMeStatus: $status")
    }

    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        printLog("GoToStatusChanged: status=$status, descriptionId=$descriptionId, description=$description")
//        robot.speak(create(status, false))
//        if (description.isNotBlank()) {
//            robot.speak(create(description, false))
//        }
        var msg = "goToStatus,$status,$descriptionId,$description"
        publishMessage("status",msg.toByteArray())
        if(status == "complete" && moving){
            moving = false
        }
    }

    override fun onConversationAttaches(isAttached: Boolean) {
        //Do something as soon as the conversation is displayed.
        printLog("onConversationAttaches", "isAttached:$isAttached")
    }

    override fun onPublish(message: ActivityStreamPublishMessage) {
        //After the activity stream finished publishing (photo or otherwise).
        //Do what you want based on the message returned.
        robot.speak(create("Uploaded.", false))
    }

    override fun onLocationsUpdated(locations: List<String>) {
        //Saving or deleting a location will update the list.
        printLog("Locations updated :\n$locations")
    }

    private fun disableWakeup() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.toggleWakeup(true)
    }

    private fun enableWakeup() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.toggleWakeup(false)
    }

    private fun toggleNavBillboard() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.toggleNavigationBillboard(!robot.navigationBillboardDisabled)
    }

    override fun onConstraintBeWithStatusChanged(isConstraint: Boolean) {
        printLog("onConstraintBeWith", "status = $isConstraint")
    }

    override fun onDetectionStateChanged(state: Int) {
        printLog("onDetectionStateChanged: state = $state")
    }

    /**
     * If you want to cover the voice flow in Launcher OS,
     * please add following meta-data to AndroidManifest.xml.
     * <pre>
     * <meta-data android:name="com.robotemi.sdk.metadata.KIOSK" android:value="true"></meta-data>
     *
     * <meta-data android:name="com.robotemi.sdk.metadata.OVERRIDE_NLU" android:value="true"></meta-data>
     * <pre>
     * And also need to select this App as the Kiosk Mode App in Settings > App > Kiosk.
     *
     * @param asrResult The result of the ASR after waking up temi.
    </pre></pre> */
    override fun onAsrResult(asrResult: String) {

        printLog("onAsrResult", "asrResult = $asrResult")
        //Toast.makeText(applicationContext, "asrResult = $asrResult", Toast.LENGTH_SHORT).show()
        var msg = "onAsrResult,$asrResult"
        publishMessage("status",msg.toByteArray())
        try {
            val metadata = packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData ?: return
            if (!robot.isSelectedKioskApp()) return
            if (!metadata.getBoolean(SdkConstants.METADATA_OVERRIDE_NLU)) return
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return
        }
        if(asrResult.length==0) return

        var command=asrResult
        var nextCommand=""
        if (asrResult.contains("然後")) {
            var index = asrResult.indexOf("然後")
            command = asrResult.substring(0,index)
            nextCommand = asrResult.substring(index+2)
        }

        when {
            command.equals("Hello", ignoreCase = true) -> {
                robot.askQuestion("Hello, I'm temi, what can I do for you?")
            }
            command.equals("Take picture", ignoreCase = true) -> {
                robot.tiltAngle(0)
                robot.askQuestion("OK!")
                robot.finishConversation()
                TakePicture()
            }
            command.equals("Play music", ignoreCase = true) -> {
                robot.finishConversation()
                robot.speak(create("Okay, please enjoy.", false))
                playMusic()
            }
            command.equals("Play movie", ignoreCase = true) -> {
                robot.finishConversation()
                robot.speak(create("Okay, please enjoy.", false))
                playMovie()
            }

            command.toLowerCase(Locale.getDefault()).contains("拍照") -> {
                robot.finishConversation()
                robot.goToPosition(Position((0.094).toFloat(), (-3.21).toFloat(), (3.14159).toFloat()))
                moving = true
                singleThreadExecutor.execute{
                    while(moving){
                        Thread.sleep(100)
                    }
                    var s = "ready_to_take_one_picture"
                    publishMessage("game",s.toByteArray())
                }

            }

            command.toLowerCase(Locale.getDefault()).contains("回")||
            command.toLowerCase(Locale.getDefault()).contains("去") -> {
                var targettext = ""
                if(command.toLowerCase(Locale.getDefault()).contains("充電")){
//                    robot.askQuestion("OK!")
                    robot.finishConversation()
//                    Log.e("print: ", robot.locations.toString())
                    robot.goTo("home base")
                }
                if(command.toLowerCase(Locale.getDefault()).contains("幫我")){
                    var textposition = command.indexOf("幫我")
                    targettext = command.substring(textposition+2)

                }
                if(command.toLowerCase(Locale.getDefault()).contains("客廳")){
//                    robot.askQuestion("OK!")
                    robot.finishConversation()
                    robot.goToPosition(Position((0.4).toFloat(), (-0.87).toFloat(), (0).toFloat()))
                    moving = true
                    singleThreadExecutor.execute{
                        while(moving){
                            Thread.sleep(100)
                        }
                        if(targettext.length > 0){
                            robot.askQuestion("我想"+targettext)
                            robot.finishConversation()
                            Thread.sleep(3000)
                        }
                        onAsrResult(nextCommand)
                    }
                }

                if(command.toLowerCase(Locale.getDefault()).contains("餐廳")){
//                    robot.askQuestion("OK!")
                    robot.finishConversation()
                    robot.goToPosition(Position((0.53).toFloat(), (-5.89).toFloat(), (0).toFloat()))
                    moving = true
                    singleThreadExecutor.execute{
                        while(moving){
                            Thread.sleep(100)
                        }
                        if(targettext.length > 0){
                            robot.askQuestion("我想"+targettext)
                            robot.finishConversation()
                            Thread.sleep(3000)
                        }
                        onAsrResult(nextCommand)
                    }
                }

                if(command.toLowerCase(Locale.getDefault()).contains("工作室")){
//                    robot.askQuestion("OK!")
                    robot.finishConversation()
                    robot.goToPosition(Position((5.64).toFloat(), (-2.29).toFloat(), (1).toFloat()))
                    moving = true
                    singleThreadExecutor.execute{
                        while(moving){
                            Thread.sleep(100)
                        }
                        if(targettext.length > 0){
                            robot.askQuestion("我想"+targettext)
                            robot.finishConversation()
                            Thread.sleep(3000)
                        }
                        onAsrResult(nextCommand)
                    }
                }

                else if(command.toLowerCase(Locale.getDefault()).contains("房間")){
//                    robot.askQuestion("OK!")
                    robot.finishConversation()
                    robot.goToPosition(Position((6.3).toFloat(), (-4.0).toFloat(), (-1).toFloat()))
                    moving = true
                    singleThreadExecutor.execute{
                        while(moving){
                            Thread.sleep(100)
                        }
                        if(command.contains("起床")){
                            music(R.raw.wake_up, 11900)
                        }
                        else if(targettext.length > 0){
                            robot.askQuestion("我想"+targettext)
                            robot.finishConversation()
                            Thread.sleep(3000)
                        }
                        onAsrResult(nextCommand)

                    }
                }
            }

            command.toLowerCase(Locale.getDefault()).contains("瘋狗")||
            command.toLowerCase(Locale.getDefault()).contains("狗叫") -> {
                robot.finishConversation()
                music(R.raw.dog, 3000)
            }

            command.toLowerCase(Locale.getDefault()).contains("跟")&&
            command.toLowerCase(Locale.getDefault()).contains("我")&&
            command.toLowerCase(Locale.getDefault()).contains("走") -> {
                robot.finishConversation()
                robot.beWithMe()
                if (command.contains("願意")) {
                    robot.askQuestion("我願意跟你到天涯海角")
                    robot.finishConversation()
                }
            }
            else -> {
                robot.askQuestion("蛤")
            }
        }
    }

    private fun playMovie() {
        // Play movie...
        printLog("onAsrResult", "Play movie...")
    }

    private fun playMusic() {
        // Play music...
        printLog("onAsrResult", "Play music...")
    }

    private fun privacyModeOn() {
        robot.privacyMode = true
        printLog("Privacy mode: ${robot.privacyMode}")
    }

    private fun privacyModeOff() {
        robot.privacyMode = false
        printLog("Privacy mode: ${robot.privacyMode}")
    }

    private fun getPrivacyModeState() {
        printLog("Privacy mode: ${robot.privacyMode}")
    }

    private fun isHardButtonsEnabled() {
        printLog("Hard buttons disabled: ${robot.isHardButtonsDisabled}")
    }

    private fun disableHardButtons() {
        robot.isHardButtonsDisabled = true
        printLog("Hard buttons disabled: ${robot.isHardButtonsDisabled}")
    }

    private fun enableHardButtons() {
        robot.isHardButtonsDisabled = false
        printLog("Hard buttons disabled: ${robot.isHardButtonsDisabled}")
    }

    private fun getOSVersion() {
        printLog("LauncherOs: ${robot.launcherVersion}, RoboxVersion: ${robot.roboxVersion}")
    }

    override fun onTelepresenceEventChanged(callEventModel: CallEventModel) {
        printLog("onTelepresenceEvent", callEventModel.toString())
    }

    override fun onRequestPermissionResult(
        permission: Permission,
        grantResult: Int,
        requestCode: Int
    ) {
        val log = String.format("Permission: %s, grantResult: %d", permission.value, grantResult)
        printLog("onRequestPermission", log)
        if (grantResult == Permission.DENIED) {
            return
        }
        when (permission) {
            Permission.FACE_RECOGNITION -> if (requestCode == REQUEST_CODE_FACE_START) {
                robot.startFaceRecognition()
            } else if (requestCode == REQUEST_CODE_FACE_STOP) {
                robot.stopFaceRecognition()
            }
            Permission.SEQUENCE -> when (requestCode) {
                REQUEST_CODE_SEQUENCE_FETCH_ALL -> {
                    getAllSequences()
                }
                REQUEST_CODE_SEQUENCE_PLAY -> {
                    playFirstSequence(true)
                }
                REQUEST_CODE_SEQUENCE_PLAY_WITHOUT_PLAYER -> {
                    playFirstSequence(false)
                }
            }
            Permission.MAP -> if (requestCode == REQUEST_CODE_MAP) {
                getMap()
            } else if (requestCode == REQUEST_CODE_GET_MAP_LIST) {
                getMapList()
            }
            Permission.SETTINGS -> if (requestCode == REQUEST_CODE_START_DETECTION_WITH_DISTANCE) {
                startDetectionWithDistance()
            }
            else -> {
                // no-op
            }
        }
    }

    private fun requestFace() {
        if (robot.checkSelfPermission(Permission.FACE_RECOGNITION) == Permission.GRANTED) {
            printLog("You already had FACE_RECOGNITION permission.")
            return
        }
        val permissions: MutableList<Permission> = ArrayList()
        permissions.add(Permission.FACE_RECOGNITION)
        robot.requestPermissions(permissions, REQUEST_CODE_NORMAL)
    }

    private fun requestMap() {
        if (robot.checkSelfPermission(Permission.MAP) == Permission.GRANTED) {
            printLog("You already had MAP permission.")
            return
        }
        val permissions: MutableList<Permission> = ArrayList()
        permissions.add(Permission.MAP)
        robot.requestPermissions(permissions, REQUEST_CODE_NORMAL)
    }

    private fun requestSettings() {
        if (robot.checkSelfPermission(Permission.SETTINGS) == Permission.GRANTED) {
            printLog("You already had SETTINGS permission.")
            return
        }
        val permissions: MutableList<Permission> = ArrayList()
        permissions.add(Permission.SETTINGS)
        robot.requestPermissions(permissions, REQUEST_CODE_NORMAL)
    }

    private fun requestSequence() {
        if (robot.checkSelfPermission(Permission.SEQUENCE) == Permission.GRANTED) {
            printLog("You already had SEQUENCE permission.")
            return
        }
        val permissions: MutableList<Permission> = ArrayList()
        permissions.add(Permission.SEQUENCE)
        robot.requestPermissions(permissions, REQUEST_CODE_NORMAL)
    }

    private fun requestAll() {
        val permissions: MutableList<Permission> = ArrayList()
        for (permission in Permission.values()) {
            if (robot.checkSelfPermission(permission) == Permission.GRANTED) {
                printLog("You already had $permission permission.")
                continue
            }
            permissions.add(permission)
        }
        robot.requestPermissions(permissions, REQUEST_CODE_NORMAL)
    }

    private fun startFaceRecognition() {
        if (requestPermissionIfNeeded(Permission.FACE_RECOGNITION, REQUEST_CODE_FACE_START)) {
            return
        }
        robot.startFaceRecognition()
    }

    private fun stopFaceRecognition() {
        robot.stopFaceRecognition()
    }

    private fun setGoToSpeed() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val speedLevels: MutableList<String> = ArrayList()
        speedLevels.add(SpeedLevel.HIGH.value)
        speedLevels.add(SpeedLevel.MEDIUM.value)
        speedLevels.add(SpeedLevel.SLOW.value)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, speedLevels)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Go To Speed Level")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.goToSpeed = SpeedLevel.valueToEnum(adapter.getItem(position)!!)
                printLog("Set go to speed to: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun setGoToSafety() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val safetyLevel: MutableList<String> = ArrayList()
        safetyLevel.add(SafetyLevel.HIGH.value)
        safetyLevel.add(SafetyLevel.MEDIUM.value)
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, safetyLevel)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Go To Safety Level")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.navigationSafety = SafetyLevel.valueToEnum(adapter.getItem(position)!!)
                printLog("Set go to safety level to: ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun toggleTopBadge() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.topBadgeEnabled = !robot.topBadgeEnabled
    }

    private fun toggleDetectionMode() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.detectionModeOn = !robot.detectionModeOn
    }

    private fun toggleAutoReturn() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.autoReturnOn = !robot.autoReturnOn
    }

    private fun toggleTrackUser() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        robot.trackUserOn = !robot.trackUserOn
    }

    private fun getVolume() {
        printLog("Current volume is: " + robot.volume)
    }

    private fun setVolume() {
        if (requestPermissionIfNeeded(Permission.SETTINGS, REQUEST_CODE_NORMAL)) {
            return
        }
        val volumeList: List<String> =
            ArrayList(listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"))
        val adapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, volumeList)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set Volume")
            .setAdapter(adapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.volume = adapter.getItem(position)!!.toInt()
                printLog("Set volume to ${adapter.getItem(position)}")
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun requestToBeKioskApp() {
        if (robot.isSelectedKioskApp()) {
            printLog("${getString(R.string.app_name)} was the selected Kiosk App.")
            return
        }
        robot.requestToBeKioskApp()
    }

    private fun startDetectionWithDistance() {
        hideKeyboard()
        if (requestPermissionIfNeeded(
                Permission.SETTINGS,
                REQUEST_CODE_START_DETECTION_WITH_DISTANCE
            )
        ) {
            return
        }
        var distanceStr = etDistance.text.toString()
        if (distanceStr.isEmpty()) distanceStr = "0"
        try {
            val distance = distanceStr.toFloat()
            robot.setDetectionModeOn(true, distance)
            printLog("Start detection mode with distance: $distance")
        } catch (e: Exception) {
            printLog("startDetectionModeWithDistance", e.message ?: "")
        }
    }

    override fun onDistanceToLocationChanged(distances: Map<String, Float>) {
        for (location in distances.keys) {
            printLog(
                "onDistanceToLocation",
                "location:" + location + ", distance:" + distances[location]
            )
        }
    }

    override fun onCurrentPositionChanged(position: Position) {
        printLog("onCurrentPosition", position.toString())
        printLog("onCurrentPosition", position.x.toString()+","+position.y.toString()+","+position.yaw.toString()+","+position.tiltAngle.toString())
        var msg = position.x.toString()+","+position.y.toString()+","+position.yaw.toString()+","+position.tiltAngle.toString()
        publishMessage("position", msg.toByteArray());

    }

    override fun onSequencePlayStatusChanged(status: Int) {
        printLog(String.format("onSequencePlayStatus status:%d", status))
        if (status == OnSequencePlayStatusChangedListener.ERROR
            || status == OnSequencePlayStatusChangedListener.IDLE
        ) {
            robot.showTopBar()
        }
    }

    override fun onRobotLifted(isLifted: Boolean, reason: String) {
        printLog("onRobotLifted: isLifted: $isLifted, reason: $reason")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        hideKeyboard()
        return super.dispatchTouchEvent(ev)
    }

    @CheckResult
    private fun requestPermissionIfNeeded(permission: Permission, requestCode: Int): Boolean {
        if (robot.checkSelfPermission(permission) == Permission.GRANTED) {
            return false
        }
        robot.requestPermissions(listOf(permission), requestCode)
        return true
    }

    override fun onDetectionDataChanged(detectionData: DetectionData) {
        printLog("onDetectionDataChanged", detectionData.toString())
    }

    override fun onUserInteraction(isInteracting: Boolean) {
        printLog("onUserInteraction", "isInteracting:$isInteracting")
    }

    @Volatile
    private var allSequences: List<SequenceModel> = emptyList()

    private fun getAllSequences() {
        if (requestPermissionIfNeeded(Permission.SEQUENCE, REQUEST_CODE_SEQUENCE_FETCH_ALL)) {
            return
        }
        Thread {
            allSequences = robot.getAllSequences(etNlu.text?.split(",") ?: emptyList())
            printLog("allSequences: ${allSequences.size}", false)
            val imageKeys: MutableList<String> = ArrayList()
            for ((_, _, _, imageKey) in allSequences) {
                if (imageKey.isEmpty()) continue
                imageKeys.add(imageKey)
            }
            val pairs = if (imageKeys.isEmpty()) emptyList()
            else robot.getSignedUrlByMediaKey(imageKeys)
            runOnUiThread {
                for (sequenceModel in allSequences) {
                    printLog(sequenceModel.toString())
                }
                for (pair in pairs) {
                    printLog(pair.component2(), false)
                }
            }
        }.start()
    }

    private fun playFirstSequence() {
        if (requestPermissionIfNeeded(Permission.SEQUENCE, REQUEST_CODE_SEQUENCE_PLAY)) {
            return
        }
        playFirstSequence(true)
    }

    private fun playFirstSequenceWithoutPlayer() {
        if (requestPermissionIfNeeded(
                Permission.SEQUENCE,
                REQUEST_CODE_SEQUENCE_PLAY_WITHOUT_PLAYER
            )
        ) {
            return
        }
        playFirstSequence(false)
    }

    private fun playFirstSequence(withPlayer: Boolean) {
        if (!allSequences.isNullOrEmpty()) {
            robot.playSequence(allSequences[0].id, withPlayer)
        }
    }

    private fun getMap() {
        if (requestPermissionIfNeeded(Permission.MAP, REQUEST_CODE_MAP)) {
            return
        }
        startActivity(Intent(this, MapActivity::class.java))
    }

    override fun onFaceRecognized(contactModelList: List<ContactModel>) {
        if (contactModelList.isEmpty()) {
            printLog("onFaceRecognized: User left")
            imageViewFace.visibility = View.GONE
            return
        }

        val imageKey = contactModelList.find { it.imageKey.isNotBlank() }?.imageKey
        if (!imageKey.isNullOrBlank()) {
            showFaceRecognitionImage(imageKey)
        } else {
            imageViewFace.setImageResource(R.drawable.app_icon)
            imageViewFace.visibility = View.VISIBLE
        }

        for (contactModel in contactModelList) {
            if (contactModel.userId.isBlank()) {
                printLog("onFaceRecognized: Unknown face")
            } else {
                printLog("onFaceRecognized: ${contactModel.firstName} ${contactModel.lastName}")
            }
        }
    }

    override fun onContinuousFaceRecognized(contactModelList: List<ContactModel>) {
        if (contactModelList.isEmpty()) {
            printLog("onContinuousFaceRecognized: User left")
            imageViewFace.visibility = View.GONE
            return
        }

        val imageKey = contactModelList.find { it.imageKey.isNotBlank() }?.imageKey
        if (!imageKey.isNullOrBlank()) {
            showFaceRecognitionImage(imageKey)
        } else {
            imageViewFace.setImageResource(R.drawable.app_icon)
            imageViewFace.visibility = View.VISIBLE
        }

        for (contactModel in contactModelList) {
            if (contactModel.userId.isBlank()) {
                printLog("onContinuousFaceRecognized: Unknown face")
            } else {
                printLog("onContinuousFaceRecognized: ${contactModel.firstName} ${contactModel.lastName}")
            }
        }
    }

    private fun showFaceRecognitionImage(mediaKey: String) {
        if (mediaKey.isEmpty()) {
            imageViewFace.setImageResource(R.drawable.app_icon)
            imageViewFace.visibility = View.GONE
            return
        }
        executorService.execute {
            val inputStream =
                robot.getInputStreamByMediaKey(ContentType.FACE_RECOGNITION_IMAGE, mediaKey)
                    ?: return@execute
            runOnUiThread {
                imageViewFace.visibility = View.VISIBLE
                imageViewFace.setImageBitmap(BitmapFactory.decodeStream(inputStream))
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun printLog(msg: String, show: Boolean = true) {
        printLog("", msg, show)
    }

    private fun printLog(tag: String, msg: String, show: Boolean = true) {
        Log.d(if (tag.isEmpty()) "MainActivity" else tag, msg)
        if (!show) return
        runOnUiThread {
            tvLog.gravity = Gravity.BOTTOM
            tvLog.append("· $msg \n")
        }
    }

    private fun clearLog() {
        tvLog.text = ""
    }

    private fun startNlu() {
        robot.startDefaultNlu(etNlu.text.toString())
    }

    override fun onSdkError(sdkException: SdkException) {
        printLog("onSdkError: $sdkException")
    }

    private fun getAllContacts() {
        val allContacts = robot.allContact
        for (userInfo in allContacts) {
            printLog("UserInfo: $userInfo")
        }
    }

    private fun goToPosition() {
        try {
            val x = etX.text.toString().toFloat()
            val y = etY.text.toString().toFloat()
            val yaw = etYaw.text.toString().toFloat()
            robot.goToPosition(Position(x, y, yaw, 0))
        } catch (e: Exception) {
            e.printStackTrace()
            printLog(e.message ?: "")
        }
    }

    private fun goToPosition(x: Float, y: Float, yaw: Float) {
        try {
            robot.goToPosition(Position(x, y, yaw, 0))
        } catch (e: Exception) {
            e.printStackTrace()
            printLog(e.message ?: "")
        }
    }

    override fun onConversationStatusChanged(status: Int, text: String) {
        printLog("Conversation", "status=$status, text=$text")
    }

    override fun onTtsVisualizerWaveFormDataChanged(waveForm: ByteArray) {
        visualizerView.visibility = View.VISIBLE
        visualizerView.updateVisualizer(waveForm)
    }

    override fun onTtsVisualizerFftDataChanged(fft: ByteArray) {
//        Log.d("TtsVisualizer", fft.contentToString())
//        ttsVisualizerView.updateVisualizer(fft);
    }

    private fun startTelepresenceToCenter() {
        val target = robot.adminInfo
        if (target == null) {
            printLog("target is null.")
            return
        }
        robot.startTelepresence(target.name, target.userId, Platform.TEMI_CENTER)
    }

    private fun startPage() {
        val systemPages: MutableList<String> = ArrayList()
        for (page in Page.values()) {
            systemPages.add(page.toString())
        }
        val arrayAdapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, systemPages)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select System Page")
            .setAdapter(arrayAdapter, null)
            .create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                robot.startPage(
                    Page.values()[position]
                )
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun restartTemi() {
        robot.restart()
    }

    private fun getMembersStatus() {
        val memberStatusModels = robot.membersStatus
        for (memberStatusModel in memberStatusModels) {
            printLog(memberStatusModel.toString())
        }
    }

    private fun repose() {
        robot.repose()
    }

    override fun onReposeStatusChanged(status: Int, description: String) {
        printLog("repose status: $status, description: $description")
    }

    override fun onLoadMapStatusChanged(status: Int) {
        printLog("load map status: $status")
    }

    private var mapList: List<MapModel> = ArrayList()
    private fun getMapList() {
        if (requestPermissionIfNeeded(Permission.MAP, REQUEST_CODE_GET_MAP_LIST)) {
            return
        }
        mapList = robot.getMapList()
    }

    private fun loadMap(reposeRequired: Boolean, position: Position?) {
        if (mapList.isEmpty()) {
            getMapList()
        }
        if (robot.checkSelfPermission(Permission.MAP) != Permission.GRANTED) {
            return
        }
        val mapListString: MutableList<String> = ArrayList()
        for (i in mapList.indices) {
            mapListString.add(mapList[i].name)
        }
        val mapListAdapter = ArrayAdapter(this, R.layout.item_dialog_row, R.id.name, mapListString)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Click item to load specific map")
        builder.setAdapter(mapListAdapter, null)
        val dialog = builder.create()
        dialog.listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, pos: Int, _: Long ->
                printLog("Loading map: " + mapList[pos])
                if (position == null) {
                    robot.loadMap(mapList[pos].id, reposeRequired)
                } else {
                    robot.loadMap(mapList[pos].id, reposeRequired, position)
                }
                dialog.dismiss()
            }
        dialog.show()
    }

    private fun getMapListBtn() {
        getMapList()
        for (mapModel in mapList) {
            printLog("Map: $mapModel")
        }
    }

    private fun loadMap() {
        loadMap(false, null)
    }

    override fun onDisabledFeatureListUpdated(disabledFeatureList: List<String>) {
        printLog("Disabled features: $disabledFeatureList")
    }

    private fun lock() {
        robot.locked = true
        printLog("Is temi locked: " + robot.locked)
    }

    private fun unlock() {
        robot.locked = false
        printLog("Is temi locked: " + robot.locked)
    }

    private fun muteAlexa() {
        if (robot.launcherVersion.contains("usa")) {
            printLog("Mute Alexa")
            robot.muteAlexa()
            return
        }
        printLog("muteAlexa() is useful only for Global version")
    }

    private fun shutdown() {
        if (!robot.isSelectedKioskApp()) {
            return
        }
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Shutdown temi?").create()
        builder.setPositiveButton("Yes") { _: DialogInterface?, _: Int ->
            printLog("shutdown")
            robot.shutdown()
        }
        builder.setNegativeButton("No") { _: DialogInterface?, _: Int -> }
        builder.create().show()
    }

    override fun onMovementVelocityChanged(velocity: Float) {
        printLog("Movement velocity: " + velocity + "m/s")
    }

    private fun loadMapWithPosition() {
        loadMapWithPosition(false)
    }

    private fun loadMapWithReposePosition() {
        loadMapWithPosition(true)
    }

    private fun loadMapWithRepose() {
        loadMap(true, null)
    }

    private fun loadMapWithPosition(reposeRequired: Boolean) {
        try {
            val x = etX.text.toString().toFloat()
            val y = etY.text.toString().toFloat()
            val yaw = etYaw.text.toString().toFloat()
            loadMap(true, Position(x, y, yaw, 0))
            val position = Position(x, y, yaw, 0)
            loadMap(reposeRequired, position)
        } catch (e: Exception) {
            e.printStackTrace()
            printLog(e.message ?: "")
        }
    }

    override fun onMovementStatusChanged(type: String, status: String) {
        printLog("Movement response - $type status: $status")
        var msg = "movementStatus,$type,$status"
        publishMessage("status",msg.toByteArray())
    }

    companion object {
        const val ACTION_HOME_WELCOME = "home.welcome"
        const val ACTION_HOME_DANCE = "home.dance"
        const val ACTION_HOME_SLEEP = "home.sleep"
        const val HOME_BASE_LOCATION = "home base"

        // Storage Permissions
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private const val REQUEST_CODE_NORMAL = 0
        private const val REQUEST_CODE_FACE_START = 1
        private const val REQUEST_CODE_FACE_STOP = 2
        private const val REQUEST_CODE_MAP = 3
        private const val REQUEST_CODE_SEQUENCE_FETCH_ALL = 4
        private const val REQUEST_CODE_SEQUENCE_PLAY = 5
        private const val REQUEST_CODE_START_DETECTION_WITH_DISTANCE = 6
        private const val REQUEST_CODE_SEQUENCE_PLAY_WITHOUT_PLAYER = 7
        private const val REQUEST_CODE_GET_MAP_LIST = 8
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        /**
         * Checks if the app has permission to write to device storage
         * If the app does not has permission then the user will be prompted to grant permissions
         */
        fun verifyStoragePermissions(activity: Activity?) {
            // Check if we have write permission
            val permission = ActivityCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
                )
            }
        }

        fun verifyCameraPermissions(activity: Activity?) {
            // Check if we have write permission
            val permission = ActivityCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.CAMERA
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.CAMERA),
                    1
                )
            }
        }
    }

    private var mTtsRequest: TtsRequest? = null
    override fun speak(ttsRequest: TtsRequest) {
        printLog("custom tts speak --> ttsRequest=$ttsRequest")
        mTtsRequest = ttsRequest
        val language = when (TtsRequest.Language.valueToEnum(ttsRequest.language)) {
            TtsRequest.Language.ZH_CN -> Locale.SIMPLIFIED_CHINESE
            TtsRequest.Language.EN_US -> Locale.US
            TtsRequest.Language.ZH_HK -> Locale("zh", "HK")
            TtsRequest.Language.ZH_TW -> Locale("zh", "Taiwan")
            TtsRequest.Language.TH_TH -> Locale("th", "TH")
            TtsRequest.Language.HE_IL -> Locale("iw", "IL")
            TtsRequest.Language.KO_KR -> Locale("ko", "ka")
            TtsRequest.Language.JA_JP -> Locale("ja", "JP")
            TtsRequest.Language.IN_ID -> Locale("in", "ID")
            TtsRequest.Language.ID_ID -> Locale("in", "ID")
            TtsRequest.Language.DE_DE -> Locale("de", "DE")
            TtsRequest.Language.FR_FR -> Locale("fr", "FR")
            TtsRequest.Language.FR_CA -> Locale("fr", "CA")
            TtsRequest.Language.PT_BR -> Locale("pt", "BR")
            TtsRequest.Language.AR_EG -> Locale("ar", "EG")
            TtsRequest.Language.AR_AE -> Locale("ar", "AE")
            TtsRequest.Language.AR_XA -> Locale("ar", "XA")
            TtsRequest.Language.RU_RU -> Locale("ru", "RU")
            TtsRequest.Language.IT_IT -> Locale("it", "IT")
            TtsRequest.Language.PL_PL -> Locale("pl", "PL")
            TtsRequest.Language.ES_ES -> Locale("ES", "ES")
            else -> if (robot.launcherVersion.contains("china")) {
                Locale.SIMPLIFIED_CHINESE
            } else {
                Locale.US
            }
        }
        tts?.language = language
        tts?.speak(ttsRequest.speech, TextToSpeech.QUEUE_ADD, Bundle(), ttsRequest.id.toString())
    }

    override fun cancel() {
        printLog("custom tts cancel")
        tts?.stop()
    }

    override fun pause() {
        printLog("custom tts pause")
    }

    override fun resume() {
        printLog("custom tts resume")
    }

    override fun onInit(status: Int) {
        printLog("TTS init status: $status")
        tts?.setPitch(0.5f)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                printLog("custom tts start")
                if (mTtsRequest == null || utteranceId == null) {
                    return
                }
                robot.publishTtsStatus(
                    mTtsRequest!!.copy(
                        id = UUID.fromString(utteranceId),
                        status = TtsRequest.Status.STARTED
                    )
                )
            }

            override fun onDone(utteranceId: String?) {
                printLog("custom tts done")
                if (mTtsRequest == null || utteranceId == null) {
                    return
                }
                robot.publishTtsStatus(
                    mTtsRequest!!.copy(
                        id = UUID.fromString(utteranceId),
                        status = TtsRequest.Status.COMPLETED
                    )
                )
            }

            override fun onError(utteranceId: String?) {
                printLog("custom tts error")
                if (mTtsRequest == null || utteranceId == null) {
                    return
                }
                robot.publishTtsStatus(
                    mTtsRequest!!.copy(
                        id = UUID.fromString(utteranceId),
                        status = TtsRequest.Status.ERROR
                    )
                )
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                super.onAudioAvailable(utteranceId, audio)
                printLog("custom tts error")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                super.onStop(utteranceId, interrupted)
                printLog("custom tts error")
                if (mTtsRequest == null || utteranceId == null) {
                    return
                }
                robot.publishTtsStatus(
                    mTtsRequest!!.copy(
                        id = UUID.fromString(utteranceId),
                        status = TtsRequest.Status.CANCELED
                    )
                )
            }

            override fun onBeginSynthesis(
                utteranceId: String?,
                sampleRateInHz: Int,
                audioFormat: Int,
                channelCount: Int
            ) {
                super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                printLog("custom tts onBeginSynthesis")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                super.onError(utteranceId, errorCode)
                printLog("custom tts onError")
                if (mTtsRequest == null || utteranceId == null) {
                    return
                }
                robot.publishTtsStatus(
                    mTtsRequest!!.copy(
                        id = UUID.fromString(utteranceId),
                        status = TtsRequest.Status.ERROR
                    )
                )
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                printLog("custom tts onRangeStart")
            }

        })
    }
}