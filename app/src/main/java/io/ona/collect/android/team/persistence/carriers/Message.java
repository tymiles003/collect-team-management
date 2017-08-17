package io.ona.collect.android.team.persistence.carriers;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by Jason Rogena - jrogena@ona.io on 17/08/2017.
 */

public class Message implements Serializable {
    private static final DateTimeFormatter TIME_FORMATTER = ISODateTimeFormat.dateTime();
    private static final String KEY_UUID = "uuid";
    private static final String KEY_TIME = "time";
    private static final String KEY_PAYLOAD = "payload";
    public static final int DEFAULT_ID = -1;
    public final long id;
    public final Subscription subscription;
    public final String uuid;
    public final JSONObject payload;
    public final boolean sentByApp;
    public final boolean read;
    public final Date sentAt;
    public final Date receivedAt;

    public Message(long id, Subscription subscription, String uuid, JSONObject payload,
                   boolean sentByApp, boolean read, Date sentAt, Date receivedAt) {
        this.id = id;
        this.subscription = subscription;
        this.uuid = uuid;
        this.payload = payload;
        this.sentByApp = sentByApp;
        this.read = read;
        this.sentAt = sentAt;
        this.receivedAt = receivedAt;
    }

    public Message(Subscription subscription, MqttMessage message, boolean sentByApp, boolean read,
                   Date receivedAt)
            throws JSONException, IllegalArgumentException, UnsupportedOperationException {
        this(
                DEFAULT_ID,
                subscription,
                extractUuid(new String(message.getPayload())),
                extractPayload(new String(message.getPayload())),
                sentByApp,
                read,
                extractSentAt(new String(message.getPayload())),
                receivedAt
        );
    }

    private static String extractUuid(String data) throws JSONException {
        return new JSONObject(data).getString(KEY_UUID);
    }

    private static Date extractSentAt(String data)
            throws JSONException, IllegalArgumentException, UnsupportedOperationException {
        return TIME_FORMATTER.parseDateTime(new JSONObject(data).getString(KEY_TIME)).toDate();
    }

    private static JSONObject extractPayload(String data) throws JSONException {
        return new JSONObject(data).getJSONObject(KEY_PAYLOAD);
    }
}
