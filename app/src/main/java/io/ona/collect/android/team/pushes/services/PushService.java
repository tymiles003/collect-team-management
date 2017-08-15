package io.ona.collect.android.team.pushes.services;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.telephony.TelephonyManager;

import java.io.Serializable;
import java.util.List;

import io.ona.collect.android.team.application.TeamManagement;
import io.ona.collect.android.team.persistence.carriers.Connection;
import io.ona.collect.android.team.persistence.carriers.Subscription;
import io.ona.collect.android.team.persistence.sqlite.databases.tables.ConnectionTable;
import io.ona.collect.android.team.persistence.sqlite.databases.tables.SubscriptionTable;
import io.ona.collect.android.team.pushes.messages.types.PushMessage;

/**
 * Created by Jason Rogena - jrogena@ona.io on 04/08/2017.
 */

public abstract class PushService implements Serializable {
    private static final String TAG = PushService.class.getSimpleName();
    private static final String ANDROID6_FAKE_MAC = "02:00:00:00:00:00";
    protected final Context context;
    protected final String name;
    protected final ConnectionListener connectionListener;
    protected final MessageListener messageListener;

    protected PushService(Context context, String name,
                          ConnectionListener connectionListener, MessageListener messageListener) {
        this.context = context.getApplicationContext();
        this.name = name;
        this.connectionListener = connectionListener;
        this.messageListener = messageListener;
    }

    public String getName() {
        return name;
    }

    protected String getClientId(Connection connection) {
        String deviceId = getDeviceId();
        return connection.username + "_" + deviceId;
    }

    /**
     * This method uses the same logic to determine the device Id used by ODK Collect
     *
     * @return The device id
     */
    private String getDeviceId() {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String deviceId = telephonyManager.getDeviceId();
        String orDeviceId = null;
        if (deviceId != null) {
            if ((deviceId.contains("*") || deviceId.contains("000000000000000"))) {
                deviceId =
                        Settings.Secure.getString(context.getContentResolver(),
                                Settings.Secure.ANDROID_ID);
                orDeviceId = deviceId;
            } else {
                orDeviceId = deviceId;
            }
        }

        if (deviceId == null) {
            // no SIM -- WiFi only
            // Retrieve WiFiManager
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

            // Get WiFi status
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null && !ANDROID6_FAKE_MAC.equals(info.getMacAddress())) {
                deviceId = info.getMacAddress();
                orDeviceId = deviceId;
            }
        }

        // if it is still null, use ANDROID_ID
        if (deviceId == null) {
            deviceId =
                    Settings.Secure
                            .getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            orDeviceId = deviceId;
        }

        return orDeviceId;
    }

    /**
     * Connects to a push service. Assumes that a push service cannot handle more than one
     * connection hence tries to disconnect from any current connection before trying to establish
     * the new connection.
     *
     * @param connection The connection to be established
     * @return {@code true} if able to establish a connection
     * @throws ConnectionException
     */
    public boolean connect(Connection connection) throws ConnectionException {
        try {
            ConnectionTable ct = (ConnectionTable) TeamManagement.getInstance()
                    .getTeamManagementDatabase().getTable(ConnectionTable.TABLE_NAME);
            List<Connection> affectedConnections = ct.addConnection(connection);
            for (Connection curConnection : affectedConnections) {
                if (!connection.equals(curConnection)) {
                    disconnect(curConnection, false);//TODO: check if the child disconnect is called (should be the case)
                }
            }
        } catch (SQLiteException e) {
            throw new ConnectionException(e);
        } catch (PushSystemNotFoundException e) {
            throw new ConnectionException(e);
        }

        return false;
    }

    /**
     * Disconnects from the push service.
     *
     * @param connection The connection to disconnect from
     * @param permanent  {@code true} if you don't want the app to reconnect to the service when the
     *                   device restarts, for instance.
     * @return {@code true} If system was able to successfully disconnect
     * @throws ConnectionException
     */
    public boolean disconnect(Connection connection, boolean permanent) throws ConnectionException {
        if (permanent) {
            try {
                ConnectionTable ct = (ConnectionTable) TeamManagement.getInstance()
                        .getTeamManagementDatabase().getTable(ConnectionTable.TABLE_NAME);
                ct.updateStatus(connection, ConnectionTable.STATUS_INACTIVE);
            } catch (SQLiteException e) {
                throw new ConnectionException(e);
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof PushService) {
            return getName().equals(((PushService) obj).getName());
        }
        return false;
    }

    public boolean subscribe(Subscription subscription)
            throws SubscriptionException {
        try {
            SubscriptionTable st = (SubscriptionTable) TeamManagement.getInstance()
                    .getTeamManagementDatabase().getTable(SubscriptionTable.TABLE_NAME);
            st.addSubscription(subscription);
        } catch (PushSystemNotFoundException e) {
            throw new SubscriptionException(e);
        } catch (SQLiteException e) {
            throw new SubscriptionException(e);
        }

        return true;
    }

    public boolean unsubscribe(Subscription subscription)
            throws SubscriptionException {
        try {
            SubscriptionTable st = (SubscriptionTable) TeamManagement.getInstance()
                    .getTeamManagementDatabase().getTable(SubscriptionTable.TABLE_NAME);
            st.updateStatus(subscription, SubscriptionTable.STATUS_INACTIVE);
        } catch (PushSystemNotFoundException e) {
            throw new SubscriptionException(e);
        } catch (SQLiteException e) {
            throw new SubscriptionException(e);
        }

        return true;
    }

    public interface ConnectionListener {
        void onConnected(Connection connection, boolean status);

        void onDisconnected();

        void onConnectionLost(Connection connection, Throwable throwable);
    }

    public interface MessageListener {
        void onMessageReceived(PushMessage message);

        void onMessageSent(PushMessage message);
    }

    public static class PushSystemNotFoundException extends Exception {
        public PushSystemNotFoundException() {
            super();
        }

        public PushSystemNotFoundException(String message) {
            super(message);
        }

        public PushSystemNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public PushSystemNotFoundException(Throwable cause) {
            super(cause);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        protected PushSystemNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    public static class ConnectionException extends Exception {
        public ConnectionException() {
            super();
        }

        public ConnectionException(String message) {
            super(message);
        }

        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConnectionException(Throwable cause) {
            super(cause);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        protected ConnectionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    public static class SubscriptionException extends Exception {
        public SubscriptionException() {
            super();
        }

        public SubscriptionException(String message) {
            super(message);
        }

        public SubscriptionException(String message, Throwable cause) {
            super(message, cause);
        }

        public SubscriptionException(Throwable cause) {
            super(cause);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        protected SubscriptionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
