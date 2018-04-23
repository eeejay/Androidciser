package org.monotonous.androidciser;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.speech.tts.TextToSpeech.OnInitListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * This class demonstrates how an accessibility service can query
 * window content to improve the feedback given to the user.
 */
public class AndroidciserService extends AccessibilityService implements OnInitListener {
    private static final int PORT = 8080;
    private MyHTTPD miniServer;

    /** Tag for logging. */
    private static final String LOG_TAG = "Androidciser";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected() {
        Log.d(LOG_TAG, "onServiceConnected");
        try {
            miniServer = new MyHTTPD();
            miniServer.start();
            setupNotification();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupNotification() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wm == null) return;
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "androidciser_1")
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle("Androidciser")
                .setContentText("Point your web browser to http://" + ip + ":" + PORT);
        NotificationManager notifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notifyMgr == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("androidciser_1",
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notifyMgr.createNotificationChannel(channel);
        }
        notifyMgr.notify(1, builder.build());
    }

    /**
     * Processes an AccessibilityEvent, by traversing the View's tree and
     * putting together a message to speak to the user.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.d(LOG_TAG, "onAccessibilityEvent");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onInterrupt() {
        /* do nothing */
        Log.d(LOG_TAG, "onInterrupt");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onInit(int status) {
        Log.d(LOG_TAG, "onInit");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        NotificationManager notifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notifyMgr != null)
            notifyMgr.cancelAll();
        miniServer.stop();
        super.onDestroy();
    }

    private class MyHTTPD extends NanoHTTPD {
        private MyHTTPD() throws IOException {
            super(PORT);
        }


        private JSONObject dumpNodeTree() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            try {
                JSONObject rv = dumpNodeTreeInternal(root);
                root.recycle();
                return rv;
            } catch (JSONException e) {
                return new JSONObject();
            }
        }

        private JSONObject nodeToJSON(AccessibilityNodeInfo node) throws JSONException {
            JSONObject rv = new JSONObject();
            rv.put("className", node.getClassName());
            rv.put("text", node.getText());
            rv.put("contentDescription", node.getContentDescription());
            rv.put("viewIdResourceName", node.getViewIdResourceName());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                List<String> extraData = node.getAvailableExtraData();
                if (!extraData.isEmpty()) {
                    rv.put("extraData", new JSONArray(extraData));
                }
            }
            Bundle extrasBundle = node.getExtras();
            Set<String> keys = extrasBundle.keySet();
            if (!keys.isEmpty()) {
                JSONObject extras = new JSONObject();
                for (String key : keys) {
                    extras.put(key, JSONObject.wrap(extrasBundle.get(key)));
                }
                rv.put("extras", extras);
            }

            JSONArray flags = new JSONArray();
            if (node.isAccessibilityFocused()) flags.put("accessibilityFocused");
            if (node.isCheckable()) flags.put("checkable");
            if (node.isChecked()) flags.put("checked");
            if (node.isClickable()) flags.put("clickable");
            if (node.isContentInvalid()) flags.put("contentInvalid");
            if (node.isContextClickable()) flags.put("contextClickable");
            if (node.isDismissable()) flags.put("dismissable");
            if (node.isEditable()) flags.put("editable");
            if (node.isEnabled()) flags.put("enabled");
            if (node.isFocusable()) flags.put("focusable");
            if (node.isFocused()) flags.put("focused");
            if (node.isImportantForAccessibility()) flags.put("importantForAccessibility");
            if (node.isLongClickable()) flags.put("longClickable");
            if (node.isMultiLine()) flags.put("multiline");
            if (node.isPassword()) flags.put("password");
            if (node.isScrollable()) flags.put("scrollable");
            if (node.isSelected()) flags.put("selected");
            if (node.isVisibleToUser()) flags.put("showingHintText");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    node.isShowingHintText()) flags.put("showingHintText");
            rv.put("flags", flags);

            AccessibilityNodeInfo.CollectionInfo collectionInfo = node.getCollectionInfo();
            if (collectionInfo != null) {
                JSONObject obj = new JSONObject();
                obj.put("columnCount", collectionInfo.getColumnCount());
                obj.put("rowCount", collectionInfo.getRowCount());
                obj.put("hierarchical", collectionInfo.isHierarchical());
                switch(collectionInfo.getSelectionMode()) {
                    case AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_MULTIPLE:
                        obj.put("selectionMode", "multiple");
                    case AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE:
                        obj.put("selectionMode", "single");
                    default:
                        obj.put("selectionMode", "none");
                }
                rv.put("collectionInfo", collectionInfo);
            }

            AccessibilityNodeInfo.CollectionItemInfo collectionItem = node.getCollectionItemInfo();
            if (collectionItem != null) {
                JSONObject obj = new JSONObject();
                obj.put("columnIndex", collectionItem.getColumnIndex());
                obj.put("columnSpan", collectionItem.getColumnSpan());
                obj.put("rowIndex", collectionItem.getRowIndex());
                obj.put("rowSpan", collectionItem.getRowSpan());
                obj.put("heading", collectionItem.isHeading());
                obj.put("selected", collectionItem.isSelected());
                rv.put("collectionItemInfo", obj);
            }

            JSONArray bounds = new JSONArray();
            Rect outBounds = new Rect();
            node.getBoundsInScreen(outBounds);
            bounds.put(outBounds.left);
            bounds.put(outBounds.top);
            bounds.put(outBounds.width());
            bounds.put(outBounds.height());
            rv.put("bounds", bounds);

            return rv;
        }

        private JSONObject dumpNodeTreeInternal(AccessibilityNodeInfo node) throws JSONException {
            JSONObject rv = nodeToJSON(node);

            int childCount = node.getChildCount();
            if (childCount > 0) {
                JSONArray children = new JSONArray();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    children.put(dumpNodeTreeInternal(child));
                    child.recycle();
                }
                rv.put("children", children);
            }

            return rv;
        }

        @Override
        public Response serve(IHTTPSession session) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            Log.d(LOG_TAG, "MyHTTPD.serve " + root);
            return newFixedLengthResponse(Response.Status.OK, "application/json", dumpNodeTree().toString());
        }
    }
}
