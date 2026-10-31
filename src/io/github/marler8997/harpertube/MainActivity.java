package io.github.marler8997.harpertube;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.TextView;

// Opens HarperTube in Chrome as a Trusted Web Activity: full screen, in
// Chrome's signed-in profile. Chrome hides its URL bar once it verifies the
// site's /.well-known/assetlinks.json lists this app's signing key.
//
// This talks to Chrome's Custom Tabs service over raw Binder calls instead of
// using the androidx.browser library. The transaction codes and descriptors
// come from androidx's ICustomTabsService.aidl and ICustomTabsCallback.aidl.
public class MainActivity extends Activity {
    private static final String URL = "https://harpertube.github.io/";
    private static final String CHROME = "com.android.chrome";

    private static final String SERVICE_ACTION = "android.support.customtabs.action.CustomTabsService";
    private static final String SERVICE_DESCRIPTOR = "android.support.customtabs.ICustomTabsService";
    private static final String CALLBACK_DESCRIPTOR = "android.support.customtabs.ICustomTabsCallback";
    private static final int TRANSACTION_WARMUP = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_NEW_SESSION = IBinder.FIRST_CALL_TRANSACTION + 2;

    private static final String EXTRA_SESSION = "android.support.customtabs.extra.SESSION";
    private static final String EXTRA_LAUNCH_AS_TRUSTED_WEB_ACTIVITY =
            "android.support.customtabs.extra.LAUNCH_AS_TRUSTED_WEB_ACTIVITY";
    private static final String EXTRA_SCREEN_ORIENTATION = "androidx.browser.trusted.extra.SCREEN_ORIENTATION";
    private static final int SCREEN_ORIENTATION_PORTRAIT = 7;
    private static final String EXTRA_DISPLAY_MODE = "androidx.browser.trusted.extra.DISPLAY_MODE";
    private static final String DISPLAY_MODE_KEY_ID = "androidx.browser.trusted.displaymode.KEY_ID";
    private static final int DISPLAY_MODE_IMMERSIVE = 1;
    private static final String DISPLAY_MODE_KEY_STICKY = "androidx.browser.trusted.displaymode.KEY_STICKY";

    // Chrome identifies the session by this binder, so it's passed both when
    // creating the session and when launching. Chrome's notifications to it
    // are ignored.
    private final Binder callback = new Binder() {
        {
            attachInterface(null, CALLBACK_DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (reply != null) {
                reply.writeNoException();
                // A null Bundle, for extraCallbackWithResult.
                reply.writeInt(0);
            }
            return true;
        }
    };

    private boolean bound = false;
    private boolean launched = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                if (!warmup(service)) {
                    fail("Chrome's warmup call returned false");
                    return;
                }
                if (!newSession(service, callback)) {
                    fail("Chrome refused to create a Custom Tabs session");
                    return;
                }
            } catch (RemoteException e) {
                fail("Calling Chrome's Custom Tabs service failed: " + e);
                return;
            }
            launch();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (!launched) fail("Chrome's Custom Tabs service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent service = new Intent(SERVICE_ACTION).setPackage(CHROME);
        bound = bindService(service, connection, BIND_AUTO_CREATE);
        if (!bound) fail("Can't connect to Chrome. Is it installed and enabled?");
    }

    // Back from the web app returns here; leave with it.
    @Override
    protected void onRestart() {
        super.onRestart();
        if (launched) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) unbindService(connection);
    }

    private void launch() {
        Bundle session = new Bundle();
        session.putBinder(EXTRA_SESSION, callback);
        Bundle displayMode = new Bundle();
        displayMode.putInt(DISPLAY_MODE_KEY_ID, DISPLAY_MODE_IMMERSIVE);
        displayMode.putBoolean(DISPLAY_MODE_KEY_STICKY, true);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(URL))
                .setPackage(CHROME)
                .putExtras(session)
                .putExtra(EXTRA_LAUNCH_AS_TRUSTED_WEB_ACTIVITY, true)
                .putExtra(EXTRA_SCREEN_ORIENTATION, SCREEN_ORIENTATION_PORTRAIT)
                .putExtra(EXTRA_DISPLAY_MODE, displayMode);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            fail("Launching Chrome failed: " + e);
            return;
        }
        launched = true;
    }

    private static boolean warmup(IBinder service) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeLong(0);
            return transactForBoolean(service, TRANSACTION_WARMUP, data);
        } finally {
            data.recycle();
        }
    }

    private static boolean newSession(IBinder service, IBinder callback) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeStrongBinder(callback);
            return transactForBoolean(service, TRANSACTION_NEW_SESSION, data);
        } finally {
            data.recycle();
        }
    }

    private static boolean transactForBoolean(IBinder service, int code, Parcel data) throws RemoteException {
        Parcel reply = Parcel.obtain();
        try {
            service.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
        }
    }

    private void fail(String message) {
        TextView text = new TextView(this);
        text.setText("HarperTube can't start:\n\n" + message);
        text.setTextSize(24);
        text.setGravity(Gravity.CENTER);
        text.setPadding(48, 48, 48, 48);
        text.setBackgroundColor(0xffffffff);
        text.setTextColor(0xff000000);
        setContentView(text);
    }
}
