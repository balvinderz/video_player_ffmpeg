// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.os.Build;
import android.provider.MediaStore.Video;
import android.util.LongSparseArray;
import com.google.android.exoplayer2.text.Subtitle;
import io.flutter.FlutterInjector;
import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformViewFactory;
import io.flutter.plugin.platform.PlatformView;

import io.flutter.plugins.videoplayer.Messages.AudioMessage;
import io.flutter.plugins.videoplayer.Messages.CreateMessage;
import io.flutter.plugins.videoplayer.Messages.LoopingMessage;
import io.flutter.plugins.videoplayer.Messages.MixWithOthersMessage;
import io.flutter.plugins.videoplayer.Messages.PlaybackSpeedMessage;
import io.flutter.plugins.videoplayer.Messages.PositionMessage;
import io.flutter.plugins.videoplayer.Messages.SubtitleMessage;
import io.flutter.plugins.videoplayer.Messages.TextureMessage;
import io.flutter.plugins.videoplayer.Messages.VideoPlayerApi;
import io.flutter.plugins.videoplayer.Messages.VolumeMessage;
import io.flutter.view.TextureRegistry;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;

/** Android platform implementation of the VideoPlayerPlugin. */
public class VideoPlayerPlugin implements FlutterPlugin, VideoPlayerApi {
  private static final String TAG = "VideoPlayerPlugin";
  private final LongSparseArray<VideoPlayer> videoPlayers = new LongSparseArray<>();
  private static FlutterState flutterState;
  private VideoPlayerOptions options = new VideoPlayerOptions();
  static  VideoPlayerPlugin instance ;
  private static final String VIEW_TYPE = "flutter.io/videoPlayer/getVideoView";

  /** Register this with the v2 embedding for the plugin to respond to lifecycle callbacks. */
  public VideoPlayerPlugin() {
    Log.i("video_player_log","created new instance");
    instance = this;
  }

  @SuppressWarnings("deprecation")
  private VideoPlayerPlugin(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
    Log.i("video_player_log","created new instance from parameter");

    this.flutterState =
        new FlutterState(
            registrar.context(),
            registrar.messenger(),
            registrar::lookupKeyForAsset,
            registrar::lookupKeyForAsset,
            registrar.textures());
    flutterState.startListening(this, registrar.messenger());
    registrar
        .platformViewRegistry()
        .registerViewFactory(
            VIEW_TYPE,
            flutterState
        );
    instance = this;
  }

  /** Registers this with the stable v1 embedding. Will not respond to lifecycle events. */
  @SuppressWarnings("deprecation")
  public static void registerWith(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
    instance = new VideoPlayerPlugin(registrar);
    if(flutterState == null){
      flutterState = new FlutterState(
          registrar.context(),
          registrar.messenger(),
          registrar::lookupKeyForAsset,
          registrar::lookupKeyForAsset,
          registrar.textures());
      registrar
          .platformViewRegistry()
          .registerViewFactory(
              VIEW_TYPE,
              flutterState
          );
      
    }

    registrar.addViewDestroyListener(
        view -> {
          instance.onDestroy();
          return false; // We are not interested in assuming ownership of the NativeView.
        });
  }
  VideoPlayer build(int viewId, Context context, BinaryMessenger binaryMessenger) {
    // only create view for player and attach channel events

    EventChannel eventChannel =
        new EventChannel(
            flutterState.binaryMessenger, "flutter.io/videoPlayer/videoEvents" + viewId);
    VideoPlayer vlcPlayer = new VideoPlayer(context, eventChannel);
    videoPlayers.append(viewId, vlcPlayer);
    Log.i("addedview", "view id is " + viewId);
    return vlcPlayer;
  }
  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {

    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      try {
        HttpsURLConnection.setDefaultSSLSocketFactory(new CustomSSLSocketFactory());
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        Log.w(
            TAG,
            "Failed to enable TLSv1.1 and TLSv1.2 Protocols for API level 19 and below.\n"
                + "For more information about Socket Security, please consult the following link:\n"
                + "https://developer.android.com/reference/javax/net/ssl/SSLSocket",
            e);
      }
    }

    final FlutterInjector injector = FlutterInjector.instance();

    this.flutterState =
        new FlutterState(
            binding.getApplicationContext(),
            binding.getBinaryMessenger(),
            injector.flutterLoader()::getLookupKeyForAsset,
            injector.flutterLoader()::getLookupKeyForAsset,
            binding.getTextureRegistry()
            );
    binding
        .getPlatformViewRegistry()
        .registerViewFactory(
            VIEW_TYPE, flutterState);
    flutterState.startListening(this, binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    if (flutterState == null) {
      Log.wtf(TAG, "Detached from the engine before registering to it.");
    }
    flutterState.stopListening(binding.getBinaryMessenger());
    flutterState = null;
    initialize();
  }

