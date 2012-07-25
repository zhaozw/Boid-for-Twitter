package com.teamboid.twitter.services;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.teamboid.twitter.Account;
import com.teamboid.twitter.AccountManager;
import com.teamboid.twitter.R;
import com.teamboid.twitter.contactsync.AndroidAccountHelper;
import com.teamboid.twitter.listadapters.FeedListAdapter;
import com.teamboid.twitter.listadapters.MediaFeedListAdapter;
import com.teamboid.twitter.listadapters.MessageConvoAdapter;
import com.teamboid.twitter.listadapters.SearchFeedListAdapter;
import com.teamboid.twitter.listadapters.TrendsListAdapter;
import com.teamboid.twitter.listadapters.UserListDisplayAdapter;
import com.teamboid.twitter.utilities.NetworkUtils;

/**
 * The service that stays running the background; authorizes, loads, and manages the current user's accounts.
 * @author Aidan Follestad
 */
public class AccountService extends Service {

	public static Twitter pendingClient;
	public static Context activity;
	private static ArrayList<Account> accounts;
	public static ArrayList<FeedListAdapter> feedAdapters;
	public static ArrayList<MediaFeedListAdapter> mediaAdapters;
	public static ArrayList<MessageConvoAdapter> messageAdapters;
	public static TrendsListAdapter trendsAdapter;
	public static SearchFeedListAdapter nearbyAdapter;
	public static ArrayList<SearchFeedListAdapter> searchFeedAdapters;
	public static UserListDisplayAdapter myListsAdapter;
	public static int configShortURLLength;
	public static int charactersPerMedia;
	public static long selectedAccount;

