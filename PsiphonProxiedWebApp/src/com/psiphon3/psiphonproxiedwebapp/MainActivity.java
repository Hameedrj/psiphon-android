package com.psiphon3.psiphonproxiedwebapp;

import org.zirco.utils.ProxySettings;

import android.os.Bundle;
import android.os.Handler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.HttpAuthHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.psiphon3.psiphonlibrary.EmbeddedValues;
import com.psiphon3.psiphonlibrary.PsiphonData;
import com.psiphon3.psiphonlibrary.TunnelCore;
import com.psiphon3.psiphonlibrary.Utils.MyLog;
import com.psiphon3.psiphonlibrary.Events;

public class MainActivity extends Activity implements MyLog.ILogInfoProvider, Events
{
    private boolean m_loadedWebView = false;
    private WebView m_webView;
    private TextView m_textView;
    private boolean m_splashScreenCancelled = false;
    private Handler m_handler = new Handler();
    private TunnelCore m_tunnelCore;
    
    // Infinite toast
    private void showSplashScreen()
    {
        m_splashScreenCancelled = false;

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.splash_screen, (ViewGroup)findViewById(R.id.splash_screen));
        m_textView = (TextView)layout.findViewById(R.id.splash_screen_text);
        
        // Set background to match (0,0) splash image pixel color
        ImageView imageView = (ImageView)layout.findViewById(R.id.splash_screen_image);
        Bitmap bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
        int pixel = bitmap.getPixel(0, 0);
        layout.setBackgroundColor(pixel);

        final Toast splashScreen = new Toast(this);
        splashScreen.setGravity(Gravity.FILL, 0, 0);
        splashScreen.setDuration(Toast.LENGTH_SHORT);
        splashScreen.setView(layout);

        Thread t = new Thread()
        {
            public void run()
            {
                try
                {
                    while (!m_splashScreenCancelled)
                    {
                        splashScreen.show();
                        sleep(1850);
                    }
                }
                catch (Exception e)
                {
                }
                
                m_textView = null;
            }
        };
        t.start();
    }
    
    private void dismissSplashScreen()
    {
        m_splashScreenCancelled = true;
    }
    
    private class CustomWebViewClient extends WebViewClient
    {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            // Always open links in the proxied WebView
            return false;
        }

        @Override
        public void onReceivedHttpAuthRequest(WebView view, final HttpAuthHandler handler, final String host, final String realm)
        {
            handler.proceed(EmbeddedValues.PROXIED_WEB_APP_HTTP_AUTH_USERNAME, EmbeddedValues.PROXIED_WEB_APP_HTTP_AUTH_PASSWORD);
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        MyLog.logInfoProvider = this;
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        m_webView = (WebView)findViewById(R.id.webView);
        m_webView.setWebViewClient(new CustomWebViewClient());
        WebSettings webSettings = m_webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        
        m_tunnelCore = new TunnelCore(this, null);
        m_tunnelCore.setUseGenericLogMessages(true);
        m_tunnelCore.setEventsInterface(this);
        m_tunnelCore.onCreate();
        m_tunnelCore.startTunnel();
    }
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        m_tunnelCore.stopTunnel();
        m_tunnelCore.onDestroy();
        m_tunnelCore = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (m_tunnelCore.getState() != TunnelCore.State.CONNECTED)
        {
            showSplashScreen();
        }
    }
        
    @Override
    protected void onPause()
    {
        super.onPause();
        
        dismissSplashScreen();        
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && m_webView.canGoBack())
        {
            m_webView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public int getAndroidLogPriorityEquivalent(int priority)
    {
        return 0;
    }

    @Override
    public String getResourceString(int stringResID, Object[] formatArgs)
    {
        if (formatArgs == null || formatArgs.length == 0)
        {
            return getString(stringResID);
        }
        
        return getString(stringResID, formatArgs);
    }

    @Override
    public String getResourceEntryName(int stringResID)
    {
        return getResources().getResourceEntryName(stringResID);
    }

    @Override
    public void appendStatusMessage(Context context, String message, int messageClass)
    {
        final String finalMessage = message;

        m_handler.post(
            new Runnable()
            {
                @Override
                public void run()
                {
                    if (m_textView != null)
                    {
                        m_textView.setText(finalMessage);
                    }
                }            
            });
    }

    private String getHomePage()
    {
        // Only supports one home page
        for (String homePage : PsiphonData.getPsiphonData().getHomePages())
        {
            return homePage;
        }

        return null;
    }
    
    @Override
    public void signalHandshakeSuccess(Context context)
    {
        final Context finalContext = this;
        
        m_handler.post(
            new Runnable()
            {
                @Override
                public void run()
                {
                    String homePage = getHomePage();
                    if (homePage != null)
                    {
                        dismissSplashScreen();

                        ProxySettings.setLocalProxy(
                                finalContext,
                                PsiphonData.getPsiphonData().getHttpProxyPort());

                        if (!m_loadedWebView)
                        {
                            // Only load the WebView once. So if we get an unexpected
                            // disconnect and reconnect, the WebView retains its state.
                            // Note this means we ignore changes to the home page during
                            // this session.
                            
                            m_webView.loadUrl(homePage);
                            m_loadedWebView = true;
                        }
                    }
                }            
            });
    }

    @Override
    public void signalUnexpectedDisconnect(Context context)
    {
        m_handler.post(
            new Runnable()
            {
                @Override
                public void run()
                {
                    showSplashScreen();
                }            
            });
    }

    @Override
    public void signalTunnelStarting(Context context)
    {
    }

    @Override
    public void signalTunnelStopping(Context context)
    {
    }

    @Override
    public Intent pendingSignalNotification(Context context)
    {
        return null;
    }
}
