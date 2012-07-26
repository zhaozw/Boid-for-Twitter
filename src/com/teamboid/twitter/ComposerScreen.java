package com.teamboid.twitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.teamboid.twitterapi.status.GeoLocation;
import org.json.JSONObject;

import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.services.SendTweetService;
import com.teamboid.twitter.utilities.Extractor;
import com.teamboid.twitter.utilities.Utilities;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

/**
 * The activity that represents the tweet composer screen.
 * @author Aidan Follestad
 */
public class ComposerScreen extends Activity {
	
	private SendTweetTask stt = new SendTweetTask();
	private int lastTheme;
	private boolean shownLinksMessage;
	
	/**
	 * Ensures the UI is loaded with the correct information from stt
	 */
	private void loadTask(){
		final EditText content = (EditText)findViewById(R.id.tweetContent);
		content.setText(stt.contents);
		invalidateOptionsMenu();
		initializeAccountSwitcher(false);
	}
	
	private void loadDraft() {
		if(!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("enable_drafts", true)) return;
		if(getIntent().getExtras() != null) return;
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		if(prefs.contains(stt.from.getId() + "_stt_draft")){
			EditText content = (EditText)findViewById(R.id.tweetContent);
			if(content.getText().toString().trim().length() > 0)
				return; // Don't override if user is tweeting something already!
			
			try{
				stt = SendTweetTask.fromJSONObject(new JSONObject(prefs.getString(stt.from.getId() + "_stt_draft", "{}")));
			} catch(Exception e){e.printStackTrace();}
			prefs.edit().remove(stt.from.getId() + "_stt_draft").commit();
			loadTask();
		}
		invalidateOptionsMenu();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(savedInstanceState != null) {
			if(savedInstanceState.containsKey("lastTheme")) {
				lastTheme = savedInstanceState.getInt("lastTheme");
				setTheme(lastTheme);
			} else setTheme(Utilities.getTheme(getApplicationContext()));
		} else setTheme(Utilities.getTheme(getApplicationContext()));
		super.onCreate(savedInstanceState);
		lengthIndic = 140;
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.composer_screen);
		final EditText content = (EditText)findViewById(R.id.tweetContent);
		content.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				getLengthIndicator(); 
				invalidateOptionsMenu(); 
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) { }
		});
		if(getIntent().getExtras() != null) {
			if(getIntent().hasExtra("stt")){
				try{
					stt = SendTweetTask.fromBundle(getIntent().getBundleExtra("stt"));
					loadTask();
				}catch(Exception e){ e.printStackTrace(); }
			} else if(Intent.ACTION_SEND.equals(getIntent().getAction())){
				if(getIntent().hasExtra(Intent.EXTRA_TEXT)) content.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));
				if(getIntent().hasExtra(Intent.EXTRA_STREAM)){
					stt.attachedImageUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
					stt.isGalleryImage = true;
					invalidateOptionsMenu();
				}
			} else{
				if(getIntent().getExtras().containsKey("text")) content.setText(getIntent().getStringExtra("text"));
				else if(getIntent().getExtras().containsKey("append")) content.append(getIntent().getStringExtra("append") + " ");
				if(getIntent().getExtras().containsKey("image")) {
					stt.attachedImage = getIntent().getStringExtra("image");
					invalidateOptionsMenu();
				}
			}
		}
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("attach_location", false)) {
			getLocation();
		}
		initializeAccountSwitcher(true);
		setProgressBarIndeterminateVisibility(false);
	}
	
	private void initializeAccountSwitcher(boolean firstLoad) {
		ActionBar ab = getActionBar(); 
		ab.setDisplayHomeAsUpEnabled(true);
		if(AccountService.getAccounts().size() > 1) {
			ab.setDisplayShowTitleEnabled(false);
			ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			final ArrayList<Account> accs = AccountService.getAccounts();
			ArrayList<String> screenNames = new ArrayList<String>();
			for(Account a : accs) screenNames.add("@" + a.getUser().getScreenName());
			ArrayAdapter<String> adapt = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, screenNames);
			adapt.setDropDownViewResource(R.layout.spinner_item_actionbar);
			ab.setListNavigationCallbacks(adapt, new OnNavigationListener() {
				@Override
				public boolean onNavigationItemSelected(int itemPosition, long itemId) {
					stt.from = accs.get(itemPosition);
					loadDraft();
					return true;
				}
			});
			if(firstLoad == true){
				stt.from = AccountService.getCurrentAccount();
				loadDraft();
			}
			for(int i = 0; i < accs.size(); i++) {
				if(accs.get(i).getId() == stt.from.getId()) {
					getActionBar().setSelectedNavigationItem(i);
					break;
				}
			}
		} else if(firstLoad == true){
			stt.from = AccountService.getCurrentAccount();
			loadDraft();
		}
	}

	private int lengthIndic;
	private int getLengthIndicator() {
		int shortLength = AccountService.configShortURLLength;
		String text = ((EditText)findViewById(R.id.tweetContent)).getText().toString();
		int toReturn = (140 - text.length());
		if(stt.hasMedia()) toReturn -= AccountService.charactersPerMedia; // TODO: We should do a check on media service
		List<String> urls = new Extractor().extractURLs(text);
		for(String u : urls) {
			if(!shownLinksMessage) {
				shownLinksMessage = true;
				Toast.makeText(getApplicationContext(), getString(R.string.links_shortened), Toast.LENGTH_SHORT).show();
			}
			toReturn += (u.length() - shortLength);
		}
		lengthIndic = toReturn;
		if(toReturn > 140) {
			toReturn = 140;
			lengthIndic = 140;
			//TODO RESHOW MEDIA IF ATTACHED BEFORE
		} 
		if(toReturn < 0) findViewById(R.id.twitlongerUsed).setVisibility(View.VISIBLE);
		else findViewById(R.id.twitlongerUsed).setVisibility(View.GONE);
		invalidateOptionsMenu();
		return toReturn;
	}

	@Override
	public void onResume() {
		super.onResume();
		if(lastTheme == 0) lastTheme = Utilities.getTheme(getApplicationContext());
		else if(lastTheme != Utilities.getTheme(getApplicationContext())) {
			lastTheme = Utilities.getTheme(getApplicationContext());
			recreate();
			return;
		}
	}

	private void saveDraft() {
		if(stt.from == null || !PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("enable_drafts", true) || getIntent().getLongExtra("reply_to", 0l) > 0) {
			finish();
			return;
		}
		final String content = ((EditText)findViewById(R.id.tweetContent)).getText().toString().trim();
		if(content.length() == 0 && stt.attachedImage == null) {
			finish();
			return;
		}
		AlertDialog.Builder prompt = new AlertDialog.Builder(this);
		prompt.setTitle(R.string.draft_str);
		prompt.setMessage(R.string.draft_prompt);
		prompt.setPositiveButton(R.string.yes_str, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				stt.contents = content;
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				try{
					prefs.edit().putString(stt.from.getId() + "_stt_draft", stt.toJSONObject().toString()).commit();
					// _stt_draft is so we don't get any issues with upgrading
				} catch(Exception e){e.printStackTrace();}
				dialog.dismiss();
				finish();
			}
		});
		prompt.setNegativeButton(R.string.no_str, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				finish();
			}
		});
		prompt.create().show();
	}

	@Override
	public void onBackPressed() {
		saveDraft();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("lastTheme", lastTheme);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.composer_actionbar, menu);
		if(getIntent().getLongExtra("reply_to", 0l) > 0) {
			menu.findItem(R.id.sendAction).setTitle(getString(R.string.reply_str) + " (" + Integer.toString(lengthIndic) + ")");
		} else menu.findItem(R.id.sendAction).setTitle(getString(R.string.tweet_str) + " (" + Integer.toString(lengthIndic) + ")");
		
		if(!stt.isGalleryImage && stt.hasMedia()) {
			MenuItem capAct = menu.findItem(R.id.captureAction);
			capAct.setIcon(getTheme().obtainStyledAttributes(new int[] { R.attr.cameraAttachedIcon }).getDrawable(0));
		} else if(stt.hasMedia()) { // could be uri
			MenuItem galAct = menu.findItem(R.id.galleryAction);
			galAct.setIcon(getTheme().obtainStyledAttributes(new int[] { R.attr.galleryAttachedIcon }).getDrawable(0));
		}
		if(stt.location != null) menu.findItem(R.id.locateAction).setIcon(getTheme().obtainStyledAttributes(new int[] { R.attr.locationAttachedIcon }).getDrawable(0));
		final EditText content = (EditText)findViewById(R.id.tweetContent);
		if(stt.attachedImage == null && content.getText().toString().trim().length() == 0) {
			menu.findItem(R.id.sendAction).setEnabled(false);
		} else menu.findItem(R.id.sendAction).setEnabled(true);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			saveDraft();
			return true;
		case R.id.locateAction:
			if(stt.location == null) getLocation();
			else {
				stt.location = null;
				invalidateOptionsMenu();
			}
			return true;
		case R.id.sendAction:
			if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("confirm_send_prompt", false)) {
				AlertDialog.Builder diag = new AlertDialog.Builder(this);
				diag.setTitle(R.string.tweet_str);
				diag.setMessage(getString(R.string.confirm_send_prompt).replace("{account}", stt.from.getUser().getScreenName()));
				diag.setPositiveButton(R.string.yes_str, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						performSend();
					}
				});
				diag.setNegativeButton(R.string.no_str, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
				});
				diag.create().show();
			} else performSend();
			return true;
		case R.id.captureAction:
			if(stt.attachedImage != null) {
				stt.attachedImage = null;
				stt.attachedImageUri = null;
				getLengthIndicator();
			} else captureImage();
			return true;
		case R.id.galleryAction:
			if(stt.attachedImage != null) {
				stt.attachedImage = null;
				stt.attachedImageUri = null;
				getLengthIndicator();
			} else selectImage();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private boolean isGettingLocation;

	private void getLocation() {
		if(isGettingLocation) return;
		isGettingLocation = true;
		setProgressBarIndeterminateVisibility(true);
		final LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				locationManager.removeUpdates(this);
				isGettingLocation = false;
				stt.location = new GeoLocation(location.getLatitude(), location.getLongitude());
				setProgressBarIndeterminateVisibility(false);
				invalidateOptionsMenu();
			}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
			public void onProviderEnabled(String provider) {}
			public void onProviderDisabled(String provider) {}
		};
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
	}
	
	private void performSend() {
		if(getLengthIndicator() < 0) {
			stt.twtlonger = true;
		}
		
		stt.contents = ((EditText)findViewById(R.id.tweetContent)).getText().toString();
		
		Log.d("from", stt.from.toString() + "");
		
		stt.in_reply_to = getIntent().getLongExtra("reply_to", 0);
		stt.replyToName = getIntent().getStringExtra("reply_to_name");
		
		//Intent send = new Intent(this, SendTweetService.class);
		//send.setAction(SendTweetService.ADD_TWEET);
		//send.putExtra("send_tweet_task", stt.toBundle());
		
		//startService(send);
		SendTweetService.addTweet(stt);
		finish();
	}
	
	private Integer getFileSize(File in){
		try{
			FileInputStream fis = new FileInputStream(in);
			int r = fis.available();
			fis.close();
			return r;
		}catch(Exception e){
			e.printStackTrace(); return 0;
		}
	}
	
	public static final Integer CAMERA_SELECT_INTENT = 500;
	public static final Integer GALLERY_SELECT_INTENT = 600;

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if(resultCode == RESULT_OK) {
			if(requestCode == GALLERY_SELECT_INTENT){
				if(getFileSize(new File(stt.attachedImage)) == 0) {
					Log.d("e", "Empty File. Using " + intent.getData().toString());
					stt.attachedImageUri = intent.getData();
				}
			}
			getLengthIndicator();
			invalidateOptionsMenu();
		} else if(resultCode == RESULT_CANCELED) {
			File attachedCapture = new File(stt.attachedImage);
			if(attachedCapture != null && attachedCapture.exists()) {
                attachedCapture.delete();
            }
			stt.attachedImage = null;
		}
	}

	private void captureImage() {
		if(!Utilities.isIntentAvailable(this, MediaStore.ACTION_IMAGE_CAPTURE)) {
			Toast.makeText(getApplicationContext(), R.string.no_camera_app, Toast.LENGTH_SHORT).show();
			return;
		}
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		stt.isGalleryImage = false;
		stt.attachedImage = Utilities.generateImageFileName(this);
		takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(stt.attachedImage)));
		startActivityForResult(takePictureIntent, CAMERA_SELECT_INTENT);
	}
	private void selectImage() {
		try {
			stt.isGalleryImage = true;
			stt.attachedImage = Utilities.createImageFile(this).getAbsolutePath();
			Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT, null).setType("image/*")
					.putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse(stt.attachedImage))
					.putExtra("outputFormat", Bitmap.CompressFormat.PNG.name());
			startActivityForResult(galleryIntent, GALLERY_SELECT_INTENT);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
	}
	
}