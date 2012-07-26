package com.teamboid.twitter;

import java.util.List;

import com.teamboid.twitter.services.AccountService;
import com.teamboid.twitter.utilities.Utilities;
import com.teamboid.twitter.views.TimePreference;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.view.MenuItem;
import android.widget.NumberPicker;
import android.widget.TextView;
import net.robotmedia.billing.BillingController;
import net.robotmedia.billing.BillingRequest;
import net.robotmedia.billing.helper.AbstractBillingObserver;
import net.robotmedia.billing.model.Transaction;

/**
 * The settings screen, displays fragments that contain preferences.
 * @author Aidan Follestad
 */
public class SettingsScreen extends PreferenceActivity  {

	private int lastTheme;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if(savedInstanceState != null && savedInstanceState.containsKey("lastTheme")) {
			lastTheme = savedInstanceState.getInt("lastTheme");
			setTheme(lastTheme);
		} else setTheme(Utilities.getTheme(getApplicationContext()));
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		AbstractBillingObserver mBillingObserver = new AbstractBillingObserver(this) {
			@Override
			public void onBillingChecked(boolean supported) { }
			@Override
			public void onPurchaseStateChanged(String itemId, Transaction.PurchaseState state) { }
			@Override
			public void onRequestPurchaseResponse(String itemId, BillingRequest.ResponseCode response) { }
		};
		BillingController.registerObserver(mBillingObserver);
		BillingController.checkBillingSupported(this);
	}

	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}

	@Override
	public void onResume() {
		super.onResume();
		if(lastTheme == 0) lastTheme = Utilities.getTheme(getApplicationContext());
		else if(lastTheme != Utilities.getTheme(getApplicationContext())) {
			lastTheme = Utilities.getTheme(getApplicationContext());
			recreate();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("lastTheme", lastTheme);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			super.onBackPressed();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public static class HelpFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.help_category);
			findPreference("version").setSummary(Utilities.getVersionName(getActivity()));
			findPreference("build").setSummary(Utilities.getVersionCode(getActivity()));
			findPreference("share_boid").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, getString(R.string.share_boid_content)), getString(R.string.share_boid_title)));
					return false;
				}
			});
			findPreference("boidapp").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					startActivity(new Intent(getActivity(), ProfileScreen.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("screen_name", "boidapp"));
					return false;
				}
			});
			findPreference("donate").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					BillingController.requestPurchase(getActivity(), "com.teamboid.twitter.donate", true);
					return false;
				}
			});

		}
	}

    public static class GeneralFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.general_category);
			findPreference("enable_ssl").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					int index = 0;
					for(Account acc : AccountService.getAccounts()) {
						acc.setClient(acc.getClient().setSslEnabled((Boolean)newValue));
						AccountService.setAccount(index, acc);
						index++;
					}
					return true;
				}
			});
			final Preference clearHistory = findPreference("clear_search_history");
			clearHistory.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					clearHistory.setEnabled(false);
					SearchRecentSuggestions suggestions = new SearchRecentSuggestions(getActivity(),
							SearchSuggestionsProvider.AUTHORITY, SearchSuggestionsProvider.MODE);
					suggestions.clearHistory();
					return false;
				}
			});
		}
	}

    public static class ComposerFragment extends PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.composer_category);
		}
	}

    public static class AppearanceFragment extends PreferenceFragment {
		
		@Override		
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.appearance_category);
			findPreference("boid_theme").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					getActivity().recreate();
					return true;
				}
			});
			findPreference("font_size").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					showFontDialog();
					return false;
				}
			});
		}
		
		private void showFontDialog() {
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			Dialog diag = new Dialog(getActivity());
			diag.setContentView(R.layout.font_size_dialog);
			diag.setCancelable(true);
			diag.setTitle(R.string.font_size);
			final TextView display = (TextView)diag.findViewById(R.id.fontSizeDialogExample);
			final NumberPicker picker = (NumberPicker)diag.findViewById(R.id.fontSizePicker);
			picker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
				@Override
				public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
					display.setText(getString(R.string.boid_is_awesome) + " " + newVal + "pt");
					display.setTextSize(newVal);
					prefs.edit().putString("font_size", Integer.toString(newVal)).apply();
				}
			});
			picker.setMinValue(11);
			picker.setMaxValue(26);
			picker.setValue(Integer.parseInt(prefs.getString("font_size", "14")));
			diag.show();
		}
	}

    public static class NightModeFragment extends PreferenceFragment {
		
		private void displayTime(String time, String prefName, int summaryResId) {
			TimePreference pref = (TimePreference)findPreference(prefName);
			String toDisplay = null;
			int minute = TimePreference.getMinute(time);
			if(minute < 10) toDisplay = ":0" + Integer.toString(minute);
			else toDisplay = ":" + Integer.toString(minute);
			int hour = TimePreference.getHour(time);
			if(hour == 0) {
				toDisplay = "12" + toDisplay + " AM";
			} else if(hour > 12) {
				hour -= 12;
				toDisplay = Integer.toString(hour) + toDisplay + " PM";
			} else toDisplay = Integer.toString(hour) + toDisplay + " AM";
			pref.setSummary(getString(summaryResId).replace("{time}", toDisplay));
		}
		
		public void displayThemeText(String theme) {
			final Preference pref = findPreference("night_mode_theme");
			if(theme.equals("0")) {
				pref.setSummary(getString(R.string.night_mode_switches_to).replace("{theme}", "dark"));
			} else if(theme.equals("1")) {
				pref.setSummary(getString(R.string.night_mode_switches_to).replace("{theme}", "light"));
			} else if(theme.equals("2")) {
				pref.setSummary(getString(R.string.night_mode_switches_to).replace("{theme}", "dark and light"));
			} else if(theme.equals("3")) {
				pref.setSummary(getString(R.string.night_mode_switches_to).replace("{theme}", "pure black"));
			}
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.nightmode_category);
			findPreference("night_mode_time").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					displayTime((String)newValue, "night_mode_time", R.string.night_mode_enabled_at);
					return true;
				}
			});
			findPreference("night_mode_endtime").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					displayTime((String)newValue, "night_mode_endtime", R.string.night_mode_disabled_at);
					return true;
				}
			});
			findPreference("night_mode_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					displayThemeText((String)newValue);
					return true;
				}
			});
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			if(prefs.contains("night_mode_time")) {
				displayTime(prefs.getString("night_mode_time", "00:00"), "night_mode_time", R.string.night_mode_enabled_at);
			}
			if(prefs.contains("night_mode_endtime")) {
				displayTime(prefs.getString("night_mode_endtime", "00:00"), "night_mode_endtime", R.string.night_mode_disabled_at);
			}
			if(prefs.contains("night_mode_theme")) {
				displayThemeText(prefs.getString("night_mode_theme", "0"));
			}
		}
	}
}