/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;

import android.animation.IntArrayEvaluator;
import android.util.Log;

import org.appspot.apprtc.util.Logout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;


/**
 * AsyncTask that converts an AppRTC room URL into the set of signaling
 * parameters to use with that room.
 */
public class RoomParametersFetcher {

  public enum State {
    NOT_CONNECTED,
    RESOLVING,
    SIGNING_IN,
    CONNECTED,
    SIGNING_OUT_WAITING,
    SIGNING_OUT,
  }

  private static final String TAG = "RoomRTCClient";
  private static final int TURN_HTTP_TIMEOUT_MS = 5000;
  private final RoomParametersFetcherEvents events;
  private final String roomUrl;
  private final String roomMessage;
  private AsyncHttpURLConnection httpConnection;
  private static Map<Integer,String> peers;
  private List<String>logs;
  private static int myId;
  private static String myName;
  private static State state;
  private boolean isinit=true;
  private boolean hasTriggerSingnalReady=false;


  private static LinkedList<IceCandidate> iceCandidates = null;
  private static SessionDescription offerSdp = null;
  private static SessionDescription answerSdp = null;
  /**
   * Room parameters fetcher callbacks.
   */
  public static interface RoomParametersFetcherEvents {
    /**
     * Callback fired once the room's signaling parameters
     * SignalingParameters are extracted.
     */
    public void onSignalingParametersReady(final SignalingParameters params);

    /**
     * Callback for room parameters extraction error.
     */
    public void onSignalingParametersError(final String description);

    public void onPeerConnected(int peerId);

    public  void onMessageFromPeer(int peerId,String message);

    public void startHangingGet(int peerId);

    public void onRemoteIceCandidate(IceCandidate candidate);

    public void onRemoteDescription( SessionDescription sdp);

  }




