/*
 * Copyright (c) 2013, Psiphon Inc.
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

package com.psiphon3.psiphonlibrary;

import java.net.DatagramSocket;
import java.net.Socket;

import com.psiphon3.psiphonlibrary.Utils.MyLog;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.ParcelFileDescriptor;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class Tun2Socks
{
    public static interface IProtectSocket
    {
        boolean doVpnProtect(Socket socket);
        boolean doVpnProtect(DatagramSocket socket);
    };


    private static Thread mThread;
    private static TunnelCore mTunnelCore;
    private static ParcelFileDescriptor mVpnInterfaceFileDescriptor;
    private static int mVpnInterfaceMTU;
    private static String mVpnIpAddress;
    private static String mVpnNetMask;
    private static String mSocksServerAddress;
    private static String mUdpgwServerAddress;
    
    // Note: this class isn't a singleton, but you can't run more
    // than one instance due to the use of global state (the lwip
    // module, etc.) in the native code.
    
    public static synchronized void Start(
            TunnelCore tunnelCore,
            ParcelFileDescriptor vpnInterfaceFileDescriptor,
            int vpnInterfaceMTU,
            String vpnIpAddress,
            String vpnNetMask,
            String socksServerAddress,
            String udpgwServerAddress)
    {
        // TODO: will be cleaner if/when TunnelCore is a singleton
        assert(mTunnelCore == null);
        mTunnelCore = tunnelCore;
        
        Stop();

        mVpnInterfaceFileDescriptor = vpnInterfaceFileDescriptor;
        mVpnInterfaceMTU = vpnInterfaceMTU;
        mVpnIpAddress = vpnIpAddress;
        mVpnNetMask = vpnNetMask;
        mSocksServerAddress = socksServerAddress;
        mUdpgwServerAddress = udpgwServerAddress;

        mThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                runTun2Socks(
                        mVpnInterfaceFileDescriptor.detachFd(),
                        mVpnInterfaceMTU,
                        mVpnIpAddress,
                        mVpnNetMask,
                        mSocksServerAddress,
                        mUdpgwServerAddress);
            	
                // Unexpected error condition (Stop not signaled)
                if (mTunnelCore != null)
                {
                	MyLog.e(R.string.tun2socks_failed, MyLog.Sensitivity.NOT_SENSITIVE);
                    
                    mTunnelCore.signalUnexpectedDisconnect();
                }
            }
        });
        mThread.start();
    }
    
    public static synchronized void Stop()
    {
        if (mThread != null)
        {
            mTunnelCore = null;
            terminateTun2Socks();
            try
            {
                mThread.join();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            mThread = null;
        }
    }
        
    public static void logTun2Socks(
            String level,
            String channel,
            String msg)
    {
        String logMsg = level + "(" + channel + "): " + msg;
        if (0 == level.compareTo("ERROR"))
        {
            MyLog.e(R.string.tun2socks_error, MyLog.Sensitivity.NOT_SENSITIVE, logMsg);
        }
        else
        {
            MyLog.g(logMsg);
        }
    }

    private native static int runTun2Socks(
            int vpnInterfaceFileDescriptor,
            int vpnInterfaceMTU,
            String vpnIpAddress,
            String vpnNetMask,
            String socksServerAddress,
            String udpgwServerAddress);

    private native static void terminateTun2Socks();
    
    static
    {
        System.loadLibrary("tun2socks");
    }
}