  private void disposeAllPlayers() {
    for (int i = 0; i < videoPlayers.size(); i++) {
      videoPlayers.valueAt(i).dispose();
    }
    videoPlayers.clear();
  }

  private void onDestroy() {
    // The whole FlutterView is being destroyed. Here we release resources acquired for all
    // instances
    // of VideoPlayer. Once https://github.com/flutter/flutter/issues/19358 is resolved this may
    // be replaced with just asserting that videoPlayers.isEmpty().
    // https://github.com/flutter/flutter/issues/20989 tracks this.
    disposeAllPlayers();
  }

  public void initialize() {


    disposeAllPlayers();
  }

  public void create(CreateMessage arg) {

    Log.i("video_players_size_is",""+videoPlayers.size());
    VideoPlayer player = videoPlayers.get(arg.getTextureId());

    if (arg.getAsset() != null) {
      String assetLookupKey;
      if (arg.getPackageName() != null) {
        assetLookupKey =
            flutterState.keyForAssetAndPackageName.get(arg.getAsset(), arg.getPackageName());
      } else {
        assetLookupKey = flutterState.keyForAsset.get(arg.getAsset());
      }
      player.setData(assetLookupKey,null,null,options);
    } else {
      @SuppressWarnings("unchecked")
      Map<Object, Object> httpHeaders = arg.getHttpHeaders();
      Map<String, String> newMap = new HashMap<String, String>();
      for (Map.Entry<Object, Object> entry : httpHeaders.entrySet()) {
        if (entry.getValue() instanceof String) {
          newMap.put(entry.getKey().toString(), (String) entry.getValue());
        }
      }
      player.setData(arg.getUri(),arg.getFormatHint(),newMap,options);
    }
  }

  public void dispose(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.dispose();
    videoPlayers.remove(arg.getTextureId());
  }

  public void setLooping(LoopingMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setLooping(arg.getIsLooping());
  }

  public void setVolume(VolumeMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setVolume(arg.getVolume());
  }

  public void setPlaybackSpeed(PlaybackSpeedMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setPlaybackSpeed(arg.getSpeed());
  }

  public void play(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.play();
  }

  public PositionMessage position(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    PositionMessage result = new PositionMessage();
    result.setPosition(player.getPosition());
    player.sendBufferingUpdate();
    return result;
  }

  public void seekTo(PositionMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.seekTo(arg.getPosition().intValue());
  }

  public void pause(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.pause();
  }
  public AudioMessage getAudios(TextureMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    AudioMessage result = new AudioMessage();
    result.setAudios(Arrays.asList(player.getAudios().toArray()));
    return result;

  }
  public void setAudio(AudioMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setAudio(arg.getAudios().get(0).toString());

  }

  public void setAudioByIndex(AudioMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setAudioByIndex(arg.getIndex().intValue());

  }

  public SubtitleMessage getSubtitles(TextureMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    SubtitleMessage result = new SubtitleMessage();
    result.setSubtitles(Arrays.asList(player.getSubtitles().toArray()));
    return result;

  }
  public void setSubtitle(SubtitleMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setSubtitle(arg.getSubtitles().get(0).toString());

  }

  public void setSubtitleByIndex(SubtitleMessage arg)
  {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    player.setSubtitleByIndex(arg.getIndex().intValue());

  }

  @Override
  public void setMixWithOthers(MixWithOthersMessage arg) {
    options.mixWithOthers = arg.getMixWithOthers();
  }

  private interface KeyForAssetFn {
    String get(String asset);
  }

  private interface KeyForAssetAndPackageName {
    String get(String asset, String packageName);
  }

  private static final class FlutterState  extends PlatformViewFactory {
    private final Context applicationContext;
    private final BinaryMessenger binaryMessenger;
    private final KeyForAssetFn keyForAsset;
    private final KeyForAssetAndPackageName keyForAssetAndPackageName;
    private final TextureRegistry textureRegistry;
    FlutterState(
        Context applicationContext,
        BinaryMessenger messenger,
        KeyForAssetFn keyForAsset,
        KeyForAssetAndPackageName keyForAssetAndPackageName,
        TextureRegistry textureRegistry
        ) {
      super(StandardMessageCodec.INSTANCE);
      this.applicationContext = applicationContext;
      this.binaryMessenger = messenger;
      this.keyForAsset = keyForAsset;
      this.keyForAssetAndPackageName = keyForAssetAndPackageName;
      this.textureRegistry = textureRegistry;
    }

    void startListening(VideoPlayerPlugin methodCallHandler, BinaryMessenger messenger) {
      VideoPlayerApi.setup(messenger, methodCallHandler);
    }
    @Override
    public PlatformView create(Context context, int viewId, Object args) {
      return  VideoPlayerPlugin.instance.build(viewId,context,binaryMessenger);
    }

    void stopListening(BinaryMessenger messenger) {
      VideoPlayerApi.setup(messenger, null);
    }
  }
}
