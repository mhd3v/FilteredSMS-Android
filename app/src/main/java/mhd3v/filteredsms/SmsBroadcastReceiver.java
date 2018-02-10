package mhd3v.filteredsms;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.text.format.DateFormat;
import android.util.Log;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Mahad on 12/22/2017.
 */

public class SmsBroadcastReceiver extends BroadcastReceiver {
    NotificationManager mNotificationManager;
    public static final String SMS_BUNDLE = "pdus";
    String address;
    String smsBody;
    boolean isContact;
    Cursor cursor;
    NotificationCompat.InboxStyle inboxStyle;
    SQLiteDatabase filteredDatabase;

    boolean messageFromNewSender;
    int blackListStatus;

    private static final String ACTION_SMS_NEW = "android.provider.Telephony.SMS_RECEIVED";


    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();

        if (ACTION_SMS_NEW.equals(action)) {

            Bundle intentExtras = intent.getExtras();

            if (intentExtras != null) {
                Object[] sms = (Object[]) intentExtras.get(SMS_BUNDLE);

                for (int i = 0; i < sms.length; ++i) {
                    String format =  intentExtras.getString("format");
                    SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sms[i], format);

                    smsBody = smsMessage.getMessageBody().toString();
                    address = smsMessage.getOriginatingAddress();

                    String defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context);

                    if (defaultSmsApp.equals("mhd3v.filteredsms")) {

                        ContentValues cv = new ContentValues();
                        cv.put("address", address);
                        cv.put("body", smsBody);
                        context.getContentResolver().insert(Uri.parse("content://sms/inbox"), cv);
                    }

                    ContentValues cv = new ContentValues();

                    filteredDatabase = context.openOrCreateDatabase("filteredDatabase", MODE_PRIVATE, null);
                    filteredDatabase.execSQL("CREATE TABLE IF NOT EXISTS messageTable " +
                            "(thread_id VARCHAR, address VARCHAR, body VARCHAR, type INT" +
                            ", date VARCHAR, date_string VARCHAR, sender VARCHAR, sender_name VARCHAR );");

                    cursor = context.getContentResolver().query(Uri.parse("content://sms"), null, null, null, null);
                    cursor.moveToFirst();

                    String date = cursor.getString(cursor.getColumnIndex("date"));
                    String dateTime = convertDate(date,"yyyy/MM/dd hh:mm:ss");

                    cv.put("thread_id", cursor.getString(cursor.getColumnIndex("thread_id")));
                    cv.put("date", dateTime);
                    cv.put("date_string", date);
                    cv.put("type", 1);
                    cv.put("address", cursor.getString(cursor.getColumnIndex("address")));
                    cv.put("body", cursor.getString(cursor.getColumnIndex("body")));

                    isContact = false;
                    String contactName = getContactName(context, address);

                    ContentValues filteredThreadsCv = new ContentValues();

                    filteredDatabase.execSQL("CREATE TABLE IF NOT EXISTS filteredThreads " +
                            "(thread_id VARCHAR, filtered_status VARCHAR, date_string VARCHAR);");

                    Cursor blackListCheckCursor = filteredDatabase.rawQuery("select blacklisted from filteredThreads where thread_id = " + cursor.getString(cursor.getColumnIndex("thread_id")) +";",null);

                    messageFromNewSender = false;

                    if(!(blackListCheckCursor.moveToFirst())){
                        messageFromNewSender = true;
                    }


                    if (isContact){
                        filteredThreadsCv.put("thread_id",cursor.getString(cursor.getColumnIndex("thread_id")));
                        filteredThreadsCv.put("filtered_status","filtered");
                        filteredThreadsCv.put("date_string", date);

                        if(messageFromNewSender){ //if message from new sender and in contact list
                            filteredThreadsCv.put("blacklisted", 0);
                            blackListStatus = 0;
                        }

                        else{ //message from a contact whose thread already exists, don't update blacklisted column in this case
                            int userSetStatus = blackListCheckCursor.getInt(blackListCheckCursor.getColumnIndex("blacklisted"));
                            filteredThreadsCv.put("blacklisted", userSetStatus);
                            Log.d("userSetStatus", Integer.toString(userSetStatus));
                            blackListStatus = userSetStatus;
                        }

                        filteredDatabase.insert("filteredThreads", null, filteredThreadsCv);

                        cv.put("sender_name", contactName);
                        cv.put("sender", "known");
                    }
                    else {
                        filteredThreadsCv.put("thread_id",cursor.getString(cursor.getColumnIndex("thread_id")));
                        filteredThreadsCv.put("filtered_status","unfiltered");
                        filteredThreadsCv.put("date_string", date);

                        if(messageFromNewSender){ //if message from new sender and not in contact list
                            filteredThreadsCv.put("blacklisted", 1);
                            blackListStatus = 1;
                        }

                        else{ //message from a sender whose thread already exists, don't update blacklisted column in this case
                            int userSetStatus = blackListCheckCursor.getInt(blackListCheckCursor.getColumnIndex("blacklisted"));
                            filteredThreadsCv.put("blacklisted", userSetStatus);
                            blackListStatus = userSetStatus;
                        }

                        filteredDatabase.insert("filteredThreads", null, filteredThreadsCv);

                        cv.put("sender_name", "");
                        cv.put("sender", "unknown");
                    }

                    filteredDatabase.insertOrThrow("messageTable", null, cv);

                    blackListCheckCursor.close();
                    cursor.close();
                    filteredDatabase.close();

                    cursor = context.getContentResolver().query(Uri
                            .parse("content://sms"), null, null, null, null);

                    cursor.moveToFirst();

                    MainActivity mainActivityInstance = MainActivity.inst;
                    CoversationActivity conversationInstance = CoversationActivity.conversationInstance;

                    if (conversationInstance != null) { //conversation thread is active

                        if (conversationInstance.threadId.equals(cursor.getString(cursor.getColumnIndex("thread_id")))) {

                                messages newSms = new messages(smsBody, Long.toString(System.currentTimeMillis()));

                                ArrayList<messages> newMessageList = new ArrayList<>();

                                newMessageList.addAll(conversationInstance.messageList);
                                newMessageList.add(newSms);

                                conversationInstance.adapter.updateMessageList(newMessageList);
                                CoversationActivity.refreshMain();

                                if(!conversationInstance.active)
                                    setNotfication(context);

                        }

                        else{
                            CoversationActivity.refreshMain();
                            setNotfication(context);
                        }

                    }

                    else if (mainActivityInstance != null) {

                        if (mainActivityInstance.active)
                            mainActivityInstance.refreshOnExtraThread();

                        else {

                            mainActivityInstance.refreshInbox = true;
                            setNotfication(context);
                        }
                    }

                    else { //MainActivity not instantiated

                        setNotfication(context);
                    }


                }
            }

        }

    }


    void setNotfication(Context context){

        if(blackListStatus == 0){

            String contactName = getContactName(context, address);

            Intent conversationThreadIntent = new Intent(context, CoversationActivity.class);
            conversationThreadIntent.setAction("android.intent.action.NotificationClicked");
            conversationThreadIntent.putExtra("threadId", cursor.getString(cursor.getColumnIndex("thread_id")));

            if(isContact)
                conversationThreadIntent.putExtra("senderName",contactName);
            else
                conversationThreadIntent.putExtra("senderName","");

            conversationThreadIntent.putExtra("sender",address);

            PendingIntent conversationThreadPendingIntent =
                    PendingIntent.getActivity(
                            context,
                            0,
                            conversationThreadIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );

            inboxStyle = new NotificationCompat.InboxStyle();

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

            SharedPreferences.Editor editor = sp.edit();

            String threadId = cursor.getString(cursor.getColumnIndex("thread_id"));

            editor.putString(threadId, sp.getString(threadId,"")+smsBody+"\n");

            editor.commit();

            String previousNotification = sp.getString(threadId,"");

            String[] result = previousNotification.split("\n");


            for(int i=0; i < result.length; i++){

                if(!(result[i].equals(null)))
                    inboxStyle.addLine(result[i]);
            }

            inboxStyle.setBigContentTitle(contactName);

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(context)
                            .setSmallIcon(R.drawable.main_icon_nobg)
                            .setNumber(result.length)
                            .setContentTitle(contactName)
                            .setContentText(result[result.length-1])
                            .setStyle(inboxStyle)
                            .setContentIntent(conversationThreadPendingIntent)
                            .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);

            int id = Integer.parseInt(cursor.getString(cursor.getColumnIndex("thread_id"))); //assign ID on base of threadID

            mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(id, mBuilder.build());

        }


    }

    public String convertDate(String dateInMilliseconds,String dateFormat) {
        return DateFormat.format(dateFormat, Long.parseLong(dateInMilliseconds)).toString();
    }

    public String getContactName(Context context, String phoneNo) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNo));
        Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor == null) {
            return phoneNo;
        }
        String Name = phoneNo;
        if (cursor.moveToFirst()) {
            isContact = true;
            Name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
        }

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        return Name;
    }

}

