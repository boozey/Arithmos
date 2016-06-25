package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageButton;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.Achievements;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.OnInvitationReceivedListener;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.ParticipantResult;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.turnbased.OnTurnBasedMatchUpdateReceivedListener;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatchConfig;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMultiplayer;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;
import com.nakedape.arithmos.purchaseUtils.IabHelper;
import com.nakedape.arithmos.purchaseUtils.IabResult;
import com.nakedape.arithmos.purchaseUtils.Inventory;
import com.nakedape.arithmos.purchaseUtils.Purchase;
import com.nakedape.arithmos.purchaseUtils.SkuDetails;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        OnTurnBasedMatchUpdateReceivedListener, OnInvitationReceivedListener{

    private static final String LOG_TAG = "MainActivity";

    // Activity Result request codes
    public static int REQUEST_LEVEL_PLAYED = 300;
    public static int REQUEST_TAKE_MATCH_TURN = 301;

    public static final String GENERAL_PREFS = "GENERAL_PREFS";

    private Context context;
    private RelativeLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(GENERAL_PREFS, MODE_PRIVATE);
        mAutoStartSignInFlow = prefs.getBoolean(AUTO_SIGN_IN, true);

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER)
                // add other APIs and scopes here as needed
                .build();


        // Initialize fields and UI
        setContentView(R.layout.activity_main);
        context = this;
        rootLayout = (RelativeLayout) findViewById(R.id.main_activity_rootlayout);
        rootLayout.findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInClicked(v);
            }
        });

        if (!mAutoStartSignInFlow) rootLayout.findViewById(R.id.achievements_button).setVisibility(View.GONE);

        ListView activityList = (ListView)rootLayout.findViewById(R.id.activity_listview);
        View header = getLayoutInflater().inflate(R.layout.activity_button_bar, null);
        activityList.addHeaderView(header);
        activityListAdapter = new ActivityListAdapter(R.layout.activity_list_item);
        activityList.setAdapter(activityListAdapter);

        ListView specialList = (ListView)rootLayout.findViewById(R.id.special_store_listview);
        View header2 = getLayoutInflater().inflate(R.layout.add_jewels_button_bar, null);
        specialList.addHeaderView(header2);

        // Setup Ads
        showAds = prefs.getBoolean(SHOW_ADS, true);
        if (showAds) {
            MobileAds.initialize(getApplicationContext(), "ca-app-pub-4640479150069852~3029191523");
            AdView mAdView = (AdView) rootLayout.findViewById(R.id.adView);
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mAdView.loadAd(adRequest);

            AdRequest intstAdRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mInterstitialAd = new InterstitialAd(this);
            mInterstitialAd.setAdUnitId(getString(R.string.interstitial_ad_unit_id));
            mInterstitialAd.loadAd(intstAdRequest);
        }

        // Load cached data if it exists
        gameBase = new ArithmosGameBase();
        loadCachedGame();

        File matchCacheFile = new File(getCacheDir(), MatchGameActivity.matchCacheFileName);
        if (matchCacheFile.exists()){
            loadCachedMatch();
        } else {
            File levelCacheFile = new File(getCacheDir(), GameActivity.levelCacheFileName);
            if (levelCacheFile.exists()) {
                Intent intent = new Intent(this, GameActivity.class);
                startActivityForResult(intent, REQUEST_LEVEL_PLAYED);
            }
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAutoStartSignInFlow)
            mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCachedGame();
        if (mAutoStartSignInFlow && !mGoogleApiClient.isConnected() && activityListAdapter.getCount() == 0) {
            rootLayout.findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
        }
        loadInterstitialAd();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id){
            case R.id.action_reset_game:
                showResetGamePrompt();
                return true;
            case R.id.action_order_history:
                showOrderHistoryPopup();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                final View popup = rootLayout.findViewById(R.id.pop_up);
                if (popup != null) {
                    AnimatorSet set = Animations.slideOutDown(popup, 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(popup);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    set.start();
                    return true;
                }
            default:
                return super.onKeyDown(keycode, e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Google play services sign-in
        if (requestCode == RC_SIGN_IN) {
            mSignInClicked = false;
            mResolvingConnectionFailure = false;
            if (resultCode == RESULT_OK) {
                mGoogleApiClient.connect();
            } else if (resultCode == RESULT_CANCELED){
                hideLoadingPopup();
                showUsePlayGamesPrompt();
            }
            else {
                // Bring up an error dialog to alert the user that sign-in
                // failed. The R.string.signin_failure should reference an error
                // string in your strings.xml file that tells the user they
                // could not be signed in, such as "Unable to sign in."
                BaseGameUtils.showActivityResultError(this,
                        requestCode, resultCode, R.string.sign_in_failed);
                hideLoadingPopup();
            }
        }

        // Level played
        if (requestCode == REQUEST_LEVEL_PLAYED){
            if (!gameBaseNeedsRefresh) {
                gameBaseNeedsRefresh = true;
                refreshGameState();
            }
            // Save or delete save as necessary
            if (resultCode == Activity.RESULT_OK) {
                if (intent.hasExtra(GameActivity.SAVED_GAME)) {
                    activityListAdapter.removeSave(intent.getStringExtra(GameActivity.SAVED_GAME));
                    deleteSavedGame(intent.getStringExtra(GameActivity.SAVED_GAME));
                }
            } else if (resultCode == Activity.RESULT_CANCELED){
                if (intent.hasExtra(GameActivity.SAVED_GAME)){
                    // show SnackBar popup
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), R.string.game_saved_message, Snackbar.LENGTH_SHORT);
                    snackbar.show();
                }
            }
        }

        // Turn based match initiated
        if (requestCode == RC_SELECT_PLAYERS) {
            if (resultCode != Activity.RESULT_OK) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            // Get the invitee list.
            final ArrayList<String> invitees =
                    intent.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

            // Get auto-match criteria.
            Bundle autoMatchCriteria = null;
            int minAutoMatchPlayers = intent.getIntExtra(
                    Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
            int maxAutoMatchPlayers = intent.getIntExtra(
                    Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

            if (minAutoMatchPlayers > 0) {
                autoMatchCriteria = RoomConfig.createAutoMatchCriteria(
                        minAutoMatchPlayers, maxAutoMatchPlayers, 0);
            } else {
                autoMatchCriteria = null;
            }

            TurnBasedMatchConfig tbmc = TurnBasedMatchConfig.builder()
                    .addInvitedPlayers(invitees)
                    .setAutoMatchCriteria(autoMatchCriteria)
                    .build();

            // Create and start the match.
            Games.TurnBasedMultiplayer
                    .createMatch(mGoogleApiClient, tbmc)
                    .setResultCallback(new MatchInitiatedCallback());
        }

        // In-app purchases
        if (requestCode == PURCHASE_REQUEST){
            if (iabHelper != null)
                iabHelper.handleActivityResult(requestCode, resultCode, intent);
        }

    }

    // Game Activity List
    private ActivityListAdapter activityListAdapter;
    private String gameToDeleteId;
    private boolean retryDeleteSavedLevel = false;

    private class ActivityListAdapter extends BaseAdapter{
        private int resource_id;
        private LayoutInflater mInflater;
        private ArrayList<GameActivityItem> history;
        private ArrayList<GameActivityItem> matches;
        private ArrayList<GameActivityItem> saves;
        private ArrayList<GameActivityItem> items;
        private GameActivityItem emptyItem;
        private ImageManager imageManager = ImageManager.create(context);
        private static final int ALL = 0, MATCHES = 1, SAVES = 2, HISTORY = 3;
        private int mode = ALL;

        public ActivityListAdapter(int resource_id){
            this.resource_id = resource_id;
            history = new ArrayList<>();
            saves = new ArrayList<>();
            matches = new ArrayList<>();
            items = new ArrayList<>();
            emptyItem = new GameActivityItem(GameActivityItem.EMPTY_ITEM);
            emptyItem.description = getString(R.string.empty_activity_item);
            //items.add(emptyItem);
        }

        public void setMode(int mode){
            this.mode = mode;
            notifyDataSetChanged();
        }

        public void sort(){
            Collections.sort(items, new Comparator<GameActivityItem>() {
                @Override
                public int compare(GameActivityItem item2, GameActivityItem item1)
                {
                    return  (int)(item1.timeStamp - item2.timeStamp);
                }
            });
        }

        public void removeGoogleItems(){
            saves.clear();
            saves.add(emptyItem);
            matches.clear();
            matches.add(emptyItem);
            items.clear();
            items.addAll(history);
            notifyDataSetChanged();
        }

        public void addItems(ArrayList<GameActivityItem> newItems){
            for (GameActivityItem item : newItems){
                if (items.contains(item)) items.remove(item);
                items.add(item);
                switch (item.itemType){
                    case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                        if (saves.contains(item))
                            saves.remove(item);
                        saves.add(item);
                        break;
                    case GameActivityItem.COMPLETED_LEVEL:
                    case GameActivityItem.INCOMPLETE_LEVEL:
                        if (history.contains(item))
                            history.remove(item);
                        history.add(item);
                        break;
                    case GameActivityItem.MATCH_COMPLETE:
                    case GameActivityItem.MATCH_INVITATION:
                    case GameActivityItem.MATCH_MY_TURN:
                    case GameActivityItem.MATCH_THEIR_TURN:
                        if (matches.contains(item))
                            matches.remove(item);
                        matches.add(item);
                        break;
                }
            }
            if (items.size() > 1)
                items.remove(emptyItem);
            if (saves.size() > 1)
                saves.remove(emptyItem);
            if (history.size() > 1)
                history.remove(emptyItem);
            if (matches.size() > 1)
                matches.remove(emptyItem);
            notifyDataSetChanged();
        }

        public void addItem(GameActivityItem item){
            if (items.contains(item)) items.remove(item);
            items.add(item);
            switch (item.itemType){
                case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                    if (saves.contains(item))
                        saves.remove(item);
                    saves.add(item);
                    break;
                case GameActivityItem.COMPLETED_LEVEL:
                case GameActivityItem.INCOMPLETE_LEVEL:
                    if (history.contains(item))
                        history.remove(item);
                    history.add(item);
                    break;
                case GameActivityItem.EMPTY_ITEM:
                    break;
                case GameActivityItem.MATCH_COMPLETE:
                case GameActivityItem.MATCH_INVITATION:
                case GameActivityItem.MATCH_MY_TURN:
                case GameActivityItem.MATCH_THEIR_TURN:
                    if (matches.contains(item))
                        matches.remove(item);
                    matches.add(item);
                    break;
            }
            if (items.size() > 1)
                items.remove(emptyItem);
            if (saves.size() > 1)
                saves.remove(emptyItem);
            if (history.size() > 1)
                history.remove(emptyItem);
            if (matches.size() > 1)
                matches.remove(emptyItem);
            notifyDataSetChanged();
        }

        public void clearList(){
            history = new ArrayList<>(1);
            history.add(emptyItem);
            saves = new ArrayList<>(1);
            saves.add(emptyItem);
            items = new ArrayList<>(1);
            items.add(emptyItem);
            notifyDataSetChanged();
        }

        public void removeItem(GameActivityItem item) {
            items.remove(item);
            history.remove(item);
            saves.remove(item);
            matches.remove(item);
            switch (item.itemType) {
                case GameActivityItem.INCOMPLETE_LEVEL:
                case GameActivityItem.COMPLETED_LEVEL:
                    gameBase.removeActivityItem(item);
                    cacheGame();
                    saveGameState();
                    break;
            }

            notifyDataSetChanged();
        }

        public void removeItem(int position){
            GameActivityItem item = emptyItem;
            switch (mode){
                case ALL:
                    item = items.get(position);
                    break;
                case MATCHES:
                    item = matches.get(position);
                    break;
                case SAVES:
                    item = saves.get(position);
                    break;
                case HISTORY:
                    item = history.get(position);
            }
            removeItem(item);

            if (getCount() == 0) {
                addItem(emptyItem);
            }
                notifyDataSetChanged();
        }

        public void removeMatch(String id){
            for (GameActivityItem item : matches)
                if (item.uniqueName.equals(id) || item.rematchId.equals(id)){
                    removeItem(item);
                    return;
                }
        }

        public void removeSave(String id){
            for (GameActivityItem item : saves)
                if (item.uniqueName.equals(id)){
                    removeItem(item);
                    return;
                }
            notifyDataSetChanged();
        }

        @Override
        public GameActivityItem getItem(int position) {
            switch (mode) {
                case ALL:
                    return items.get(position);
                case MATCHES:
                    return matches.get(position);
                case SAVES:
                    return saves.get(position);
                case HISTORY:
                    return history.get(position);
                default:
                    return emptyItem;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount(){
            switch (mode){
                case ALL:
                    if (items.size() == 0) items.add(emptyItem);
                    return items.size();
                case MATCHES:
                    if (matches.size() == 0) matches.add(emptyItem);
                    return matches.size();
                case SAVES:
                    if (saves.size() == 0) saves.add(emptyItem);
                    return saves.size();
                case HISTORY:
                    if (history.size() == 0) history.add(emptyItem);
                    return history.size();
                default:
                    return 0;
            }
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            switch (getItem(position).itemType){
                case GameActivityItem.COMPLETED_LEVEL:
                case GameActivityItem.INCOMPLETE_LEVEL:
                    return getCompletedGameView(position, convertView);
                case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                    return getSavedGameView(position, convertView);
                case GameActivityItem.EMPTY_ITEM:
                    return getEmptyView(position, convertView);
                case GameActivityItem.MATCH_INVITATION:
                    return getMatchInvitionView(position, convertView);
                case GameActivityItem.MATCH_THEIR_TURN:
                    return getTheirTurnView(position, convertView);
                case GameActivityItem.MATCH_MY_TURN:
                    return getMyTurnView(position, convertView);
                case GameActivityItem.MATCH_COMPLETE:
                    return getCompletedMatchView(position, convertView);
                default:
                    return convertView;
            }
        }

        private View getEmptyView(int position, View convertView){
            convertView.findViewById(R.id.large_image_view).setVisibility(View.INVISIBLE);
            convertView.findViewById(R.id.small_image_view).setVisibility(View.INVISIBLE);
            convertView.findViewById(R.id.description).setVisibility(View.INVISIBLE);
            convertView.findViewById(R.id.time_stamp).setVisibility(View.INVISIBLE);
            convertView.findViewById(R.id.button).setVisibility(View.INVISIBLE);
            convertView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);

            TextView challengeView = (TextView) convertView.findViewById(R.id.title);
            challengeView.setText(getItem(position).description);
            return convertView;
        }
        private View getSavedGameView(final int position, View convertView) {
            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            TextView descView = (TextView) convertView.findViewById(R.id.description);
            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            TextView button = (TextView) convertView.findViewById(R.id.button);
            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);

            // Load image
            ImageView imageView = (ImageView) convertView.findViewById(R.id.large_image_view);
            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);
            if (mGoogleApiClient.isConnected()) {
                imageView.setVisibility(View.VISIBLE);
                smlImageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(imageView, getItem(position).imageUri);
                imageManager.loadImage(smlImageView, Games.Players.getCurrentPlayer(mGoogleApiClient).getIconImageUri());
            } else {
                imageView.setVisibility(View.GONE);
                smlImageView.setVisibility(View.GONE);
            }

            // Set challenge
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(R.string.saved_game_text);

            // Set description
            descView.setVisibility(View.VISIBLE);
            descView.setText(getItem(position).description);

            // Set date modified
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));

            button.setVisibility(View.VISIBLE);

            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setText(R.string.remove);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getItem(position).uniqueName != null) {
                        deleteSavedGame(getItem(position).uniqueName);
                        removeItem(position);
                    } else
                        removeItem(position);
                }
            });
            button.setText(R.string.continue_text);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PlaySavedLevel(getItem(position).uniqueName);
                }
            });
            return convertView;
        }
        private View getCompletedGameView(final int position, View convertView) {
            ImageView imageView = (ImageView)convertView.findViewById(R.id.large_image_view);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageResource(R.mipmap.ic_launcher);

            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);
            if (mGoogleApiClient.isConnected()){
                smlImageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(smlImageView, Games.Players.getCurrentPlayer(mGoogleApiClient).getIconImageUri());
            } else
                smlImageView.setVisibility(View.GONE);

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            TextView descView = (TextView) convertView.findViewById(R.id.description);
            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            TextView button = (TextView) convertView.findViewById(R.id.button);
            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);

            // Set challenge
            titleView.setVisibility(View.VISIBLE);
            String text = getString(ArithmosGameBase.getChallengeDisplayNameResId(getItem(position).challengeName)) + " " +
                    getString(ArithmosGameBase.getLevelDisplayNameResIds(getItem(position).challengeName)[getItem(position).challengeLevel]);
            titleView.setText(text);

            // Set description
            descView.setVisibility(View.VISIBLE);
            descView.setText(getItem(position).description);

            // Set date modified
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));


            button.setVisibility(View.VISIBLE);

            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setText(R.string.dismiss);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeItem(position);
                }
            });
            button.setText(R.string.replay_text);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAdThenPlayLevel(ArithmosGameBase.getLevelXmlIds(getItem(position).challengeName)[getItem(position).challengeLevel]);
                }
            });

            return convertView;
        }
        private View getMatchInvitionView(final int position, View convertView){
            ImageView imageView = (ImageView)convertView.findViewById(R.id.large_image_view);
            if (mGoogleApiClient.isConnected()) {
                imageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(imageView, getItem(position).imageUri);
            } else {
                imageView.setVisibility(View.GONE);
            }

            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);
            smlImageView.setVisibility(View.INVISIBLE);

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getItem(position).challengeName);

            TextView descView = (TextView) convertView.findViewById(R.id.description);
            descView.setVisibility(View.VISIBLE);
            descView.setText(R.string.match_invitation);

            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));

            TextView button = (TextView) convertView.findViewById(R.id.button);
            button.setVisibility(View.VISIBLE);
            button.setText(R.string.accept_invitation);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    acceptInvitation(getItem(position).uniqueName);
                }
            });

            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);
            deleteButton.setText(R.string.decline);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    declineInvite(getItem(position).uniqueName);
                    removeItem(position);
                }
            });


            return convertView;
        }
        private View getTheirTurnView(final int position, View convertView){
            ImageView imageView = (ImageView)convertView.findViewById(R.id.large_image_view);

            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);

            if (mGoogleApiClient.isConnected()) {
                imageView.setVisibility(View.VISIBLE);
                smlImageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(smlImageView, Games.Players.getCurrentPlayer(mGoogleApiClient).getIconImageUri());
                imageManager.loadImage(imageView, getItem(position).imageUri);
            } else {
                imageView.setVisibility(View.GONE);
                smlImageView.setVisibility(View.GONE);
            }

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getItem(position).challengeName);

            TextView descView = (TextView) convertView.findViewById(R.id.description);
            descView.setVisibility(View.VISIBLE);
            descView.setText(R.string.match_their_turn);

            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));

            TextView button = (TextView) convertView.findViewById(R.id.button);
            button.setVisibility(View.GONE);

            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);
            deleteButton.setText(R.string.leave);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMatch(getItem(position).uniqueName);
                    removeItem(position);
                }
            });

            return convertView;
        }
        private View getMyTurnView(final int position, View convertView){
            ImageView imageView = (ImageView)convertView.findViewById(R.id.large_image_view);

            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);

            if (mGoogleApiClient.isConnected()) {
                imageView.setVisibility(View.VISIBLE);
                smlImageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(smlImageView, getItem(position).imageUri);
                imageManager.loadImage(imageView, Games.Players.getCurrentPlayer(mGoogleApiClient).getIconImageUri());
            } else {
                imageView.setVisibility(View.GONE);
                smlImageView.setVisibility(View.GONE);
            }

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getItem(position).challengeName);

            TextView descView = (TextView) convertView.findViewById(R.id.description);
            descView.setVisibility(View.VISIBLE);
            descView.setText(R.string.match_my_turn);

            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));

            TextView button = (TextView) convertView.findViewById(R.id.button);
            button.setVisibility(View.VISIBLE);
            button.setText(R.string.take_my_turn);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takeMatchTurn(getItem(position).uniqueName);
                }
            });

            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setText(R.string.leave);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMatch(getItem(position).uniqueName);
                    removeItem(position);
                }
            });


            return convertView;
        }
        private View getCompletedMatchView(final int position, View convertView){
            ImageView smlImageView = (ImageView)convertView.findViewById(R.id.small_image_view);

            if (mGoogleApiClient.isConnected()) {
                smlImageView.setVisibility(View.VISIBLE);
                imageManager.loadImage(smlImageView, Games.Players.getCurrentPlayer(mGoogleApiClient).getIconImageUri());
            } else {
                smlImageView.setVisibility(View.GONE);
            }

            ImageView imageView = (ImageView)convertView.findViewById(R.id.large_image_view);
            imageView.setVisibility(View.VISIBLE);
            if (getItem(position).matchResult == ParticipantResult.MATCH_RESULT_WIN)
                imageView.setImageResource(R.drawable.ic_gold_trophy);
            else if (getItem(position).matchResult == ParticipantResult.MATCH_RESULT_LOSS)
                imageView.setImageResource(R.drawable.ic_silver_trophy);
            else
                imageView.setVisibility(View.GONE);

            TextView titleView = (TextView) convertView.findViewById(R.id.title);
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(getItem(position).challengeName);

            TextView descView = (TextView) convertView.findViewById(R.id.description);
            descView.setVisibility(View.VISIBLE);
            descView.setText(R.string.match_complete);

            TextView timeView = (TextView) convertView.findViewById(R.id.time_stamp);
            timeView.setVisibility(View.VISIBLE);
            timeView.setText(Utils.getTimeAgo(getResources(), getItem(position).getLastModifiedTimeStamp()));

            TextView button = (TextView) convertView.findViewById(R.id.button);
            if (getItem(position).canRematch && getItem(position).rematchId != null && !getItem(position).rematchId.equals("")) {
                button.setVisibility(View.VISIBLE);
                button.setText(R.string.rematch);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rematch(getItem(position).rematchId);
                    }
                });
            } else
                button.setVisibility(View.GONE);

            TextView deleteButton = (TextView) convertView.findViewById(R.id.delete_button);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setText(R.string.dismiss);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissMatch(getItem(position).uniqueName);
                    removeItem(position);
                }
            });

            return convertView;
        }
    }

    private void PlaySavedLevel(String uniqueName){
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.SAVED_GAME, uniqueName);
        startActivityForResult(intent, REQUEST_LEVEL_PLAYED);
    }

    public void ActivityButtonClick(View v){
        if (rootLayout.findViewById(R.id.activity_listview).getAlpha() == 0) {
            if (rootLayout.findViewById(R.id.challenge_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.challenge_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.special_store_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.special_store_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.achievement_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.achievement_listview), 200, 0).start();
            Animations.slideUp(rootLayout.findViewById(R.id.activity_listview), 200, 100, rootLayout.getHeight() / 3).start();
            rootLayout.findViewById(R.id.activity_listview).bringToFront();
        }
    }

    public void ActivityButtonBarClick(View v){
        TextView all = (TextView)rootLayout.findViewById(R.id.all_button);
        all.setText(R.string.all);
        TextView matches = (TextView)rootLayout.findViewById(R.id.matches_button);
        matches.setText(R.string.matches);
        TextView saves = (TextView)rootLayout.findViewById(R.id.saves_button);
        saves.setText(R.string.saves);
        TextView history = (TextView)rootLayout.findViewById(R.id.history_button);
        history.setText(R.string.history);
        switch (v.getId()){
            case R.id.all_button:
                all.setText(R.string.all_underline);
                activityListAdapter.setMode(ActivityListAdapter.ALL);
                break;
            case R.id.matches_button:
                matches.setText(R.string.matches_underline);
                activityListAdapter.setMode(ActivityListAdapter.MATCHES);
                break;
            case R.id.saves_button:
                saves.setText(R.string.saves_underline);
                activityListAdapter.setMode(ActivityListAdapter.SAVES);
                break;
            case R.id.history_button:
                history.setText(R.string.history_underline);
                activityListAdapter.setMode(ActivityListAdapter.HISTORY);
                break;
        }
    }

    private void refreshSavedGames(){
        Games.Snapshots.load(mGoogleApiClient, false).setResultCallback(new ResultCallback<Snapshots.LoadSnapshotsResult>() {
            @Override
            public void onResult(@NonNull Snapshots.LoadSnapshotsResult loadSnapshotsResult) {
                for (SnapshotMetadata snapshot : loadSnapshotsResult.getSnapshots()){
                    if (!snapshot.getUniqueName().startsWith(GAME_FILE_PREFIX)) {
                        GameActivityItem item = new GameActivityItem(GameActivityItem.GOOGLE_PLAY_SAVED_GAME);
                        item.uniqueName = snapshot.getUniqueName();
                        item.description = snapshot.getDescription();
                        item.imageUri = snapshot.getCoverImageUri();
                        item.timeStamp = snapshot.getLastModifiedTimestamp();
                        activityListAdapter.addItem(item);
                    }
                }
                activityListAdapter.sort();
                loadSnapshotsResult.release();
            }
        });
    }

    private void deleteSavedGame(final String uniqueName){
        if (uniqueName != null && mGoogleApiClient.isConnected()) {
            Games.Snapshots.open(mGoogleApiClient, uniqueName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    Games.Snapshots.delete(mGoogleApiClient, openSnapshotResult.getSnapshot().getMetadata());
                }
            });
            retryDeleteSavedLevel = false;
            gameToDeleteId = null;
        }
        else if (uniqueName != null){
            retryDeleteSavedLevel = true;
            gameToDeleteId = uniqueName;
            if (!mGoogleApiClient.isConnecting()){
                mGoogleApiClient.connect();
            }
        }
    }

    private void cacheGame(){
        if (gameCacheFile == null)
            gameCacheFile = new File(getCacheDir(), gameCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(gameCacheFile);
            byte[] data = gameBase.getByteData();
            outputStream.write(data, 0, data.length);
            outputStream.close();
            gameBase.setSaved(false);
            Log.d(LOG_TAG, "Game base cached");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadCachedGame(){
        gameCacheFile = new File(getCacheDir(), gameCacheFileName);
        if (gameCacheFile.exists()){
            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(gameCacheFile);
                byte[] data = new byte[inputStream.available()];
                if (inputStream.read(data) > 0) {
                    gameBase.loadByteData(data);
                    gameBase.setSaved(false);
                    // Update jewel count and challenges
                    TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                    int prevCount = jewelText.getTag() == null ? 0 : (int)jewelText.getTag();
                    Animations.CountTo(getResources(), R.string.number_after_x, jewelText, prevCount, gameBase.getJewelCount());
                    jewelText.setTag(gameBase.getJewelCount());
                    // Update challenge list if it has already been displayed
                    if (challengeListAdapter != null) challengeListAdapter.notifyDataSetChanged();
                    // Update recent activity
                    if (activityListAdapter != null) {
                        activityListAdapter.addItems(gameBase.getActivityItems());
                        activityListAdapter.sort();
                    }
                    Log.d(LOG_TAG, "Game base loaded from cache");
                    return true;
                }
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private void saveGameState(){
        if (gameBase.needsSaving() && mGoogleApiClient.isConnected()){
            retrySaveGameBase = false;
            SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
            String gameFileName = prefs.getString(GAME_FILE_NAME, null);
            if (gameFileName == null){
                gameFileName = GAME_FILE_PREFIX + new BigInteger(281, new Random()).toString(13);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(GAME_FILE_NAME, gameFileName);
                editor.apply();
                Log.d(LOG_TAG, "New game base Google file created");
            }
            Games.Snapshots.open(mGoogleApiClient, gameFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    String desc = "Arithmos Game Data";
                    writeSnapshot(openSnapshotResult.getSnapshot(), gameBase.getByteData(), desc);
                    gameBase.setSaved(true);
                    Log.d(LOG_TAG, "Game state saved");
                }
            });
        } else if (mAutoStartSignInFlow){
            retrySaveGameBase = true;
            mGoogleApiClient.connect();
        }
    }

    private void refreshGameState(){
        Log.d(LOG_TAG, "Refresh Game State Called");
        if (mGoogleApiClient.isConnected()) {
            Games.Snapshots.load(mGoogleApiClient, false).setResultCallback(new ResultCallback<Snapshots.LoadSnapshotsResult>() {
                @Override
                public void onResult(@NonNull Snapshots.LoadSnapshotsResult loadSnapshotsResult) {

                    String gameFileName = null;
                    for (SnapshotMetadata s : loadSnapshotsResult.getSnapshots()) {
                        if (s.getUniqueName().contains(GAME_FILE_PREFIX)) {
                            gameFileName = s.getUniqueName();
                            SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(GAME_FILE_NAME, gameFileName);
                            editor.apply();
                            if (s.getLastModifiedTimestamp() > gameBase.timeStamp())
                                loadGameState(gameFileName);
                            Log.d(LOG_TAG, "Game base downloaded");
                        }
                    }
                    loadSnapshotsResult.getSnapshots().release();
                    if (gameFileName == null)
                        saveGameState();
                }
            });
        }
    }

    private void loadGameState(String gameFileName){
        if (gameFileName != null && gameBaseNeedsRefresh && mGoogleApiClient.isConnected()) {
            Games.Snapshots.open(mGoogleApiClient, gameFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    try {
                        // Load data
                        gameBase.loadByteData(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());
                        // Update jewel count and challenges
                        TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                        int prevCount = jewelText.getTag() == null ? 0 : (int)jewelText.getTag();
                        Animations.CountTo(getResources(), R.string.number_after_x, jewelText, prevCount, gameBase.getJewelCount());
                        jewelText.setTag(gameBase.getJewelCount());
                        // Update challenge list if it has already been displayed
                        if (challengeListAdapter != null) challengeListAdapter.notifyDataSetChanged();
                        // Update recent activity
                        activityListAdapter.addItems(gameBase.getActivityItems());
                        activityListAdapter.sort();
                        gameBaseNeedsRefresh = false;
                        isGameBaseRefreshing = false;
                        consumePurchases();
                        cacheGame();
                        Log.d(LOG_TAG, "Game refreshed from Google");
                    } catch (IOException | NullPointerException | ClassCastException e) {
                        e.printStackTrace();
                        if (e instanceof NullPointerException) {
                            SharedPreferences.Editor editor = getSharedPreferences(GAME_PREFS, MODE_PRIVATE).edit();
                            editor.remove(GAME_FILE_NAME);
                            editor.apply();
                        }
                        if (e instanceof ClassCastException) {
                            gameBase.resetActivityList();
                            saveGameState();
                        }
                    }
                }
            });
        }
    }

    public void PlayerImageClick(View v){
        refreshAll();
    }


    // Game, challenges, and levels
    public static final String GAME_PREFS = "GAME_PREFS";
    public static final String GAME_FILE_NAME = "GAME_FILE_NAME";
    private static final String GAME_FILE_PREFIX = "ArithmosGame_";
    public static final String gameCacheFileName = "arithmos_game_cache";
    private ArithmosGameBase gameBase;
    private File gameCacheFile;
    private boolean retrySaveGameBase = false, retryGameReset = false;
    private boolean gameBaseNeedsRefresh = true, isGameBaseRefreshing = false;
    private ChallengeListAdapter challengeListAdapter;

    public void ChallengesButtonClick(View v){
        ExpandableListView listView = (ExpandableListView) rootLayout.findViewById(R.id.challenge_listview);
        if (listView.getVisibility() != View.VISIBLE || listView.getAlpha() == 0) {
            if (challengeListAdapter == null) {
                challengeListAdapter = new ChallengeListAdapter(context, R.layout.challenge_list_item, R.layout.level_list_item);
                listView.setAdapter(challengeListAdapter);
            }
            listView.setVisibility(View.VISIBLE);
            listView.bringToFront();

            if (rootLayout.findViewById(R.id.activity_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.activity_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.special_store_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.special_store_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.achievement_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.achievement_listview), 200, 0).start();
            Animations.slideUp(listView, 200, 100, rootLayout.getHeight() / 3).start();
        }

    }

    private void showAdThenPlayLevel(final int levelXmlId){
        if (showAds && mInterstitialAd.isLoaded()){
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                    playLevel(levelXmlId);
                }
            });
            mInterstitialAd.show();
        } else
            playLevel(levelXmlId);
    }

    private void playLevel(int levelXmlId){
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.LEVEL_XML_RES_ID, levelXmlId);
        startActivityForResult(intent, REQUEST_LEVEL_PLAYED);
    }

    private void showLeaderboard(String leaderboardId){
        startActivityForResult(Games.Leaderboards.getLeaderboardIntent(mGoogleApiClient,
                leaderboardId), 411);
    }

    private class ChallengeListAdapter extends BaseExpandableListAdapter {
        private LayoutInflater mInflater;
        private Context context;
        private int groupResourceId, childResourceId;

        public ChallengeListAdapter(Context context, int groupResourceId, int childResourceId){
            this.context = context;
            this.groupResourceId = groupResourceId;
            this.childResourceId = childResourceId;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public Integer getChild(int groupPosition, int childPosititon) {
            return ArithmosGameBase.getLevelDisplayNameResIds(ArithmosGameBase.challenges[groupPosition])[childPosititon];
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return ArithmosGameBase.getLevelDisplayNameResIds(ArithmosGameBase.challenges[groupPosition]).length;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(final int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(childResourceId, null);
            }

            ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
            if (!gameBase.isLevelUnlocked(getGroup(groupPosition), childPosition))
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_lock_image));
            else if (gameBase.getNumStars(getGroup(groupPosition), childPosition) == 3)
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_three_stars));
            else if (gameBase.getNumStars(getGroup(groupPosition), childPosition) == 2)
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_two_stars));
            else if (gameBase.getNumStars(getGroup(groupPosition), childPosition) == 1)
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_one_star));
            else
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_zero_stars));

            TextView textView = (TextView) convertView.findViewById(R.id.textView1);
            Button button = (Button) convertView.findViewById(R.id.button1);
            AppCompatImageButton button2 = (AppCompatImageButton) convertView.findViewById(R.id.button2);
            Button matchButton = (Button) convertView.findViewById(R.id.match_button);
            if (!gameBase.isLevelUnlocked(getGroup(groupPosition), childPosition)){
                textView.setText(R.string.level_locked);
                button.setVisibility(View.GONE);
                button2.setVisibility(View.GONE);
                matchButton.setVisibility(View.GONE);
            } else {
                int StringId = ArithmosGameBase.getLevelDisplayNameResIds(ArithmosGameBase.challenges[groupPosition])[childPosition];
                textView.setText(StringId);
                button.setVisibility(View.VISIBLE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAdThenPlayLevel(ArithmosGameBase.getLevelXmlIds(getGroup(groupPosition))[childPosition]);
                    }
                });
                if (mAutoStartSignInFlow) {
                    button2.setVisibility(View.VISIBLE);
                    button2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ArithmosLevel level = new ArithmosLevel(context, ArithmosGameBase.getLevelXmlIds(getGroup(groupPosition))[childPosition]);
                            showLeaderboard(level.getLeaderboardId());
                        }
                    });

                    matchButton.setVisibility(View.VISIBLE);
                    matchButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startMatchClick(ArithmosGameBase.getLevelXmlIds(getGroup(groupPosition))[childPosition]);
                        }
                    });
                } else {
                    button2.setVisibility(View.GONE);
                    matchButton.setVisibility(View.GONE);
                }
            }
            return convertView;
        }

        @Override
        public int getGroupCount() {
            return ArithmosGameBase.challenges.length;
        }

        @Override
        public String getGroup(int position) {
            return ArithmosGameBase.challenges[position];
        }

        @Override
        public long getGroupId(int position) {
            return position;
        }

        @Override
        public View getGroupView(int position, boolean isExpanded, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(groupResourceId, null);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.textView1);
            String challengeName = ArithmosGameBase.challenges[position];
            textView.setText(ArithmosGameBase.getChallengeDisplayNameResId(challengeName));
            return convertView;
        }
    }

    private void showResetGamePrompt(){
        final View layout = getLayoutInflater().inflate(R.layout.generic_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView msgView = (TextView)layout.findViewById(R.id.textView);
        msgView.setText(R.string.reset_game_message);

        layout.findViewById(R.id.checkBox).setVisibility(View.GONE);

        Button button1 = (Button)layout.findViewById(R.id.button1);
        button1.setText(R.string.yes);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetGame();
                AnimatorSet set = Animations.slideOutDown(layout, 150, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.no);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 150, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 100, 0, rootLayout.getHeight() / 3).start();
    }

    private void resetGame(){
        if (mGoogleApiClient.isConnected()) {
            retryGameReset = false;
            // Show loading popup
            final View loadingPopup = Utils.progressPopup(context, R.string.reset_game_progress_message);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 100, 0, rootLayout.getHeight() / 3).start();

            Games.Snapshots.load(mGoogleApiClient, false).setResultCallback(new ResultCallback<Snapshots.LoadSnapshotsResult>() {
                @Override
                public void onResult(@NonNull Snapshots.LoadSnapshotsResult loadSnapshotsResult) {
                    for (SnapshotMetadata data : loadSnapshotsResult.getSnapshots()){
                        Games.Snapshots.delete(mGoogleApiClient, data);
                    }
                    //gameBase.resetGame();
                    gameBase = new ArithmosGameBase();
                    activityListAdapter.clearList();
                    cacheGame();
                    SharedPreferences.Editor editor = getSharedPreferences(GAME_PREFS, MODE_PRIVATE).edit();
                    editor.remove(GAME_FILE_NAME);
                    editor.commit();
                    saveGameState();
                    if (challengeListAdapter != null) challengeListAdapter.notifyDataSetChanged();
                    // Clear loading popup
                    rootLayout.removeView(loadingPopup);
                    // Show confirmation popup
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), R.string.reset_game_success, Snackbar.LENGTH_SHORT);
                    snackbar.show();
                }
            });
        } else {
            retryGameReset = true;
            mGoogleApiClient.connect();
        }
    }


    // Special Store
    public void specialStoreButtonClick(View v){
        ListView listView = (ListView)rootLayout.findViewById(R.id.special_store_listview);
        if (listView.getVisibility() != View.VISIBLE || listView.getAlpha() == 0) {
            listView.setAdapter(new SpecialListAdapter());
            listView.setVisibility(View.VISIBLE);
            listView.bringToFront();

            if (rootLayout.findViewById(R.id.activity_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.activity_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.challenge_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.challenge_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.achievement_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.achievement_listview), 200, 0).start();
            Animations.slideUp(listView, 200, 100, rootLayout.getHeight() / 3).start();
        }
    }

    private class SpecialListAdapter extends BaseAdapter {
        private int resource_id = R.layout.special_store_item_layout;
        private LayoutInflater mInflater;
        private String[] names = getResources().getStringArray(R.array.special_item_names);
        private int[] costs = getResources().getIntArray(R.array.special_costs);
        private int[] iconResIds = {R.drawable.ic_right_arrow, R.drawable.ic_bomb,
                R.drawable.ic_pencil, R.drawable.ic_calculator, R.drawable.ic_zero};
        private int[] descResIds = {R.string.skip_description, R.string.bomb_description,
                R.string.pencil_description, R.string.calc_description, R.string.zero_description};

        public SpecialListAdapter(){

        }

        @Override
        public String getItem(int position) {
            return names[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount(){
            return names.length;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            ImageView iconView = (ImageView)convertView.findViewById(R.id.icon);
            iconView.setImageResource(iconResIds[position]);

            final TextView countView = (TextView)convertView.findViewById(R.id.item_count);
            countView.setText(getString(R.string.number_after_x, gameBase.getSpecialCount(names[position])));

            TextView descView = (TextView)convertView.findViewById(R.id.description_textview);
            descView.setText(descResIds[position]);

            TextView costView = (TextView)convertView.findViewById(R.id.cost_textview);
            costView.setText(String.valueOf(costs[position]));

            Button addButton = (Button)convertView.findViewById(R.id.get_button);
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (gameBase.getJewelCount() >= costs[position]) {
                        int prevCount = gameBase.getJewelCount();
                        gameBase.addSpecial(names[position], 1);
                        gameBase.spendJewels(costs[position]);
                        countView.setText(getString(R.string.number_after_x, gameBase.getSpecialCount(names[position])));
                        TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                        Animations.CountTo(getResources(), R.string.number_after_x, jewelText, prevCount, gameBase.getJewelCount());
                        cacheGame();
                        saveGameState();
                    }
                }
            });
            return convertView;
        }
    }


    // Purchasing API and purchase processing
    private static final int PURCHASE_REQUEST = 7001;
    private static final String SKU_1000_JEWELS = "jewels_1000", SKU_REMOVE_ADS = "remove_ads";
    private static final String ORDER_HISTORY_FILENAME = "order_history";
    private IabHelper iabHelper;
    private IabListAdapter iabListAdapter;

    public void IabButtonClick(View v){
        showIabStorePopup();
    }

    private void showIabStorePopup(){

        // Prepare popup window
        final View layout = getLayoutInflater().inflate(R.layout.popup_listview, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView titleView = (TextView)layout.findViewById(R.id.title_textview);
        titleView.setText(R.string.iab_popup_title);

        Button button = (Button)layout.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        // Setup in-App purchasing
        final ProgressBar progressBar = (ProgressBar)layout.findViewById(R.id.progress_bar);
        iabHelper = new IabHelper(this, getString(R.string.base64EncodedPublicKey));
        iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d(LOG_TAG, "Problem setting up In-app Billing: " + result);
                    return;
                }
                progressBar.setVisibility(View.GONE);
                ListView iabListView = (ListView)layout.findViewById(R.id.listview);
                iabListAdapter = new IabListAdapter();
                iabListView.setAdapter(iabListAdapter);
                final ArrayList<String> skuList = new ArrayList<>();
                skuList.add(SKU_1000_JEWELS);
                skuList.add(SKU_REMOVE_ADS);
                try {
                    iabHelper.queryInventoryAsync(true, skuList, null, new IabHelper.QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            if (result.isSuccess()) {
                                for (String id : skuList){
                                    if (inv.hasDetails(id)) {
                                        iabListAdapter.addItem(id, inv.getSkuDetails(id), inv.hasPurchase(id));
                                    }
                                }
                            }
                            Log.d(LOG_TAG, result.getMessage());
                            progressBar.setVisibility(View.GONE);
                        }
                    });
                } catch (IabHelper.IabAsyncInProgressException e){e.printStackTrace();}
            }
        });




        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }

    private void startPurchaseFlow(String productId){
        showLoadingPopup(R.string.loading, 0);
        Log.d(LOG_TAG, "Purchasing " + productId);
        try {
            iabHelper.launchPurchaseFlow(this, productId, PURCHASE_REQUEST, new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    if (!result.isSuccess()) {
                        Log.d(LOG_TAG, "Error processing purchase " + result.getMessage());
                        hideLoadingPopup();
                        return;
                    } else {
                        Log.d(LOG_TAG, "Purchase Successful");
                        recordOrderAsync(info);
                        processPurchase(info);
                        hideLoadingPopup();
                    }

                }
            });
        } catch (IabHelper.IabAsyncInProgressException e) {e.printStackTrace();}
    }

    private void recordOrderAsync(final Purchase purchase){
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Load order history from file
                ArrayList<OrderData> orders = null;
                File orderHistoryFile = new File(getFilesDir(), ORDER_HISTORY_FILENAME);
                if (orderHistoryFile.exists()) {
                    FileInputStream fis;
                    ObjectInputStream ois;
                    try {
                        fis = new FileInputStream(orderHistoryFile);
                        ois = new ObjectInputStream(fis);
                        orders = (ArrayList<OrderData>)ois.readObject();
                        ois.close();
                        fis.close();
                        Log.d(LOG_TAG, "Order history opened");
                    } catch (Exception e) {
                        e.printStackTrace();
                        orderHistoryFile.delete();
                    }
                }
                if (orders == null)
                    orders = new ArrayList<>();

                // Write order to history and save
                orders.add(new OrderData(purchase.getOrderId(), purchase.getSku(), purchase.getPurchaseTime()));

                FileOutputStream fos;
                ObjectOutputStream oos;
                try {
                    fos = new FileOutputStream(orderHistoryFile);
                    oos = new ObjectOutputStream(fos);
                    oos.writeObject(orders);
                    oos.close();
                    fos.close();
                    Log.d(LOG_TAG, "Order history saved");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private class OrderData implements Serializable {
        public OrderData(String orderId, String sku, long timeStamp){
            this.orderId = orderId;
            this.sku = sku;
            this.timeStamp = timeStamp;
        }
        transient private int serializationVersion = 0;
        transient String orderId, sku;
        transient long timeStamp;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeInt(serializationVersion);
            out.writeObject(orderId);
            out.writeObject(sku);
            out.writeLong(timeStamp);
        }
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
            int version = in.readInt();
            orderId = (String)in.readObject();
            sku = (String)in.readObject();
            timeStamp = in.readLong();
        }
    }

    private void showOrderHistoryPopup(){
        // Prepare popup window
        final View layout = getLayoutInflater().inflate(R.layout.popup_listview, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        layout.findViewById(R.id.progress_bar).setVisibility(View.GONE);


        TextView titleView = (TextView)layout.findViewById(R.id.title_textview);
        titleView.setText(R.string.order_history_popup_title);

        Button button = (Button)layout.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        // Load and display order history
        // Load order history from file
        ArrayList<OrderData> orders = null;
        File orderHistoryFile = new File(getFilesDir(), ORDER_HISTORY_FILENAME);
        if (orderHistoryFile.exists()) {
            FileInputStream fis;
            ObjectInputStream ois;
            try {
                fis = new FileInputStream(orderHistoryFile);
                ois = new ObjectInputStream(fis);
                orders = (ArrayList<OrderData>)ois.readObject();
                ois.close();
                fis.close();
                Log.d(LOG_TAG, "Order history opened");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (orders == null)
            orders = new ArrayList<>();

        ListView listView = (ListView)layout.findViewById(R.id.listview);
        OrderListAdapter adapter = new OrderListAdapter(orders);
        listView.setAdapter(adapter);


        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }

    private class OrderListAdapter extends BaseAdapter {
        private int resource_id = R.layout.order_history_list_item;
        private LayoutInflater mInflater;
        private ArrayList<OrderData> items;

        public OrderListAdapter(ArrayList<OrderData> purchases) {
            items = purchases;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public OrderData getItem(int position) {
            return items.get(position);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            TextView orderView = (TextView)convertView.findViewById(R.id.order_number_textview);
            orderView.setText(getString(R.string.order_number, getItem(position).orderId));

            TextView skuView = (TextView)convertView.findViewById(R.id.sku_textview);
            skuView.setText(getString(R.string.product_id, getItem(position).sku));

            TextView timeView = (TextView)convertView.findViewById(R.id.time_stamp_textview);
            timeView.setText(Utils.getDate(getItem(position).timeStamp, "kk:mm:ss MM/dd/yy"));

            return convertView;
        }
    }

    private void processPurchase(Purchase purchase){
        if (purchase.getSku().equals(SKU_REMOVE_ADS))
        {
            removeAds();
        } else {
            try {
                iabHelper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {
                    @Override
                    public void onConsumeFinished(Purchase purchase, IabResult result) {
                        if (result.isSuccess()) {
                            processConsumedPurchase(purchase.getSku());
                            cacheGame();
                            saveGameState();
                        }
                    }
                });
            } catch (IabHelper.IabAsyncInProgressException e) {
                e.printStackTrace();
            }
        }
    }

    private void consumePurchases(){
        iabHelper = new IabHelper(this, getString(R.string.base64EncodedPublicKey));
        final ArrayList<String> skuList = new ArrayList<>();
        skuList.add(SKU_1000_JEWELS);
        skuList.add(SKU_REMOVE_ADS);
        iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d(LOG_TAG, "Problem setting up In-app Billing: " + result);
                    return;
                }
                try {
                    iabHelper.queryInventoryAsync(true, skuList, null, new IabHelper.QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            if (result.isSuccess()) {
                                ArrayList<Purchase> purchases = new ArrayList<>(skuList.size());
                                for (String sku : skuList){
                                    if (inv.hasPurchase(sku)) purchases.add(inv.getPurchase(sku));
                                }
                                if (purchases.size() > 0)
                                    for (Purchase p : purchases){
                                        processPurchase(p);
                                    }
                            }
                            iabHelper.disposeWhenFinished();
                            Log.d(LOG_TAG, result.getMessage());
                        }
                    });
                } catch (IabHelper.IabAsyncInProgressException e){e.printStackTrace();}
            }
        });
    }

    private void processConsumedPurchase(String sku){
        switch (sku){
            case SKU_1000_JEWELS:
                int prevCount = gameBase.getJewelCount();
                gameBase.recordJewels(1000);
                TextView jewelView = (TextView)rootLayout.findViewById(R.id.jewel_count);
                Animations.CountTo(getResources(), R.string.number_after_x, jewelView, prevCount, gameBase.getJewelCount());
                Log.d(LOG_TAG, "Consumed 1000 jewel purchase");
                Toast.makeText(context, getString(R.string.jewel_purchase, 1000), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private class IabListAdapter extends BaseAdapter {
        private int resource_id = R.layout.iab_store_item;
        private LayoutInflater mInflater;
        private ArrayList<SkuDetails> items;
        private ArrayList<String> productIds;
        private ArrayList<Boolean> purchasedList;

        public IabListAdapter(){
            items = new ArrayList<>();
            productIds = new ArrayList<>();
            purchasedList = new ArrayList<>();
        }

        public void setPurchased(String productId, boolean purchased){
            int index = productIds.indexOf(productId);
            if (index >= 0)
                purchasedList.set(index, purchased);
            notifyDataSetChanged();
        }

        public void addItem(String id, SkuDetails item, boolean purchase){
            productIds.add(id);
            items.add(item);
            purchasedList.add(purchase);
            notifyDataSetChanged();
        }

        public ArrayList<String> getOwnedSkus(){
            ArrayList<String> skus = new ArrayList<>(productIds.size());
            for (int i = 0; i < purchasedList.size(); i++){
                if (purchasedList.get(i))
                    skus.add(productIds.get(i));
            }
            return skus;
        }

        @Override
        public int getCount(){
            return items.size();
        }

        @Override
        public long getItemId(int position){
            return position;
        }

        @Override
        public SkuDetails getItem(int position){
            return items.get(position);
        }

        public SkuDetails getItem(String productId){
            int index = productIds.indexOf(productId);
            return items.get(index);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent){
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            ImageView iconView = (ImageView)convertView.findViewById(R.id.icon);
            iconView.setVisibility(View.VISIBLE);
            if (productIds.get(position).contains("jewel")) {
                iconView.setImageResource(R.drawable.ic_red_jewel);
            }
            else
                iconView.setVisibility(View.GONE);

            TextView titleView = (TextView)convertView.findViewById(R.id.title_textview);
            titleView.setText(getItem(position).getTitle());

            TextView descView = (TextView)convertView.findViewById(R.id.description_textview);
            descView.setText(getItem(position).getDescription());

            TextView priceView = (TextView)convertView.findViewById(R.id.price_textview);
            priceView.setText(getItem(position).getPrice());

            Button button = (Button)convertView.findViewById(R.id.get_button);
            if (purchasedList.get(position)) {
                button.setText(R.string.owned);
                button.setOnClickListener(null);
            }
            else {
                button.setText(R.string.buy);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startPurchaseFlow(productIds.get(position));
                    }
                });
            }
            return convertView;
        }
    }



    // Achievements
    private AchievementListAdapter achievementListAdapter;

    public void achievementButtonClick(View v){
        ListView listView = (ListView)rootLayout.findViewById(R.id.achievement_listview);
        if (listView.getVisibility() != View.VISIBLE || listView.getAlpha() == 0) {
            if (achievementListAdapter == null)
                achievementListAdapter = new AchievementListAdapter();
            listView.setAdapter(achievementListAdapter);
            listView.setVisibility(View.VISIBLE);
            listView.bringToFront();

            if (rootLayout.findViewById(R.id.activity_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.activity_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.challenge_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.challenge_listview), 200, 0).start();
            if (rootLayout.findViewById(R.id.special_store_listview).getAlpha() > 0)
                Animations.fadeOut(rootLayout.findViewById(R.id.special_store_listview), 200, 0).start();
            Animations.slideUp(listView, 200, 100, rootLayout.getHeight() / 3).start();
        }
    }

    private void refreshAchievementList(){
        Games.Achievements.load(mGoogleApiClient, false).setResultCallback(new ResultCallback<Achievements.LoadAchievementsResult>() {
            @Override
            public void onResult(@NonNull Achievements.LoadAchievementsResult loadAchievementsResult) {
                achievementListAdapter = new AchievementListAdapter();
                if (loadAchievementsResult.getStatus().isSuccess()) {
                    for (Achievement achievement : loadAchievementsResult.getAchievements()){
                        if (achievement.getState() != Achievement.STATE_HIDDEN) {
                            AchievementItem item = new AchievementItem(achievement.getType());
                            item.id = achievement.getAchievementId();
                            item.name = achievement.getName();
                            item.description = achievement.getDescription();
                            item.state = achievement.getState();
                            item.xpValue = achievement.getXpValue();
                            if (achievement.getState() == Achievement.STATE_REVEALED)
                                item.imageUri = achievement.getRevealedImageUri();
                            else
                                item.imageUri = achievement.getUnlockedImageUri();
                            if (achievement.getType() == Achievement.TYPE_INCREMENTAL) {
                                item.currentSteps = achievement.getCurrentSteps();
                                item.totalSteps = achievement.getTotalSteps();
                            }
                            achievementListAdapter.addItem(item);
                        }
                    }
                }
                loadAchievementsResult.release();
            }
        });
    }

    private class AchievementListAdapter extends BaseAdapter {
        private int resource_id = R.layout.achievement_list_item;
        private LayoutInflater mInflater;
        private ArrayList<AchievementItem> items;
        private ImageManager imageManager = ImageManager.create(context);

        public AchievementListAdapter(){
            items = new ArrayList<>();
        }

        public void addItem(AchievementItem item){
            items.add(item);
            notifyDataSetChanged();
        }

        @Override
        public int getCount(){
            return items.size();
        }

        @Override
        public long getItemId(int position){
            return position;
        }

        @Override
        public AchievementItem getItem(int position){
            return items.get(position);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent){
            if (convertView == null) {
                mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = mInflater.inflate(resource_id, null);
            }

            TextView titleView = (TextView)convertView.findViewById(R.id.title);
            titleView.setText(getItem(position).name);

            TextView descView = (TextView)convertView.findViewById(R.id.description);
            descView.setText(getItem(position).description);

            TextView xpView = (TextView)convertView.findViewById(R.id.xp_textview);
            xpView.setText(getString(R.string.xp, getItem(position).xpValue));

            ImageView imageView = (ImageView)convertView.findViewById(R.id.image_view);
            if (mGoogleApiClient.isConnected())
                imageManager.loadImage(imageView, getItem(position).imageUri);

            ProgressBar progressBar = (ProgressBar)convertView.findViewById(R.id.progress_bar);
            TextView progressView = (TextView)convertView.findViewById(R.id.progress_textview);
            if (getItem(position).state != Achievement.STATE_UNLOCKED && getItem(position).type == Achievement.TYPE_INCREMENTAL){
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setMax(getItem(position).totalSteps);
                progressBar.setProgress(getItem(position).currentSteps);
                progressView.setVisibility(View.VISIBLE);
                progressView.setText(getString(R.string.percent, Math.round(getItem(position).currentSteps * 100 / getItem(position).totalSteps)));
            } else {
                progressBar.setVisibility(View.GONE);
                progressView.setVisibility(View.GONE);
            }

            if (getItem(position).state == Achievement.STATE_UNLOCKED) {
                titleView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
                descView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
                xpView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
            } else {
                titleView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_inactive_dark, null));
                descView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_inactive_dark, null));
                xpView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_inactive_dark, null));
            }

            return convertView;
        }
    }

    private class AchievementItem {
        int type, state;
        int currentSteps, totalSteps;
        long xpValue;
        String id, name, description;
        Uri imageUri;

        public AchievementItem(int type){
            this.type = type;
        }
    }



    // Turn based matches
    private static int RC_SELECT_PLAYERS = 9002;
    private int matchLevelXmlId;
    private String matchId;
    private boolean retryTakeMatchTurn = false, retryAcceptInvitation = false;
    private void startMatchClick(int levelXmlId){
        matchLevelXmlId = levelXmlId;
        Intent intent =
                Games.TurnBasedMultiplayer.getSelectOpponentsIntent(mGoogleApiClient, 1, 1, true);
        startActivityForResult(intent, RC_SELECT_PLAYERS);
        /*Intent intent = new Intent(this, MatchGameActivity.class);
        intent.putExtra(MatchGameActivity.LEVEL_XML_RES_ID, levelXmlId);
        SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
        String gameFileName = prefs.getString(GAME_FILE_NAME, null);
        if (gameFileName != null)
            intent.putExtra(GameActivity.GAME_BASE_FILE_NAME, gameFileName);
        intent.putExtra(MatchGameActivity.CREATE_MATCH, true);
        startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);*/
    }

    private void refreshMatchList(){
        if (mGoogleApiClient.isConnected()) {
            int[] statusTypes = new int[]{TurnBasedMatch.MATCH_TURN_STATUS_INVITED, TurnBasedMatch.MATCH_TURN_STATUS_MY_TURN,
                    TurnBasedMatch.MATCH_TURN_STATUS_THEIR_TURN, TurnBasedMatch.MATCH_TURN_STATUS_COMPLETE};
            Games.TurnBasedMultiplayer.loadMatchesByStatus(mGoogleApiClient, statusTypes).setResultCallback(new ResultCallback<TurnBasedMultiplayer.LoadMatchesResult>() {
                @Override
                public void onResult(@NonNull TurnBasedMultiplayer.LoadMatchesResult loadMatchesResult) {
                    for (Invitation i : loadMatchesResult.getMatches().getInvitations()) {
                        GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_INVITATION);
                        item.challengeName = i.getInviter().getDisplayName();
                        item.timeStamp = i.getCreationTimestamp();
                        item.uniqueName = i.getInvitationId();
                        item.imageUri = i.getInviter().getIconImageUri();
                        activityListAdapter.addItem(item);
                    }

                    for (TurnBasedMatch match : loadMatchesResult.getMatches().getMyTurnMatches()) {
                        GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_MY_TURN);
                        Participant lastParticipant = match.getParticipant(match.getLastUpdaterId());
                        item.challengeName = lastParticipant.getDisplayName();
                        item.timeStamp = match.getLastUpdatedTimestamp();
                        item.uniqueName = match.getMatchId();
                        item.imageUri = lastParticipant.getIconImageUri();
                        activityListAdapter.addItem(item);
                    }

                    for (TurnBasedMatch match : loadMatchesResult.getMatches().getTheirTurnMatches()) {
                        GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_THEIR_TURN);
                        Participant nextParticipant = match.getParticipant(match.getPendingParticipantId());
                        item.challengeName = nextParticipant.getDisplayName();
                        item.timeStamp = match.getLastUpdatedTimestamp();
                        item.uniqueName = match.getMatchId();
                        item.imageUri = nextParticipant.getIconImageUri();
                        activityListAdapter.addItem(item);
                    }


                    for (TurnBasedMatch match : loadMatchesResult.getMatches().getCompletedMatches()) {
                        GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_COMPLETE);
                        Participant origParticipant = match.getParticipant(match.getCreatorId());
                        item.challengeName = origParticipant.getDisplayName();
                        item.timeStamp = match.getLastUpdatedTimestamp();
                        item.uniqueName = match.getMatchId();
                        item.imageUri = origParticipant.getIconImageUri();
                        if (match.canRematch()) {
                            item.canRematch = match.canRematch();
                            item.rematchId = match.getRematchId();
                        }
                        ParticipantResult result = match.getParticipant(match.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient))).getResult();
                        if (result != null)
                            item.matchResult = result.getResult();
                        activityListAdapter.addItem(item);
                    }

                    activityListAdapter.sort();
                    loadMatchesResult.release();
                    rootLayout.findViewById(R.id.progress_bar).setVisibility(View.GONE);
                    Log.d(LOG_TAG, "Matches loaded");
                }
            });
        }
    }

    private void acceptInvitation(final String matchId){
        if (mGoogleApiClient.isConnected()) {
            retryAcceptInvitation = false;
            final View popup = Utils.progressPopup(context, getString(R.string.accept_invitation_progress));
            Animations.slideUp(popup, 150, 0, rootLayout.getHeight() / 3).start();
            Games.TurnBasedMultiplayer.acceptInvitation(mGoogleApiClient, matchId).setResultCallback(new ResultCallback<TurnBasedMultiplayer.InitiateMatchResult>() {
                @Override
                public void onResult(@NonNull TurnBasedMultiplayer.InitiateMatchResult initiateMatchResult) {
                    takeMatchTurn(matchId);
                }
            });
        } else if (mAutoStartSignInFlow){
            retryAcceptInvitation = true;
            this.matchId = matchId;
            mGoogleApiClient.connect();
        } else {
            Toast.makeText(context, R.string.not_signed_in, Toast.LENGTH_SHORT).show();
        }
    }

    private void takeMatchTurn(String matchId){
        if (mGoogleApiClient.isConnected()) {
            showLoadingPopup(R.string.loading, 0);
            Games.TurnBasedMultiplayer.loadMatch(mGoogleApiClient, matchId).setResultCallback(new MatchLoadedCallback());
        } else if (mAutoStartSignInFlow){
            showLoadingPopup(R.string.loading, 0);
            this.matchId = matchId;
            retryTakeMatchTurn = true;
            mGoogleApiClient.connect();
        } else {
            Toast.makeText(context, R.string.not_signed_in, Toast.LENGTH_SHORT).show();
        }
    }

    private void declineInvite(String id){
        if (mGoogleApiClient.isConnected()) {
            Games.TurnBasedMultiplayer.declineInvitation(mGoogleApiClient, id);
        }
    }

    private void dismissMatch(String id){
        if (mGoogleApiClient.isConnected()) {
            Games.TurnBasedMultiplayer.dismissMatch(mGoogleApiClient, id);
        }
    }

    private void rematch(String id){
        if (mGoogleApiClient.isConnected())
        Games.TurnBasedMultiplayer.rematch(mGoogleApiClient, id).setResultCallback(new MatchInitiatedCallback());
    }

    private void loadCachedMatch(){
        File matchCacheFile = new File(getCacheDir(), MatchGameActivity.matchCacheFileName);
        if (matchCacheFile.exists()) {
            FileInputStream fis;
            ObjectInputStream ois;
            try {
                fis = new FileInputStream(matchCacheFile);
                ois = new ObjectInputStream(fis);
                MatchGameActivity.MatchCacheData data = (MatchGameActivity.MatchCacheData) ois.readObject();
                takeMatchTurn(data.matchId);

                ois.close();
                fis.close();
                Log.d(LOG_TAG, "Match loaded from cache");
            } catch (Exception e) {
                e.printStackTrace();
                matchCacheFile.delete();
            }
        }
    }

    public class MatchInitiatedCallback implements ResultCallback<TurnBasedMultiplayer.InitiateMatchResult> {

        @Override
        public void onResult(TurnBasedMultiplayer.InitiateMatchResult result) {
            // Check if the status code is not success.
            Status status = result.getStatus();
            if (!status.isSuccess()) {
                //showError(status.getStatusCode());
                Log.d(LOG_TAG, status.getStatusMessage());
                return;
            }

            // Initialize match
            TurnBasedMatch match = result.getMatch();
            ArithmosLevel level = new ArithmosLevel(context, matchLevelXmlId);
            ArrayList<String> playerIds = match.getParticipantIds();
            ArithmosGame game = new ArithmosGame(level, playerIds.get(0), playerIds.get(1));
            game.setCurrentPlayer(match.getCreatorId());
            Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, match.getMatchId(), game.getSaveGameData(), match.getCreatorId()).setResultCallback(new ResultCallback<TurnBasedMultiplayer.UpdateMatchResult>() {
                @Override
                public void onResult(@NonNull TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
                    hideLoadingPopup();
                    Intent intent = new Intent(context, MatchGameActivity.class);
                    intent.putExtra(MatchGameActivity.LEVEL_XML_RES_ID, matchLevelXmlId);
                    intent.putExtra(MatchGameActivity.CREATE_MATCH, true);
                    intent.putExtra(MatchGameActivity.MATCH, updateMatchResult.getMatch());
                    startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);
                }
            });

            // Otherwise, this is the first player. Initialize the game state.

            // Let the player take the first turn
        }
    }
    public class MatchLoadedCallback implements ResultCallback<TurnBasedMultiplayer.LoadMatchResult>{
        @Override
        public void onResult(@NonNull TurnBasedMultiplayer.LoadMatchResult loadMatchResult) {
            retryTakeMatchTurn = false;
            Status status = loadMatchResult.getStatus();
            if (!status.isSuccess()) {
                Log.d(LOG_TAG, status.getStatusMessage());

                return;
            }
            final TurnBasedMatch match = loadMatchResult.getMatch();
            hideLoadingPopup();
                if (showAds && mInterstitialAd.isLoaded()){
                    mInterstitialAd.setAdListener(new AdListener() {
                        @Override
                        public void onAdClosed() {
                            super.onAdClosed();
                            Intent intent = new Intent(context, MatchGameActivity.class);
                            intent.putExtra(MatchGameActivity.MATCH, match);
                            startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);
                        }
                    });
                    mInterstitialAd.show();
                } else {
                    Intent intent = new Intent(context, MatchGameActivity.class);
                    intent.putExtra(MatchGameActivity.MATCH, match);
                    startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);
                }
        }
    }

    @Override
    public void onInvitationRemoved(String id){
        activityListAdapter.removeMatch(id);
    }

    @Override
    public void onInvitationReceived(final Invitation invitation){
        GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_INVITATION);
        item.uniqueName = invitation.getInvitationId();
        item.challengeName = invitation.getInviter().getDisplayName();
        item.imageUri = invitation.getInviter().getIconImageUri();
        item.timeStamp = invitation.getCreationTimestamp();
        activityListAdapter.addItem(item);

        String message = getString(R.string.match_invitation_alert, invitation.getInviter().getDisplayName());
        Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), message, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.accept_invitation, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptInvitation(invitation.getInvitationId());
            }
        });
        snackbar.show();
    }

    @Override
    public void onTurnBasedMatchReceived(final TurnBasedMatch match){
        // Show popup
        String message;

        if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_COMPLETE){
            // Add match to list
            GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_COMPLETE);Participant origParticipant = match.getParticipant(match.getCreatorId());
            item.challengeName = origParticipant.getDisplayName();
            item.timeStamp = match.getLastUpdatedTimestamp();
            item.uniqueName = match.getMatchId();
            item.imageUri = origParticipant.getIconImageUri();
            if (match.canRematch()){
                item.canRematch = match.canRematch();
                item.rematchId = match.getRematchId();
            }
            ParticipantResult result = match.getParticipant(match.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient))).getResult();
            if (result != null)
                item.matchResult = result.getResult();
            activityListAdapter.addItem(item);
            // Show notification
            message = getString(R.string.match_completed_alert, match.getParticipant(match.getLastUpdaterId()).getDisplayName());
            Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), message, Snackbar.LENGTH_LONG);
            snackbar.setAction(R.string.take_my_turn, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takeMatchTurn(match.getMatchId());
                }
            });
            snackbar.show();
        } else if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_CANCELED){
            // Remove match from list
            activityListAdapter.removeMatch(match.getMatchId());
            // Show notification
            message = getString(R.string.match_canceled_alert, match.getParticipant(match.getLastUpdaterId()).getDisplayName());
            Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), message, Snackbar.LENGTH_LONG);
            snackbar.show();
        } else if (match.getTurnStatus() == TurnBasedMatch.MATCH_TURN_STATUS_MY_TURN) {
            // Add match to list
            GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_MY_TURN);
            Participant lastParticipant = match.getParticipant(match.getLastUpdaterId());
            item.challengeName = lastParticipant.getDisplayName();
            item.timeStamp = match.getLastUpdatedTimestamp();
            item.uniqueName = match.getMatchId();
            item.imageUri = lastParticipant.getIconImageUri();
            activityListAdapter.addItem(item);
            // Show notification
            message = getString(R.string.match_turn_taken_alert, match.getParticipant(match.getLastUpdaterId()).getDisplayName());
            Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), message, Snackbar.LENGTH_LONG);
            snackbar.setAction(R.string.take_my_turn, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takeMatchTurn(match.getMatchId());
                }
            });
            snackbar.show();
        }
        Log.d(LOG_TAG, "Match notification received");
    }

    @Override
    public void onTurnBasedMatchRemoved(String matchId){
        activityListAdapter.removeMatch(matchId);
    }


    // Google Play Games Services
    private GoogleApiClient mGoogleApiClient;

    private static int RC_SIGN_IN = 9001;
    private static String SHOW_USE_GOOGLE_PLAY_PROMPT = "SHOW_USE_GOOGLE_PLAY_PROMPT";

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;
    public static final String AUTO_SIGN_IN = "auto_sign_in";

    @Override
    public void onConnected(Bundle connectionHint) {
        // show sign-out button, hide the sign-in button
        rootLayout.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
        showGoogleUi();
        updatePlayerInfo();

        Games.TurnBasedMultiplayer.registerMatchUpdateListener(mGoogleApiClient, this);
        Games.Invitations.registerInvitationListener(mGoogleApiClient, this);

        // Retry saving game state
        if (retrySaveGameBase)
            saveGameState();
        else
            refreshGameState();

        // Retry to delete snapshot
        if (retryDeleteSavedLevel)
            deleteSavedGame(gameToDeleteId);

        // Retry game reset
        if (retryGameReset)
            resetGame();

        if (retryTakeMatchTurn)
            takeMatchTurn(matchId);

        if (retryAcceptInvitation)
            acceptInvitation(matchId);

        refreshMatchList();
        refreshSavedGames();
        refreshAchievementList();

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (mResolvingConnectionFailure) {
            // already resolving
            return;
        }

        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (mSignInClicked || mAutoStartSignInFlow) {
            mAutoStartSignInFlow = false;
            mSignInClicked = false;
            mResolvingConnectionFailure = true;

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(this,
                    mGoogleApiClient, connectionResult,
                    RC_SIGN_IN, connectionResult.getErrorMessage())) {
                mResolvingConnectionFailure = false;
            }
        }

        rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Attempt to reconnect
        mGoogleApiClient.connect();
    }

    private PendingResult<Snapshots.CommitSnapshotResult> writeSnapshot(Snapshot snapshot, byte[] data, String desc) {

        // Set the data payload for the snapshot
        snapshot.getSnapshotContents().writeBytes(data);

        // Create the change operation
        SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder()
                //.setCoverImage(coverImage)
                .setDescription(desc)
                .build();

        // Commit the operation
        return Games.Snapshots.commitAndClose(mGoogleApiClient, snapshot, metadataChange);
    }

    public void signInClicked(View v) {
        Log.d(LOG_TAG, "Sign-in clicked");
            // start the asynchronous sign in flow
            mSignInClicked = true;
            mAutoStartSignInFlow = true;
            mGoogleApiClient.connect();
            SharedPreferences prefs = getSharedPreferences(GENERAL_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(AUTO_SIGN_IN, true);
            editor.apply();
    }

    public void signOutClicked(View v){
        // sign out.
        mSignInClicked = false;
        SharedPreferences prefs = getSharedPreferences(GENERAL_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(AUTO_SIGN_IN, false);
        editor.apply();
        mAutoStartSignInFlow = false;
        if (mGoogleApiClient.isConnected()) Games.signOut(mGoogleApiClient);

        hideGoogleUi();

        // show sign-in button, hide the sign-out button
        rootLayout.findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.GONE);
    }

    private void hideGoogleUi(){
        if (challengeListAdapter != null)
            challengeListAdapter.notifyDataSetChanged();

        rootLayout.findViewById(R.id.achievements_button).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.player_image).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.player_name_textview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.player_full_name_textview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.player_xp_textview).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.player_title_textview).setVisibility(View.GONE);

        if (activityListAdapter != null)
            activityListAdapter.removeGoogleItems();
    }

    private void showGoogleUi(){
        rootLayout.findViewById(R.id.achievements_button).setVisibility(View.VISIBLE);

        if (challengeListAdapter != null)
            challengeListAdapter.notifyDataSetChanged();

        rootLayout.findViewById(R.id.achievements_button).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.player_image).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.player_name_textview).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.player_full_name_textview).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.player_xp_textview).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.player_title_textview).setVisibility(View.VISIBLE);
    }

    private void showUsePlayGamesPrompt(){
        final SharedPreferences prefs = getSharedPreferences(GENERAL_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(SHOW_USE_GOOGLE_PLAY_PROMPT, true)) {
            final View layout = getLayoutInflater().inflate(R.layout.generic_popup, null);

            // Prepare popup window
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layout.setLayoutParams(params);
            layout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            TextView msgView = (TextView) layout.findViewById(R.id.textView);
            msgView.setText(R.string.use_google_play_prompt);

            final AppCompatCheckBox checkBox = (AppCompatCheckBox) layout.findViewById(R.id.checkBox);
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setText(R.string.dont_show_again);


            Button button1 = (Button) layout.findViewById(R.id.button1);
            button1.setText(R.string.yes);
            button1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(AUTO_SIGN_IN, true);
                    editor.putBoolean(SHOW_USE_GOOGLE_PLAY_PROMPT, !checkBox.isChecked());
                    editor.apply();
                    mSignInClicked = true;
                    mAutoStartSignInFlow = true;
                    mGoogleApiClient.connect();
                    AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(layout);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    set.start();
                }
            });

            Button button2 = (Button) layout.findViewById(R.id.button2);
            button2.setText(R.string.no);
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(AUTO_SIGN_IN, false);
                    editor.putBoolean(SHOW_USE_GOOGLE_PLAY_PROMPT, !checkBox.isChecked());
                    editor.apply();
                    rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.GONE);
                    rootLayout.findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
                    AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(layout);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    set.start();
                }
            });

            rootLayout.addView(layout);
            Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
        }
    }

    private void updatePlayerInfo(){
        Player player = Games.Players.getCurrentPlayer(mGoogleApiClient);
        TextView nameTextView = (TextView)rootLayout.findViewById(R.id.player_name_textview);
        nameTextView.setText(player.getDisplayName());
        if (nameTextView.getVisibility() != View.VISIBLE)
            Animations.fadeIn(nameTextView, 200, 0).start();

        TextView titleView = (TextView)rootLayout.findViewById(R.id.player_title_textview);
        titleView.setText(player.getTitle());
        if (titleView.getVisibility() != View.VISIBLE)
            Animations.fadeIn(titleView, 200, 75).start();

        TextView fullNameView = (TextView) rootLayout.findViewById(R.id.player_full_name_textview);
        if (player.getName() != null) {
            fullNameView.setText(player.getName());
            if (fullNameView.getVisibility() != View.VISIBLE)
                Animations.fadeIn(fullNameView, 200, 150).start();
        } else {
            fullNameView.setVisibility(View.GONE);
        }

        TextView xpView = (TextView)rootLayout.findViewById(R.id.player_xp_textview);
        xpView.setText(getString(R.string.xp, player.getLevelInfo().getCurrentXpTotal()));
        if (xpView.getVisibility() != View.VISIBLE)
            Animations.fadeIn(xpView, 200, 225).start();


        ImageView playerImageView = (ImageView)rootLayout.findViewById(R.id.player_image);
        ImageManager imageManager = ImageManager.create(context);
        if (rootLayout.getHeight() > 800)
            imageManager.loadImage(playerImageView, player.getHiResImageUri());
        else
            imageManager.loadImage(playerImageView, player.getIconImageUri());
    }

    private void refreshAll(){
        if (mGoogleApiClient.isConnected()) {
            updatePlayerInfo();
            refreshSavedGames();
            refreshMatchList();
            refreshAchievementList();
            refreshGameState();
        } else if (!mGoogleApiClient.isConnecting())
            mGoogleApiClient.connect();
    }


    // Ads
    private InterstitialAd mInterstitialAd;
    private boolean showAds = true;
    public static String SHOW_ADS = "SHOW_ADS";

    private void loadInterstitialAd(){
        if (showAds && !mInterstitialAd.isLoaded()) {
            AdRequest intstAdRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mInterstitialAd.loadAd(intstAdRequest);
        }
    }

    private void removeAds(){
        SharedPreferences.Editor editor = getSharedPreferences(GENERAL_PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(SHOW_ADS, false);
        editor.apply();
        showAds = false;
        rootLayout.findViewById(R.id.adView).setVisibility(View.GONE);
        String message = getString(R.string.ads_removed_notification);
        Snackbar snackbar = Snackbar.make(findViewById(R.id.myCoordinatorLayout), message, Snackbar.LENGTH_SHORT);
        snackbar.show();
    }


    // Utility Methods
    private void showLoadingPopup(int resId, int delay){
        if (rootLayout.findViewById(R.id.loading_popup) == null) {
            View loadingPopup = Utils.progressPopup(context, resId);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 100, delay, rootLayout.getHeight() / 3).start();
        }
    }

    private void hideLoadingPopup(){
        final View popup = rootLayout.findViewById(R.id.loading_popup);
        if (popup != null){
            AnimatorSet set = Animations.slideOutDown(popup, 150, 0, rootLayout.getHeight() / 3);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    rootLayout.removeView(popup);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }
}