  public void onHangGetConnnect()
  {
    Logout.verbose(TAG,"======+++++++++++++++=======");
    Log.d(TAG, "Room Get: " + roomUrl);
    httpConnection = new AsyncHttpURLConnection(
            "GET", roomUrl, roomMessage,
            new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                Log.e(TAG, "Room onHang error: " + errorMessage);
                //events.onSignalingParametersError(errorMessage);
              }

              @Override
              public void onHttpComplete(String response,int peerId) {
                onHangGetRead(response,peerId);
              }
            });
    httpConnection.send();
  }

  private void onHangGetRead(String response,int peerId) {
    Logout.verbose(TAG, "=======read======"+response);
    Log.d(TAG, "Room response: " + response+"======");
    try {
      if(peerId==myId)
      {
        String[]items= response.split("\n");
        for (String item :
                items) {
          String[] values = item.split(",");
          if(values.length==3) {
            if(Integer.parseInt(values[2])==1) {
              peers.put(Integer.parseInt(values[1]), values[0]);
              events.onPeerConnected(Integer.parseInt(values[1]));
            }
          }
        }
      }else {

        JSONObject message = new JSONObject(response);
        if ( message.has("type")) {
          String messageType = message.getString("type");
          if(messageType.equals("offer")) {
            offerSdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(messageType),
                    message.getString("sdp"));
            //PeerConnection.IceServer turnServers=new PeerConnection.IceServer("stun:git.geekon.cn:12345");

          }else
          {
            answerSdp=new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(messageType),
                    message.getString("sdp"));
            SignalingParameters params= new SignalingParameters(String.valueOf(peerId),
                    answerSdp, iceCandidates);
            events.onRemoteDescription(answerSdp);
          }
        } else {
          //add ice candiate

          IceCandidate candidate = new IceCandidate(
                  message.getString("sdpMid"),
                  message.getInt("sdpMLineIndex"),
                  message.getString("candidate"));
          iceCandidates.add(candidate);

          //Log.d(TAG,"++++++++++++++++++++++++++++++++++++iceCandidate size:"+iceCandidates.size());
          /*
          if(isinit)
          {
            events.onRemoteIceCandidate(candidate);
          }else if (hasTriggerSingnalReady&&offerSdp!=null) {
            events.onRemoteIceCandidate(candidate);
          }
          */
          if (iceCandidates.size()>1&&!isinit){
            events.onRemoteIceCandidate(candidate);
          }else if(isinit)
            events.onRemoteIceCandidate(candidate);
        }

        //if(offerSdp!=null&&iceCandidates.size()==1&&!hasTriggerSingnalReady)
        if(offerSdp!=null&&iceCandidates.size()==1)
        {
          //hasTriggerSingnalReady=true;
          Log.d(TAG,"Room iceCandidates size:"+iceCandidates.size());
          SignalingParameters params= new SignalingParameters(String.valueOf(peerId),
                    offerSdp, iceCandidates);
            events.onSignalingParametersReady(params);
        }

      }
    }catch (Exception e) {

    }
    events.startHangingGet(myId);
  }

  public RoomParametersFetcher(String roomUrl, String roomMessage,
                               final RoomParametersFetcherEvents events) {
    Logout.verbose(TAG,"=====================");
    if(peers==null)
    {
      peers=new Hashtable<Integer, String>();
    }
    if(iceCandidates==null) {
      iceCandidates = new LinkedList<IceCandidate>();
      answerSdp=null;
      offerSdp=null;
    }
    this.roomUrl = roomUrl;
    this.roomMessage = roomMessage;
    this.events = events;
  }

  public void makeRequest() {
    Logout.verbose(TAG,"=====================");
    if(iceCandidates!=null)
      if (iceCandidates.size()>0)
        iceCandidates.clear();

    Log.d(TAG, "Room Get: " + roomUrl);
    httpConnection = new AsyncHttpURLConnection(
            "GET", roomUrl, roomMessage,
            new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                Log.e(TAG, "Room connection error: " + errorMessage);
                events.onSignalingParametersError(errorMessage);
              }

              @Override
              public void onHttpComplete(String response,int peerId) {
                roomHttpResponseParse(response,peerId);
              }
            });
    httpConnection.send();
  }


  public void postRequest() {
    Logout.verbose(TAG,"=====================");
    Log.d(TAG, "Room Post: " + roomUrl);
    httpConnection = new AsyncHttpURLConnection(
            "POST", roomUrl, roomMessage,
            new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                Log.e(TAG, "Room connection error: " + errorMessage);
                events.onSignalingParametersError(errorMessage);
              }

              @Override
              public void onHttpComplete(String response,int peerId) {
                //roomHttpResponseParse(response,peerId);
              }
            });
    httpConnection.send();
  }

  private void roomHttpResponseParse(String response,int peerId) {
    Logout.verbose(TAG,"=====================");
    Log.d(TAG, "Room response: " + response);
    try{
      String[]items= response.split("\n");
      for (String item :
              items) {
        String[] values = item.split(",");
        if(values.length==3) {
          if (Integer.parseInt(values[1])==peerId)
          {
            myName=values[0];
            myId=peerId;
            if(values[2].equals("1"))
              state=State.CONNECTED;
            events.startHangingGet(myId);
          }
          else
          {
            if(peers==null)
            {
              peers=new HashMap<Integer, String>();
            }
            if(Integer.parseInt(values[2])==1) {
              peers.put(Integer.parseInt(values[1]), values[0]);
              if(isinit)
                events.onPeerConnected(Integer.parseInt(values[1]));
            }
          }
        }
      }

    }catch (Exception e)
    {

    }
  }

  // Requests & returns a TURN ICE Server based on a request URL.  Must be run
  // off the main thread!
  private LinkedList<PeerConnection.IceServer> requestTurnServers(String url)
          throws IOException, JSONException {
    Logout.verbose(TAG,"=====================");
    LinkedList<PeerConnection.IceServer> turnServers =
            new LinkedList<PeerConnection.IceServer>();
    Log.d(TAG, "Request TURN from: " + url);
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(TURN_HTTP_TIMEOUT_MS);
    connection.setReadTimeout(TURN_HTTP_TIMEOUT_MS);
    int responseCode = connection.getResponseCode();
    if (responseCode != 200) {
      throw new IOException("Non-200 response when requesting TURN server from "
              + url + " : " + connection.getHeaderField(null));
    }
    InputStream responseStream = connection.getInputStream();
    String response = drainStream(responseStream);
    connection.disconnect();
    Log.d(TAG, "TURN response: " + response);
    JSONObject responseJSON = new JSONObject(response);
    String username = responseJSON.getString("username");
    String password = responseJSON.getString("password");
    JSONArray turnUris = responseJSON.getJSONArray("uris");
    for (int i = 0; i < turnUris.length(); i++) {
      String uri = turnUris.getString(i);
      turnServers.add(new PeerConnection.IceServer(uri, username, password));
    }
    return turnServers;
  }

  // Return the list of ICE servers described by a WebRTCPeerConnection
  // configuration string.
  private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(
          String pcConfig) throws JSONException {
    Logout.verbose(TAG,"=====================");
    JSONObject json = new JSONObject(pcConfig);
    JSONArray servers = json.getJSONArray("iceServers");
    LinkedList<PeerConnection.IceServer> ret =
            new LinkedList<PeerConnection.IceServer>();
    for (int i = 0; i < servers.length(); ++i) {
      JSONObject server = servers.getJSONObject(i);
      String url = server.getString("urls");
      String credential =
              server.has("credential") ? server.getString("credential") : "";
      ret.add(new PeerConnection.IceServer(url, "", credential));
    }
    return ret;
  }

  // Return the contents of an InputStream as a String.
  private static String drainStream(InputStream in) {
    Logout.verbose(TAG,"=====================");
    Scanner s = new Scanner(in).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

}
