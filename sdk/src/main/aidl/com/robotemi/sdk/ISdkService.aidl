// ISdkService.aidl
package com.robotemi.sdk;

import android.content.pm.ActivityInfo;
import com.robotemi.sdk.ISdkServiceCallback;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.DisplayListRequest;
import com.robotemi.sdk.activitystream.ActivityStreamObject;
import com.robotemi.sdk.notification.AlertNotification;
import com.robotemi.sdk.notification.NormalNotification;
import com.robotemi.sdk.mediabar.MediaBarData;
import com.robotemi.sdk.UserInfo;
import com.robotemi.sdk.model.RecentCallModel;
import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.sequence.SequenceModel;
import com.robotemi.sdk.navigation.model.Position;
import com.robotemi.sdk.map.MapDataModel;
import com.robotemi.sdk.model.MemberStatusModel;
import com.robotemi.sdk.map.MapModel;

interface ISdkService {

    void speak(in TtsRequest ttsRequest);

    void register(in ApplicationInfo applicationInfo, ISdkServiceCallback callback);

    void shareActivityStreamObject(in ActivityStreamObject activityStreamObject);

    /**
     * Stops currently processed TTS request and empty the queue.
     */
    void cancelAll();

    /**
     * update the media bar data (title, subtitle, icon etc..)
     *
     * @param mediaBarData an object containg the media bar data (title, subtitle, icon etc..).
     */
    void setMedia(in MediaBarData mediaBarData);

    /**
    * pauses the media
    */
    void pause();

    /**
    * Indicate media is playing for specified packageName
    */
    void setMediaPlaying(in boolean isPlaying, in String packageName);

    /**
    * show a notification on top of the screen
    *
    * @param notification - the notification object containg the notification data
    */
    void showNormalNotification(in NormalNotification notification);

    /**
    * show a notification on top of the screen
    *
    * @param notification - the notification object containg the notification data
    */
    void showAlertNotification(in AlertNotification notification);

    /**
    * removes a spesific notification from the screen
    *
    * @param notification - the notification object containg the notification data
    */
    void removeAlertNotification(in AlertNotification notification);

    void onStart(in ActivityInfo activityInfo);

    /**
     * Request to lock contexts even if skill screen is dismissed.
     * Useful for services running in the background without UI.
     *
     * @param contextsToLock - List of contexts names to lock.
     */
    void lockContexts(in List<String> contextsToLock);

    /**
     * Release previously locked contexts. See {@link #lockContexts(List)}.
     *
     * @param contextsToRelease - List of contexts names to release.
     */
    void releaseContexts(in List<String> contextsToRelease);

    /**
     * Send robot to previously saved location.
     *
     * @param location - Saved location name.
     */
    void goTo(in String location);

    /**
     * Retrieve list of previously saved locations.
     *
     * @return List of saved locations.
     */
    List<String> getLocations();

    /**
     * Save location.
     *
     * @param - Location name.
     *
     * @return Result of a successful or failed operation.
     */
    boolean saveLocation(in String name);

    /**
     * Request robot to follow the user.
     */
    void followMe();

    /**
     * Request robot to follow the user.
     */
    void beWithMe();

    void skidJoy(in float x, in float y);

    void turnBy(in int azimuth, in float speed);

    void tiltAngle(in int degrees, in float speed);

    void tiltBy(in int degrees, in float speed);

    String startTelepresence(in String displayName, in String peerId, int platform);

    UserInfo getAdminInfo();

    List<UserInfo> getAllContacts();

    void showAppList();

    List<RecentCallModel> getRecentCalls();

    void showTopBar();

    void hideTopBar();

    /*
     * Request robot to stop any current movement.
     */
    void stopMovement();

    BatteryData getBatteryData();

    String getSerialNumber();

    /**
     * Request robot to toggle the wakeup trigger
     */
     void toggleWakeup(boolean disable, in String packageName);

     /**
     * Request robot to show/hide navigation billboards
     */
     void toggleNavigationBillboard(in boolean hide);

     /**
     * Delete location.
     *
     * @param name - Location name.
     *
     * @return Result of a successful or failed operation.
     */
    boolean deleteLocation(in String name);

    void wakeup();

    String getWakeupWord();

     /**
     * Request robot to turn on/off privacy mode
     */
    void togglePrivacyMode(boolean on, in String packageName);

    boolean getPrivacyModeState();

    void constraintBeWith();

    void toggleHardButtons(boolean disable, in String packageName);

    boolean isHardButtonsDisabled();

    void askQuestion(in String question);

    void finishConversation();

    String getLauncherVersion();

    String getRoboxVersion();

    int checkSelfPermission(in String packageName, in String permission);

    void requestPermissions(in String packageName, in List<String> permissions, int requestCode);

    void requestToBeKioskApp(in String packageName);

    boolean isSelectedKioskApp(in String packageName);

    void setTopBadgeEnabled(in String packageName, boolean enabled);

    boolean isTopBadgeEnabled();

    void setGoToBillboardDisabled(in String packageName, boolean disabled);

    boolean isGoToBillboardDisabled();

    void setDetectionModeOn(in String packageName, boolean on, float distance);

    boolean isDetectionModeOn();

    void setTrackUserOn(in String packageName, boolean on);

    boolean isTrackUserOn();

    void setAutoReturnOn(in String packageName, boolean on);

    boolean isAutoReturnOn();

    void setVolume(in String packageName, int volume);

    int getVolume();

    void setNavigationSafety(in String packageName, in String safetyLevel);

    String getNavigationSafety();

    void setGoToSpeed(in String packageName, in String speedLevel);

    String getGoToSpeed();

    void startFaceRecognition(in String packageName);

    void stopFaceRecognition(in String packageName);

    List<SequenceModel> getAllSequences(in String packageName);

    void playSequence(in String packageName, in String sequenceId, boolean withPlayer);

    void goToPosition(in Position position);

    MapDataModel getMapData(in String packageName);

    void startDefaultNlu(in String packageName, in String content);

    boolean isWakeupDisabled();

    void repose();

    void restart(in String packageName);

    void startPage(in String packageName, in String page);

    List<MemberStatusModel> getMembersStatus();

    List<MapModel> getMapList(in String packageName);

    void loadMap(in String packageName, in String mapId);

    void lock(in String packageName, boolean isForLocking);

    boolean isLocked();

    void muteAlexa(in String packageName);

    void shutdown(in String packageName);
}