	public static ArrayList<Account> getAccounts() {
		if(accounts == null) accounts = new ArrayList<Account>();
		return accounts;
	}
	public static boolean existsAccount(long accId) {
		boolean found = false;
		for(int i = 0; i < accounts.size(); i++) {
			if(accounts.get(i).getId() == accId) {
				found = true;
				break;
			}
		}
		return found;
	}
	public static void setAccount(int index, Account acc) {
		if(accounts == null) accounts = new ArrayList<Account>();
		accounts.set(index, acc);
	}
	public static Account getCurrentAccount() {
		if(selectedAccount == 0) return null;
		Account toReturn = null;
		for(Account acc : getAccounts()) {
			if(acc.getUser().getId() == selectedAccount) {
				toReturn = acc;
				break;
			}
		}
		return toReturn;
	}
	public static void removeAccount(Context activity, Account acc) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		prefs.edit().remove(Long.toString(acc.getId()) + "_columns").commit();
		prefs.edit().remove(Long.toString(acc.getId()) + "_muting").commit();
		activity.getSharedPreferences("accounts", 0).edit().remove(acc.getToken()).commit();
		for(int i = 0; i < accounts.size(); i++) {
			if(accounts.get(i).getToken().equals(acc.getToken())) {
				AccountManager.unregisterFromPush(accounts.get(i).getId(), ((Activity)AccountService.activity), new Runnable() {
					@Override
					public void run() {
						/* do nothing */
					}
					
				}, new Runnable(){
					@Override
					public void run(){
						Toast.makeText(AccountService.activity, R.string.push_error, Toast.LENGTH_LONG).show();
					}
				});
				
				accounts.remove(i);
				break;
			}
		}
	}
	
	public static ConfigurationBuilder getConfiguration(String token, String secret){
		return new ConfigurationBuilder()
			.setDebugEnabled(true)
			.setIncludeEntitiesEnabled(true)
			.setIncludeRTsEnabled(true)
			.setOAuthConsumerKey("5LvP1d0cOmkQleJlbKICtg")
			.setOAuthConsumerSecret("j44kDQMIDuZZEvvCHy046HSurt8avLuGeip2QnOpHKI")
			.setOAuthAccessToken(token)
			.setOAuthAccessTokenSecret(secret)
			.setUseSSL(PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("enable_ssl", false));
	}
	
	public static void verifyAccount(final String verifier) {
		final Toast act = Toast.makeText(activity, activity.getString(R.string.authorizing_account), Toast.LENGTH_LONG);
		act.show();
		new Thread(new Runnable() {
			public void run() {
				try {
					if(pendingClient == null) {
						((Activity)activity).runOnUiThread(new Runnable() {
							@Override
							public void run() { 
								act.cancel();
								Toast.makeText(activity, activity.getString(R.string.authorization_error), Toast.LENGTH_LONG).show();
								activity.sendBroadcast(new Intent(AccountManager.END_LOAD));
							}
						});
						return;
					}
					final AccessToken accessToken = pendingClient.getOAuthAccessToken(verifier);
					ConfigurationBuilder cb = getConfiguration(accessToken.getToken(), accessToken.getTokenSecret());
					final Twitter toAdd = new TwitterFactory(cb.build()).getInstance();
					final User toAddUser = toAdd.verifyCredentials();
					ArrayList<Account> accs = getAccounts();
					for(Account user : accs) {
						if(user.getUser().getId() == toAddUser.getId()) {
							((Activity)activity).runOnUiThread(new Runnable() {
								@Override
								public void run() { 
									act.cancel();
									Toast.makeText(activity, activity.getString(R.string.account_already_added), Toast.LENGTH_LONG).show();
									activity.sendBroadcast(new Intent(AccountManager.END_LOAD));
								}
							});
							return;
						}
					}
					activity.getSharedPreferences("accounts", 0).edit().putString(accessToken.getToken(), accessToken.getTokenSecret()).commit();
					accounts.add(new Account(activity, toAdd, accessToken.getToken()).setSecret(accessToken.getTokenSecret()).setUser(toAddUser));
					pendingClient = null;
					((Activity)activity).runOnUiThread(new Runnable() {
						@Override
						public void run() { 
							act.cancel();
							activity.sendBroadcast(new Intent(AccountManager.END_LOAD).putExtra("access_token", accessToken.getToken()));
						}
					});
				} catch (final TwitterException e) {
					e.printStackTrace();
					((Activity)activity).runOnUiThread(new Runnable() {
						@Override
						public void run() { 
							act.cancel();
							Toast.makeText(activity, activity.getString(R.string.authorization_error) + " " + e.getErrorMessage(), Toast.LENGTH_LONG).show();
							activity.sendBroadcast(new Intent(AccountManager.END_LOAD));
						}
					});
				}
			}
		}).start();
	}
	
	public static List<Account> getCachedAccounts(Context activity){
		List<Account> r = new ArrayList<Account>();
		File cachedFile = new File(activity.getFilesDir(), "acconuts.cache.json");
		if(cachedFile.lastModified() > new Date().getTime() - ( 1000 * 60 * 60 * 5 ) ){
			// 5 hour cache
			try{
				BufferedReader bir = new BufferedReader( new InputStreamReader( new FileInputStream(cachedFile ) ) );
				String line = bir.readLine();
				bir.close();
				
				JSONArray cache = new JSONArray(line);
				for(int i = 0; i <= cache.length(); i++){
					accounts.add( Account.unserialize(activity, cache.getJSONObject(i) ) );
				}
			} catch(Exception e){ e.printStackTrace(); }
		}
		return r;
	}
	
	public static void initAccountServiceIfNeeded(Activity ac){
		if(activity == null) activity = ac;
		if(accounts == null) accounts = new ArrayList<Account>();
		if(accounts.size() == 0){
			loadTwitterConfig(ac);
			loadCachedAccounts();
		}
	}
	
	public static boolean loadCachedAccounts(){
		List<Account> cached = getCachedAccounts(activity);
		if(cached.size() == 0) return false;
		
		accounts.addAll( cached );
		return true;
	}

	public static void loadAccounts() {
		if(activity == null) return;
		final Map<String, ?> accountStore = activity.getSharedPreferences("accounts", 0).getAll();
		if(accountStore.size() == 0) {
			activity.startActivity(new Intent(activity, AccountManager.class));			
			return;
		} else if(getAccounts().size() == accountStore.size()) return;
		
		// Cache
		if(loadCachedAccounts()) return;
		
		if(!NetworkUtils.haveNetworkConnection(activity)) {
			Toast.makeText(activity, activity.getString(R.string.no_internet), Toast.LENGTH_LONG).show();
			return;
		}
		final int lastAccountCount = getAccounts().size();
		final ProgressDialog dialog = ProgressDialog.show(activity, "", activity.getString(R.string.loading_accounts), true);
		dialog.show();
		new AsyncTask<Integer, Integer, Integer>() {
			
			public Integer doInBackground(Integer... in) {
				JSONArray jAccounts = new JSONArray();
				
				android.accounts.AccountManager am = android.accounts.AccountManager.get(activity);
				HashMap<String, android.accounts.Account> accs = new HashMap<String, android.accounts.Account>();
				
				android.accounts.Account temp[] = am.getAccountsByType(AndroidAccountHelper.ACCOUNT_TYPE);
				for(android.accounts.Account acc : temp)
					accs.put(am.getUserData(acc, "accId"), acc);
				
				for(final String token : accountStore.keySet()) {
					boolean skip = false;
					for(int i = 0; i < accounts.size(); i++) {
						Account acc = accounts.get(i);
						if(acc.getToken().equals(token)) {
							skip = true;
							if(accs.containsKey( acc.getId() )){
								accs.remove( acc.getId() );
							}
							break;
						}
					}
					if(skip) continue;
					ConfigurationBuilder cb = getConfiguration(token, accountStore.get(token).toString());
					final Twitter toAdd = new TwitterFactory(cb.build()).getInstance();
					try {
						final User accountUser = toAdd.verifyCredentials();
						
						// Android stuff
						boolean exists = false;
						if(accs.containsKey( toAdd.getId() ) ){
							accs.remove( toAdd.getId() );
							exists = true;
						}
						
						Account aToAdd = new Account(activity, toAdd, token).setSecret(accountStore.get(token).toString()).setUser(accountUser);
						if(!exists){
							AndroidAccountHelper.addAccount(activity, aToAdd);
						}
						try{ jAccounts.put(aToAdd.serialize()); } catch(Exception e){ e.printStackTrace(); }
						accounts.add(aToAdd);
					} catch (final TwitterException e) {
						e.printStackTrace();
						((Activity)activity).runOnUiThread(new Runnable() {
							@Override
							public void run() { Toast.makeText(activity, activity.getString(R.string.failed_load_account) + " " + e.getErrorMessage(), Toast.LENGTH_LONG).show(); }
						});
					}
				}
				((Activity)activity).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(getAccounts().size() > 0) {
							if(getAccounts().size() != lastAccountCount) {
								selectedAccount = accounts.get(0).getId();
								activity.sendBroadcast(new Intent(AccountManager.END_LOAD).putExtra("last_account_count", lastAccountCount == 0));
							}
						} else activity.startActivity(new Intent(activity, AccountManager.class));
						((Activity)activity).invalidateOptionsMenu();
						dialog.dismiss();
					}
				});
				
				try{
					FileOutputStream fos = activity.openFileOutput("accounts.cache.json", Context.MODE_PRIVATE);
					OutputStreamWriter bos = new OutputStreamWriter(fos);
					bos.write(jAccounts.toString());
					bos.close();
					fos.close();
				} catch(Exception e){
					e.printStackTrace();
				}
					
				// Remove all old accounts (or if username has changed/other circumastances)
				for(android.accounts.Account acc : accs.values()){
					if(AccountService.existsAccount( Long.parseLong(am.getUserData(acc, "accId") ))){
						am.removeAccount(acc, null, null );
					}
				}
				return 0;
			}
		}.execute();
	}

	public static void loadTwitterConfig(final Activity context) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		long lastConfigUpdate = prefs.getLong("last_config_update", new Date().getTime() - 86400000);
		configShortURLLength = 21;
		charactersPerMedia = 21;
		if(lastConfigUpdate <= (new Date().getTime() - 86400000)) {
			Log.i("BOID", "Loading Twitter config (this should only happen once every 24 hours)...");
			new Thread(new Runnable() {
				public void run() {
					try {
						final Twitter tempClient = new TwitterFactory().getInstance();
						tempClient.setOAuthConsumer("5LvP1d0cOmkQleJlbKICtg", "j44kDQMIDuZZEvvCHy046HSurt8avLuGeip2QnOpHKI");
						TwitterAPIConfiguration config = tempClient.getAPIConfiguration();
						configShortURLLength = config.getShortURLLength();
						charactersPerMedia = config.getCharactersReservedPerMedia();
						prefs.edit().putInt("shorturl_length", config.getShortURLLength()).putLong("last_config_update", new Date().getTime())
							.putInt("mediachars_length", config.getCharactersReservedPerMedia()).commit();
					} catch(final TwitterException e) {
						e.printStackTrace();
						configShortURLLength = 21;
						charactersPerMedia = 21;
						context.runOnUiThread(new Runnable() {
							@Override
							public void run() { 
								Toast.makeText(context, context.getString(R.string.failed_fetch_config).replace("{reason}", e.getErrorMessage()), Toast.LENGTH_LONG).show();
							}
						});
					}
				}
			}).start();
		}
	}

	public static FeedListAdapter getFeedAdapter(Activity activity, String id, long account) {
		return getFeedAdapter(activity, id, account, true);
	}
	public static FeedListAdapter getFeedAdapter(Activity activity, String id, long account, boolean createIfNull) {
		if(feedAdapters == null) feedAdapters = new ArrayList<FeedListAdapter>();
		FeedListAdapter toReturn = null;
		for(FeedListAdapter adapt : feedAdapters) {
			if(id.equals(adapt.ID) && account == adapt.account) {
				toReturn = adapt;
				break;
			}
		}
		if(toReturn == null && createIfNull) {
			toReturn = new FeedListAdapter(activity, id, account);
			feedAdapters.add(toReturn);
		}
		return toReturn;
	}
	public static void clearFeedAdapter(Activity activity, String id, long account) {
		if(feedAdapters == null) return;
		for(int i = 0; i < feedAdapters.size(); i++) {
			FeedListAdapter curAdapt = feedAdapters.get(i); 
			if(curAdapt.ID.equals(id) && curAdapt.account == account) {
				curAdapt.clear();
				feedAdapters.set(i, curAdapt);
				break;
			}
		}
	}
	public static MediaFeedListAdapter getMediaFeedAdapter(Activity activity, String id, long account) {
		if(mediaAdapters == null) mediaAdapters = new ArrayList<MediaFeedListAdapter>();
		MediaFeedListAdapter toReturn = null;
		for(MediaFeedListAdapter adapt : mediaAdapters) {
			if(id.equals(adapt.ID) && account == adapt.account) {
				toReturn = adapt;
				break;
			}
		}
		if(toReturn == null) {
			toReturn = new MediaFeedListAdapter(activity, id, account);
			mediaAdapters.add(toReturn);
		}
		return toReturn;
	}
	public static MessageConvoAdapter getMessageConvoAdapter(Activity activity, long account) {
		if(messageAdapters == null) messageAdapters = new ArrayList<MessageConvoAdapter>();
		MessageConvoAdapter toReturn = null;
		for(MessageConvoAdapter adapt : messageAdapters) {
			if(account == adapt.account) {
				toReturn = adapt;
				break;
			}
		}
		if(toReturn == null) {
			toReturn = new MessageConvoAdapter(activity, account);
			messageAdapters.add(toReturn);
		}
		return toReturn;
	}
	public static TrendsListAdapter getTrendsAdapter(Activity activity) {
		if(trendsAdapter == null) trendsAdapter = new TrendsListAdapter(activity);
		return trendsAdapter;
	}
	public static SearchFeedListAdapter getSearchFeedAdapter(Activity activity, String id, long account) {
		if(searchFeedAdapters == null) searchFeedAdapters = new ArrayList<SearchFeedListAdapter>();
		SearchFeedListAdapter toReturn = null;
		for(SearchFeedListAdapter adapt : searchFeedAdapters) {
			if(id.equals(adapt.ID) && account == adapt.account) {
				toReturn = adapt;
				break;
			}
		}
		if(toReturn == null) {
			toReturn = new SearchFeedListAdapter(activity, id, account);
			searchFeedAdapters.add(toReturn);
		}
		return toReturn;
	}
	public static SearchFeedListAdapter getNearbyAdapter(Activity activity) {
		if(nearbyAdapter == null) nearbyAdapter = new SearchFeedListAdapter(activity, 0);
		return nearbyAdapter;
	}
	public static UserListDisplayAdapter getMyListsAdapter(Activity activity) {
		if(myListsAdapter == null) myListsAdapter = new UserListDisplayAdapter(activity);
		return myListsAdapter;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		accounts = new ArrayList<Account>();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	@Override
	public void onDestroy() { super.onDestroy(); }
	@Override
	public IBinder onBind(Intent intent) { return null; }

	public static Account getAccount(long accId) {
		Account result = null;
		for(int i = 0; i < accounts.size(); i++) {
			if(accounts.get(i).getId() == accId) {
				result = accounts.get(i);
			}
		}
		return result;
	}
}