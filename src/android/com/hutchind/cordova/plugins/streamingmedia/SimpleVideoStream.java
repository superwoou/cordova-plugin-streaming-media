package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.media.MediaPlayer;
import android.os.Build;
import android.widget.MediaController;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.MotionEvent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class SimpleVideoStream extends Activity implements
	MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener,
	MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener {
	private String TAG = getClass().getSimpleName();
	private VideoView mVideoView = null;
	private MediaPlayer mMediaPlayer = null;
	private MediaController mMediaController = null;
	private ProgressBar mProgressBar = null;
	private String mVideoUrl;
	private Boolean mShouldAutoClose = true;
	private Double mPlaybackRate = 1.0;
	private Double mPlaybackTime = 0.0;
	private int mDuration = 0;
	private Boolean mIsPause = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		Bundle b = getIntent().getExtras();
		mVideoUrl = b.getString("mediaUrl");
		mShouldAutoClose = b.getBoolean("shouldAutoClose");
		mShouldAutoClose = mShouldAutoClose == null ? true : mShouldAutoClose;

		RelativeLayout relLayout = new RelativeLayout(this);
		relLayout.setBackgroundColor(Color.BLACK);
		RelativeLayout.LayoutParams relLayoutParam = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
		relLayoutParam.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		mVideoView = new VideoView(this);
		mVideoView.setLayoutParams(relLayoutParam);
		relLayout.addView(mVideoView);

		// Create progress throbber
		mProgressBar = new ProgressBar(this);
		mProgressBar.setIndeterminate(true);
		// Center the progress bar
		RelativeLayout.LayoutParams pblp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		pblp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		mProgressBar.setLayoutParams(pblp);
		// Add progress throbber to view
		relLayout.addView(mProgressBar);
		mProgressBar.bringToFront();

		setOrientation(b.getString("orientation"));

		if(b.containsKey("playbackRate"))
			mPlaybackRate = b.getDouble("playbackRate");
		else
			mPlaybackRate = 1.0;

		if(b.containsKey("playbackTime"))
			mPlaybackTime = b.getDouble("playbackTime");
		else
			mPlaybackTime = 0.0;

		setContentView(relLayout, relLayoutParam);

		play();
	}

	private void play() {
		mProgressBar.setVisibility(View.VISIBLE);
		Uri videoUri = Uri.parse(mVideoUrl);
		try {
			mVideoView.setOnCompletionListener(this);
			mVideoView.setOnPreparedListener(this);
			mVideoView.setOnErrorListener(this);
			mVideoView.setVideoURI(videoUri);
			mMediaController = new MediaController(this);
			mMediaController.setAnchorView(mVideoView);
			mMediaController.setMediaPlayer(mVideoView);
			mVideoView.setMediaController(mMediaController);
		} catch (Throwable t) {
			Log.d(TAG, t.toString());
		}
	}

	private void setOrientation(String orientation) {
		if ("landscape".equals(orientation)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}else if("portrait".equals(orientation)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	private Runnable checkIfPlaying = new Runnable() {
		@Override
		public void run() {
			if (mVideoView.getCurrentPosition() > 0) {
				// Video is not at the very beginning anymore.
				// Hide the progress bar.
				mProgressBar.setVisibility(View.GONE);
				if(mPlaybackTime > 0.0) {
					mMediaPlayer.seekTo((int)(mPlaybackTime * 1000));
					Log.d(TAG, String.format("seekTo %d", mMediaPlayer.getCurrentPosition()));
				}
			} else {
				// Video is still at the very beginning.
				// Check again after a small amount of time.
				mVideoView.postDelayed(checkIfPlaying, 100);
			}
		}
	};


	private Runnable pauseCheck = new Runnable() {
		@Override
		public void run() {
			try {
				if (mMediaPlayer.isPlaying()) {
					if(mIsPause) {
						JSONObject json = new JSONObject();
						try {
							json.put("type", "play");
							json.put("duration", mDuration);
							try {
								json.put("position", mMediaPlayer.getCurrentPosition());
							} catch(IllegalStateException e) {
								json.put("position", mDuration);
							}
						} catch (JSONException e) {
							e.printStackTrace();
						}

						PluginResult result = new PluginResult(PluginResult.Status.OK, json.toString());
						result.setKeepCallback(true);
						StreamingMedia.callbackContext.sendPluginResult(result);
					}
					mIsPause = false;
				} else {
					if(!mIsPause) {
						pause();
					}
					mIsPause = true;
				}
				mVideoView.postDelayed(pauseCheck, 300);
			} catch(Exception e) {

			}
		}
	};

	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d(TAG, "Stream is prepared");
		mMediaPlayer = mp;
		mMediaPlayer.setOnBufferingUpdateListener(this);
		mVideoView.requestFocus();

		mDuration = mp.getDuration();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(mPlaybackRate.floatValue()));
		}
		mp.seekTo((int)(mPlaybackTime.floatValue() * 1000));
		mVideoView.start();
		mVideoView.postDelayed(checkIfPlaying, 0);
		mVideoView.postDelayed(pauseCheck, 300);


		JSONObject json = new JSONObject();
		try {
			json.put("type", "play");
			json.put("duration", mDuration);
			try {
				json.put("position", mMediaPlayer.getCurrentPosition());
			} catch(IllegalStateException e) {
				json.put("position", mDuration);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		PluginResult result = new PluginResult(PluginResult.Status.OK, json.toString());
		result.setKeepCallback(true);
		StreamingMedia.callbackContext.sendPluginResult(result);
	}

	@Override
	public void onPause() {
		super.onPause();
		pause();
	}

	private void pause() {
		Log.d(TAG, "Pausing video.");

		JSONObject json = new JSONObject();
		try {
			json.put("type", "pause");
			json.put("duration", mDuration);
			try {
				json.put("position", mMediaPlayer.getCurrentPosition());
			} catch(IllegalStateException e) {
				json.put("position", mDuration);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		PluginResult result = new PluginResult(PluginResult.Status.OK, json.toString());
		result.setKeepCallback(true);
		StreamingMedia.callbackContext.sendPluginResult(result);

		mVideoView.pause();
	}

	private void stop() {
		Log.d(TAG, "Stopping video.");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();
	}

	private void wrapItUp(int resultCode, String message) {
		Intent intent = new Intent();
		if(resultCode == RESULT_OK) {
			JSONObject json = new JSONObject();
			try {
				json.put("type", "end");
				json.put("duration", mDuration);
				try {
					json.put("position", mMediaPlayer.getCurrentPosition());
				} catch(IllegalStateException e) {
					json.put("position", mDuration);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			intent.putExtra("json", json.toString());
		}
		intent.putExtra("message", message);
		setResult(resultCode, intent);
		finish();
	}

	public void onCompletion(MediaPlayer mp) {
		stop();
		if (mShouldAutoClose) {
			wrapItUp(RESULT_OK, null);
		}
	}

	public boolean onError(MediaPlayer mp, int what, int extra) {
		StringBuilder sb = new StringBuilder();
		sb.append("MediaPlayer Error: ");
		switch (what) {
			case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
				sb.append("Not Valid for Progressive Playback");
				break;
			case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
				sb.append("Server Died");
				break;
			case MediaPlayer.MEDIA_ERROR_UNKNOWN:
				sb.append("Unknown");
				break;
			default:
				sb.append(" Non standard (");
				sb.append(what);
				sb.append(")");
		}
		sb.append(" (" + what + ") ");
		sb.append(extra);
		Log.e(TAG, sb.toString());

		wrapItUp(RESULT_CANCELED, sb.toString());
		return true;
	}

	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		Log.d(TAG, "onBufferingUpdate : " + percent + "%");
	}

	@Override
	public void onBackPressed() {
		// If we're leaving, let's finish the activity
		wrapItUp(RESULT_OK, null);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// The screen size changed or the orientation changed... don't restart the activity
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mMediaController != null)
			mMediaController.show();
		return false;
	}
}
