/*
 * Copyright (c) 2014, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;

import com.inmobi.commons.InMobi;
import com.inmobi.monetization.IMBanner;
import com.inmobi.monetization.IMBannerListener;
import com.inmobi.monetization.IMErrorCode;
import com.inmobi.monetization.IMInterstitial;
import com.inmobi.monetization.IMInterstitialListener;
import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.PsiphonData;


public class StatusActivity
    extends com.psiphon3.psiphonlibrary.MainBase.TabbedActivityBase
{
    public static final String BANNER_FILE_NAME = "bannerImage";

    private ImageView m_banner;
    private boolean m_inmobiInitialized = false;
    private IMBanner m_inmobiBannerAdView = null;
    private IMInterstitial m_inmobiInterstitial = null;
    private boolean m_fullScreenAdShown = false;
    private boolean m_tunnelWholeDevicePromptShown = false;

    public StatusActivity()
    {
        super();
        m_eventsInterface = new Events();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setContentView(R.layout.main);

        m_banner = (ImageView)findViewById(R.id.banner);
        m_tabHost = (TabHost)findViewById(R.id.tabHost);
        m_toggleButton = (Button)findViewById(R.id.toggleButton);

        // NOTE: super class assumes m_tabHost is initialized in its onCreate

        super.onCreate(savedInstanceState);

        if (m_firstRun)
        {
            EmbeddedValues.initialize(this);
        }

        // Play Store Build instances should use existing banner from previously installed APK
        // (if present). To enable this, non-Play Store Build instances write their banner to
        // a private file.
        try
        {
            if (EmbeddedValues.IS_PLAY_STORE_BUILD)
            {
                File bannerImageFile = new File(getFilesDir(), BANNER_FILE_NAME);
                if (bannerImageFile.exists())
                {
                    Bitmap bitmap = BitmapFactory.decodeFile(bannerImageFile.getAbsolutePath());
                    m_banner.setImageBitmap(bitmap);
                }
            }
            else
            {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.banner);
                if (bitmap != null)
                {
                    FileOutputStream out = openFileOutput(BANNER_FILE_NAME, Context.MODE_PRIVATE);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                }
            }
        }
        catch (IOException e)
        {
            // Ignore failure
        }

        PsiphonData.getPsiphonData().setDownloadUpgrades(true);
        
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(StatusActivity.this);
        localBroadcastManager.registerReceiver(new ConnectionStateChangeReceiver(), new IntentFilter(TUNNEL_STOPPING));
        localBroadcastManager.registerReceiver(new ConnectionStateChangeReceiver(), new IntentFilter(UNEXPECTED_DISCONNECT));

        // Auto-start on app first run
        if (m_firstRun)
        {
            m_firstRun = false;
            startUp();
        }

        if (PsiphonData.getPsiphonData().getDataTransferStats().isConnected())
        {
            loadSponsorTab(false);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
        reInitAds();
    }
    
    @Override
    public void onDestroy()
    {
      super.onDestroy();
    }
    
    private void loadSponsorTab(boolean freshConnect)
    {
        resetSponsorHomePage(freshConnect);
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        // If the app is already foreground (so onNewIntent is being called),
        // the incoming intent is not automatically set as the activity's intent
        // (i.e., the intent returned by getIntent()). We want this behaviour,
        // so we'll set it explicitly.
        setIntent(intent);

        // Handle explicit intent that is received when activity is already running
        HandleCurrentIntent();
    }

    @Override
    protected void doToggle()
    {
        super.doToggle();
    }
    
    @Override
    public void onTabChanged(String tabId)
    {
        showFullScreenAd();
        super.onTabChanged(tabId);
    }
    
    public class ConnectionStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            reInitAds();
        }
    }
    
    private void showFullScreenAd()
    {
        if (PsiphonData.getPsiphonData().getShowAds() && !m_fullScreenAdShown)
        {
            ArrayList<InterstitialAd> interstitialAds = new ArrayList<InterstitialAd>();
            interstitialAds.add(new InMobiInterstitial());
            Collections.shuffle(interstitialAds);
            for (InterstitialAd interstitial : interstitialAds)
            {
                interstitial.show();
            }
        }
    }
    
    interface InterstitialAd
    {
        void show();
    }
    
    private class InMobiInterstitial implements InterstitialAd
    {
        @Override
        public void show()
        {
            if (!m_fullScreenAdShown &&
                    m_inmobiInterstitial != null &&
                    m_inmobiInterstitial.getState() == IMInterstitial.State.READY)
            {
                m_inmobiInterstitial.show();
                m_fullScreenAdShown = true;
            }
        }
    }
    
    private static Integer getOptimalInMobiSlotSize(Activity context)
    {
        Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
        double density = displayMetrics.density;
        double width = displayMetrics.widthPixels;
        double height = displayMetrics.heightPixels;
        int[][] maparray = {{IMBanner.INMOBI_AD_UNIT_728X90, 728, 90},
                            {IMBanner.INMOBI_AD_UNIT_468X60, 468, 60},
                            {IMBanner.INMOBI_AD_UNIT_320X50, 320, 50}};
        for (int i = 0; i < maparray.length; i++)
        {
            if (maparray[i][1] * density <= width && maparray[i][2] * density <= height)
            {
                return maparray[i][0];
            }
        }
        return IMBanner.INMOBI_AD_UNIT_320X50;
    }
    
    static final String INMOBI_BANNER_PROPERTY_ID = "";
    static final String INMOBI_INTERSTITIAL_PROPERTY_ID = "";
    
    private void initAds()
    {
        if (PsiphonData.getPsiphonData().getShowAds())
        {
            if (!m_inmobiInitialized)
            {
                InMobi.initialize(this, INMOBI_BANNER_PROPERTY_ID);
                InMobi.setLanguage(Locale.getDefault().getISO3Language());
                m_inmobiInitialized = true;
            }

            if (m_inmobiInterstitial == null)
            {
                m_inmobiInterstitial = new IMInterstitial(this, INMOBI_INTERSTITIAL_PROPERTY_ID);
                m_inmobiInterstitial.setIMInterstitialListener(new IMInterstitialListener() {
                    @Override
                    public void onDismissInterstitialScreen(IMInterstitial arg0)
                    {
                    }
                    @Override
                    public void onInterstitialFailed(IMInterstitial arg0, IMErrorCode arg1)
                    {
                        Log.d("InMobi", String.format("Interstitial Request Failed: %s", arg1.toString()));
                        // Set to null so it will be recreated the next time
                        m_inmobiInterstitial = null;
                    }
                    @Override
                    public void onInterstitialInteraction(IMInterstitial arg0, Map<String, String> arg1)
                    {
                    }
                    @Override
                    public void onInterstitialLoaded(IMInterstitial arg0)
                    {
                    }
                    @Override
                    public void onLeaveApplication(IMInterstitial arg0)
                    {
                    }
                    @Override
                    public void onShowInterstitialScreen(IMInterstitial arg0)
                    {
                    }
                });
                m_inmobiInterstitial.loadInterstitial();
            }
            
            if (m_inmobiBannerAdView == null)
            {
                m_inmobiBannerAdView = new IMBanner(this, INMOBI_BANNER_PROPERTY_ID, getOptimalInMobiSlotSize(this));
                m_inmobiBannerAdView.setRefreshInterval(30);
                m_inmobiBannerAdView.setIMBannerListener(new IMBannerListener() {
                    @Override
                    public void onBannerInteraction(IMBanner arg0, Map<String, String> arg1)
                    {
                    }
                    @Override
                    public void onBannerRequestFailed(IMBanner arg0, IMErrorCode arg1)
                    {
                        Log.d("InMobi", String.format("Banner Request Failed: %s", arg1.toString()));
                        // Leave m_inmobiBannerAdView. If it is not in the layout, we will
                        // call m_inmobiBannerAdView.loadBanner() again the next time
                    }
                    @Override
                    public void onBannerRequestSucceeded(IMBanner arg0)
                    {
                        Log.d("InMobi", "Banner Request Succeeded");
                        if (m_inmobiBannerAdView.getParent() == null)
                        {
                            LinearLayout layout = (LinearLayout)findViewById(R.id.bannerLayout);
                            layout.removeAllViewsInLayout();
                            layout.addView(m_inmobiBannerAdView);
                        }
                    }
                    @Override
                    public void onDismissBannerScreen(IMBanner arg0)
                    {
                    }
                    @Override
                    public void onLeaveApplication(IMBanner arg0)
                    {
                    }
                    @Override
                    public void onShowBannerScreen(IMBanner arg0)
                    {
                    }
                });
            }
            if (m_inmobiBannerAdView.getParent() == null)
            {
                m_inmobiBannerAdView.loadBanner();
            }
        }
    }
    
    private void reInitAds()
    {
        if (PsiphonData.getPsiphonData().getShowAds())
        {
            LinearLayout layout = (LinearLayout)findViewById(R.id.bannerLayout);
            layout.removeAllViewsInLayout();
            layout.addView(m_banner);

            m_inmobiInterstitial = null;
            m_inmobiBannerAdView = null;

            initAds();
        }
    }
    
    protected void HandleCurrentIntent()
    {
        Intent intent = getIntent();

        if (intent == null || intent.getAction() == null)
        {
            return;
        }

        if (0 == intent.getAction().compareTo(HANDSHAKE_SUCCESS))
        {
            reInitAds();
            
            // Show the home page. Always do this in browser-only mode, even
            // after an automated reconnect -- since the status activity was
            // brought to the front after an unexpected disconnect. In whole
            // device mode, after an automated reconnect, we don't re-invoke
            // the browser.
            if (!PsiphonData.getPsiphonData().getTunnelWholeDevice()
                || !intent.getBooleanExtra(HANDSHAKE_SUCCESS_IS_RECONNECT, false))
            {
                m_tabHost.setCurrentTabByTag("home");
                loadSponsorTab(true);

                //m_eventsInterface.displayBrowser(this);
            }

            // We only want to respond to the HANDSHAKE_SUCCESS action once,
            // so we need to clear it (by setting it to a non-special intent).
            setIntent(new Intent(
                            "ACTION_VIEW",
                            null,
                            this,
                            this.getClass()));
        }

        // No explicit action for UNEXPECTED_DISCONNECT, just show the activity
    }

    public void onToggleClick(View v)
    {
        doToggle();
    }

    public void onOpenBrowserClick(View v)
    {
        m_eventsInterface.displayBrowser(this);
    }

    @Override
    public void onFeedbackClick(View v)
    {
        Intent feedbackIntent = new Intent(this, FeedbackActivity.class);
        startActivity(feedbackIntent);
    }

    @Override
    protected void startUp()
    {
        // If the user hasn't set a whole-device-tunnel preference, show a prompt
        // (and delay starting the tunnel service until the prompt is completed)

        boolean hasPreference = PreferenceManager.getDefaultSharedPreferences(this).contains(TUNNEL_WHOLE_DEVICE_PREFERENCE);

        if (m_tunnelWholeDeviceToggle.isEnabled() &&
            !hasPreference &&
            !isServiceRunning())
        {
            if (!m_tunnelWholeDevicePromptShown)
            {
                final Context context = this;

                AlertDialog dialog = new AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setOnKeyListener(
                            new DialogInterface.OnKeyListener() {
                                @Override
                                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                    // Don't dismiss when hardware search button is clicked (Android 2.3 and earlier)
                                    return keyCode == KeyEvent.KEYCODE_SEARCH;
                                }})
                    .setTitle(R.string.StatusActivity_WholeDeviceTunnelPromptTitle)
                    .setMessage(R.string.StatusActivity_WholeDeviceTunnelPromptMessage)
                    .setPositiveButton(R.string.StatusActivity_WholeDeviceTunnelPositiveButton,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Persist the "on" setting
                                    updateWholeDevicePreference(true);
                                    startTunnel(context);
                                }})
                    .setNegativeButton(R.string.StatusActivity_WholeDeviceTunnelNegativeButton,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Turn off and persist the "off" setting
                                        m_tunnelWholeDeviceToggle.setChecked(false);
                                        updateWholeDevicePreference(false);
                                        startTunnel(context);
                                    }})
                    .setOnCancelListener(
                            new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    // Don't change or persist preference (this prompt may reappear)
                                    startTunnel(context);
                                }})
                    .show();
                
                // Our text no longer fits in the AlertDialog buttons on Lollipop, so force the
                // font size (on older versions, the text seemed to be scaled down to fit).
                // TODO: custom layout
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
                }
                
                m_tunnelWholeDevicePromptShown = true;
            }
            else
            {
                // ...there's a prompt already showing (e.g., user hit Home with the
                // prompt up, then resumed Psiphon)
            }

            // ...wait and let onClick handlers will start tunnel
        }
        else
        {
            // No prompt, just start the tunnel (if not already running)

            startTunnel(this);
        }

        // Handle the intent that resumed that activity
        HandleCurrentIntent();
    }
}
