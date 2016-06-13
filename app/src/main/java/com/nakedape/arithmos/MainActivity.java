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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageButton;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

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

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        OnTurnBasedMatchUpdateReceivedListener, OnInvitationReceivedListener{

    private static final String LOG_TAG = "MainActivity";

    // Activity Result request codes
    public static int REQUEST_LEVEL_PLAYED = 300;
    public static int REQUEST_TAKE_MATCH_TURN = 301;

    private static final String ACTIVITY_PREFS = "activity_prefs";

    private Context context;
    private RelativeLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(ACTIVITY_PREFS, MODE_PRIVATE);
        mAutoStartSignInFlow = prefs.getBoolean(AUTO_SIGN_IN, true);

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER)
                // add other APIs and scopes here as needed
                .build();

        setContentView(R.layout.activity_main);
        context = this;
        rootLayout = (RelativeLayout) findViewById(R.id.main_activity_rootlayout);
        rootLayout.findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInClicked(v);
            }
        });
        gameBase = new ArithmosGameBase();
        activityListAdapter = new ActivityListAdapter(R.layout.activity_list_item);
        ListView activityList = (ListView)rootLayout.findViewById(R.id.activity_listview);
        activityList.setAdapter(activityListAdapter);
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
        if (mAutoStartSignInFlow && !mGoogleApiClient.isConnected() && activityListAdapter.getCount() == 0) {
            rootLayout.findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
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
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                final View popup = rootLayout.findViewById(R.id.pop_up);
                if (popup != null) {
                    AnimatorSet set = Animations.shrinkOut(popup, 200);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(popup);
                            Animations.popIn(rootLayout.findViewById(R.id.challenges_button), 200, 0).start();
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
            } else {
                // Bring up an error dialog to alert the user that sign-in
                // failed. The R.string.signin_failure should reference an error
                // string in your strings.xml file that tells the user they
                // could not be signed in, such as "Unable to sign in."
                BaseGameUtils.showActivityResultError(this,
                        requestCode, resultCode, R.string.gamehelper_sign_in_failed);
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

    }

    // Game Activity List
    private ActivityListAdapter activityListAdapter;
    private String gameToDeleteId;
    private boolean retryDeleteSavedLevel = false;

    private class ActivityListAdapter extends BaseAdapter{
        private int resource_id;
        private LayoutInflater mInflater;
        private ArrayList<GameActivityItem> items;
        private ArrayList<GameActivityItem> matches;
        private ArrayList<GameActivityItem> saves;
        private GameActivityItem emptyItem;
        private ImageManager imageManager = ImageManager.create(context);

        public ActivityListAdapter(int resource_id){
            this.resource_id = resource_id;
            items = new ArrayList<>();
            saves = new ArrayList<>();
            matches = new ArrayList<>();
            emptyItem = new GameActivityItem(GameActivityItem.EMPTY_ITEM);
            emptyItem.description = getString(R.string.empty_activity_item);
            //items.add(emptyItem);
        }

        public void addItems(ArrayList<GameActivityItem> newItems){
            for (GameActivityItem item : newItems){
                switch (item.itemType){
                    case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                        if (saves.contains(item))
                            saves.remove(item);
                        saves.add(item);
                        break;
                    case GameActivityItem.COMPLETED_LEVEL:
                    case GameActivityItem.INCOMPLETE_LEVEL:
                        if (items.contains(item))
                            items.remove(item);
                        items.add(item);
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
            if (getCount() > 1)
                items.remove(emptyItem);
            notifyDataSetChanged();
        }

        public void addItem(GameActivityItem item){
            switch (item.itemType){
                case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                    if (saves.contains(item))
                        saves.remove(item);
                    saves.add(item);
                    break;
                case GameActivityItem.COMPLETED_LEVEL:
                case GameActivityItem.INCOMPLETE_LEVEL:
                case GameActivityItem.EMPTY_ITEM:
                    if (items.contains(item))
                        items.remove(item);
                    items.add(item);
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
            if (getCount() > 1)
                items.remove(emptyItem);
            notifyDataSetChanged();
        }

        public void clearList(){
            items = new ArrayList<>(1);
            matches = new ArrayList<>();
            saves = new ArrayList<>();
            //items.add(emptyItem);
            notifyDataSetChanged();
        }

        public void removeItem(GameActivityItem item) {
            switch (item.itemType) {
                case GameActivityItem.INCOMPLETE_LEVEL:
                case GameActivityItem.COMPLETED_LEVEL:
                    gameBase.removeActivityItem(item);
                    saveGameState();
                    items.remove(item);
                    break;
                case GameActivityItem.GOOGLE_PLAY_SAVED_GAME:
                    saves.remove(item);
                    break;
                case GameActivityItem.MATCH_COMPLETE:
                case GameActivityItem.MATCH_INVITATION:
                case GameActivityItem.MATCH_MY_TURN:
                case GameActivityItem.MATCH_THEIR_TURN:
                    matches.remove(item);
                    break;
            }
            if (getCount() == 0) {
                addItem(emptyItem);
            }

            notifyDataSetChanged();
        }

        public void removeItem(int position){
            if (position < matches.size())
                matches.remove(position);
            else if (position < matches.size() + saves.size())
                saves.remove(position - matches.size());
            else if (position < matches.size() + saves.size() + items.size()){
                GameActivityItem item = items.get(position - matches.size() - saves.size());
                gameBase.removeActivityItem(item);
                items.remove(position - matches.size() - saves.size());
            }

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
        }

        @Override
        public GameActivityItem getItem(int position) {
            if (position < matches.size())
                return matches.get(position);
            else if (position < matches.size() + saves.size())
                return saves.get(position - matches.size());
            else if (position < matches.size() + saves.size() + items.size()){
                return items.get(position - matches.size() - saves.size());
            }
            else return emptyItem;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount(){
            return matches.size() + saves.size() + items.size();
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
                    playLevel(ArithmosGameBase.getLevelXmlIds(getItem(position).challengeName)[getItem(position).challengeLevel]);
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
        SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
        String gameFileName = prefs.getString(GAME_FILE_NAME, null);
        if (gameFileName != null)
            intent.putExtra(GameActivity.GAME_BASE_FILE_NAME, gameFileName);
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
            }
            Games.Snapshots.open(mGoogleApiClient, gameFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    String desc = "Arithmos Game Data";
                    writeSnapshot(openSnapshotResult.getSnapshot(), gameBase.getByteData(), desc);
                    Log.d(LOG_TAG, "Game state saved");
                }
            });
        } else {
            retrySaveGameBase = true;
            mGoogleApiClient.connect();
        }
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

    private void refreshGameState(){
        if (!isGameBaseRefreshing) {
            isGameBaseRefreshing = true;
            Games.Snapshots.load(mGoogleApiClient, false).setResultCallback(new ResultCallback<Snapshots.LoadSnapshotsResult>() {
                @Override
                public void onResult(@NonNull Snapshots.LoadSnapshotsResult loadSnapshotsResult) {
                    for (SnapshotMetadata s : loadSnapshotsResult.getSnapshots()) {
                        if (s.getUniqueName().contains(GAME_FILE_PREFIX)) {
                            loadGameState(s);
                        }
                        Log.d(LOG_TAG, s.getUniqueName());
                    }
                    loadSnapshotsResult.getSnapshots().release();
                }
            });
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
                loadSnapshotsResult.release();
            }
        });
    }

    private void loadGameState(SnapshotMetadata metadata){
        String gameFileName = metadata.getUniqueName();
        final SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(GAME_FILE_NAME, gameFileName);
        editor.apply();
        if (gameFileName != null && gameBaseNeedsRefresh) {
            Games.Snapshots.open(mGoogleApiClient, gameFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    try {
                        // Load data
                        gameBase.loadByteData(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());
                        // Update jewel count and challenges
                        TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                        Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                        // Update challenge list if it has already been displayed
                        if (challengeListAdapter != null) challengeListAdapter.notifyDataSetChanged();
                        // Update recent activity
                        activityListAdapter.addItems(gameBase.getActivityItems());
                        gameBaseNeedsRefresh = false;
                        isGameBaseRefreshing = false;
                        Log.d(LOG_TAG, "Game refreshed");
                    } catch (IOException | NullPointerException | ClassCastException e) {
                        e.printStackTrace();
                        if (e instanceof NullPointerException) {
                            SharedPreferences.Editor editor = prefs.edit();
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
    private static final String GAME_PREFS = "GAME_PREFS";
    private static final String GAME_FILE_NAME = "GAME_FILE_NAME";
    private static final String GAME_FILE_PREFIX = "ArithmosGame_";
    private ArithmosGameBase gameBase;
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

    private void playLevel(int levelXmlId){
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.LEVEL_XML_RES_ID, levelXmlId);
        SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
        String gameFileName = prefs.getString(GAME_FILE_NAME, null);
        if (gameFileName != null)
            intent.putExtra(GameActivity.GAME_BASE_FILE_NAME, gameFileName);
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
                        playLevel(ArithmosGameBase.getLevelXmlIds(getGroup(groupPosition))[childPosition]);
                    }
                });
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
                AnimatorSet set = Animations.slideDown(layout, 150, 0, rootLayout.getHeight() / 3);
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
                AnimatorSet set = Animations.slideDown(layout, 150, 0, rootLayout.getHeight() / 3);
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
                    gameBase.resetGame();
                    activityListAdapter.clearList();
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
        private int resource_id = R.layout.store_item_layout;
        private LayoutInflater mInflater;
        private String[] names = getResources().getStringArray(R.array.special_item_names);
        private String[] descriptions = getResources().getStringArray(R.array.special_descriptions);
        private int[] costs = getResources().getIntArray(R.array.special_costs);
        private int[] iconResIds = {R.drawable.ic_right_arrow, R.drawable.ic_bomb,
                R.drawable.ic_pencil, R.drawable.ic_calculator};

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
            descView.setText(descriptions[position]);

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
                        countView.setText(String.valueOf(gameBase.getSpecialCount(names[position])));
                        TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                        Animations.CountTo(jewelText, prevCount, gameBase.getJewelCount());
                        saveGameState();
                    }
                }
            });
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
                titleView.setTextColor(getColor(R.color.text_primary_dark));
                descView.setTextColor(getColor(R.color.text_primary_dark));
                xpView.setTextColor(getColor(R.color.text_primary_dark));
            } else {
                titleView.setTextColor(getColor(R.color.text_inactive_dark));
                descView.setTextColor(getColor(R.color.text_inactive_dark));
                xpView.setTextColor(getColor(R.color.text_inactive_dark));
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


    // New game methods
    public void NewGameButtonClick(View v) {
        Animations.shrinkOut(v, 100).start();
        ShowCreateGamePopup();
    }

    private void ShowCreateGamePopup() {
        final View layout = getLayoutInflater().inflate(R.layout.popup_game_config, null);
        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);

        RelativeLayout window = (RelativeLayout) layout.findViewById(R.id.window_layout);
        window.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        final Spinner gridSizes = (Spinner) layout.findViewById(R.id.grid_size_spinner);
        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.gridSizes, R.layout.game_options_spinner_item);
        adapter.setDropDownViewResource(R.layout.game_options_spinner_dropdown_item);
        gridSizes.setAdapter(adapter);
        gridSizes.setSelection(1);

        final Spinner goalModes = (Spinner) layout.findViewById(R.id.goal_mode_spinner);
        adapter = ArrayAdapter.createFromResource(this, R.array.goalModes, R.layout.game_options_spinner_item);
        adapter.setDropDownViewResource(R.layout.game_options_spinner_dropdown_item);
        goalModes.setAdapter(adapter);
        final CheckBox allowBonus = (CheckBox) layout.findViewById(R.id.allow_bonus_checkbox);
        final CheckBox allowAutoComplete = (CheckBox) layout.findViewById(R.id.allow_autocomplete_checkbox);
        Button playButton = (Button) layout.findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootLayout.removeView(layout);
                String gridSize = (String) gridSizes.getSelectedItem();
                String goalMode = (String) goalModes.getSelectedItem();
                CreateGame(gridSize, goalMode, allowBonus.isChecked(), allowAutoComplete.isChecked());
            }
        });

        // Click handler to close popup
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Animate
                AnimatorSet set = Animations.shrinkOut(layout, 200);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                        Animations.popIn(rootLayout.findViewById(R.id.challenges_button), 200, 0).start();
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
        Animations.popIn(layout, 200, 0).start();
    }

    private void CreateGame(String gridSize, String goalMode, boolean allowBonus, boolean allowAutocomplete) {
        Bundle options = new Bundle();

        // Specify grid size
        if (gridSize.equals(getString(R.string.grid_10x10))) {
            options.putInt(GameActivity.GRID_SIZE, 10);
        } else if (gridSize.equals(getString(R.string.grid_8x8))) {
            options.putInt(GameActivity.GRID_SIZE, 8);
        } else if (gridSize.equals(getString(R.string.grid_6x6))) {
            options.putInt(GameActivity.GRID_SIZE, 6);
        }

        // Specify goal mode
        if (goalMode.equals(getString(R.string.single_number))) {
            options.putInt(GameActivity.GOAL_MODE, ArithmosLevel.GOAL_SINGLE_NUM);
        } else if (goalMode.equals(getString(R.string.random_number))) {
            options.putInt(GameActivity.GOAL_MODE, ArithmosLevel.GOAL_MULT_NUM);
        } else if (goalMode.equals(getString(R.string.multiples))) {
            options.putInt(GameActivity.GOAL_MODE, ArithmosLevel.GOAL_MULTIPLES);
        } else if (goalMode.equals(getString(R.string.three01))) {
            options.putInt(GameActivity.GOAL_MODE, ArithmosLevel.GOAL_301);
        }

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRAS, options);
        startActivity(intent);
    }


    // Turn based matches
    private static int RC_SELECT_PLAYERS = 9002;
    private int matchLevelXmlId;
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
        int[] statusTypes = new int[]{TurnBasedMatch.MATCH_TURN_STATUS_INVITED, TurnBasedMatch.MATCH_TURN_STATUS_MY_TURN,
                                    TurnBasedMatch.MATCH_TURN_STATUS_THEIR_TURN, TurnBasedMatch.MATCH_TURN_STATUS_COMPLETE};
        Games.TurnBasedMultiplayer.loadMatchesByStatus(mGoogleApiClient, statusTypes).setResultCallback(new ResultCallback<TurnBasedMultiplayer.LoadMatchesResult>() {
            @Override
            public void onResult(@NonNull TurnBasedMultiplayer.LoadMatchesResult loadMatchesResult) {
                for (Invitation i : loadMatchesResult.getMatches().getInvitations()){
                    GameActivityItem item = new GameActivityItem(GameActivityItem.MATCH_INVITATION);
                    item.challengeName = i.getInviter().getDisplayName();
                    item.timeStamp = i.getCreationTimestamp();
                    item.uniqueName = i.getInvitationId();
                    item.imageUri = i.getInviter().getIconImageUri();
                    activityListAdapter.addItem(item);
                }

                for (TurnBasedMatch match : loadMatchesResult.getMatches().getMyTurnMatches()){
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
                    if (match.canRematch()){
                        item.canRematch = match.canRematch();
                        item.rematchId = match.getRematchId();
                    }
                    ParticipantResult result = match.getParticipant(match.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient))).getResult();
                    if (result != null)
                        item.matchResult = result.getResult();
                    activityListAdapter.addItem(item);
                }

                loadMatchesResult.release();
                rootLayout.findViewById(R.id.progress_bar).setVisibility(View.GONE);
                Log.d(LOG_TAG, "Matches loaded");
            }
        });
    }

    private void acceptInvitation(final String matchId){
        final View popup = Utils.progressPopup(context, getString(R.string.accept_invitation_progress));
        Animations.slideUp(popup, 150, 0, rootLayout.getHeight() / 3).start();
        Games.TurnBasedMultiplayer.acceptInvitation(mGoogleApiClient, matchId).setResultCallback(new ResultCallback<TurnBasedMultiplayer.InitiateMatchResult>() {
            @Override
            public void onResult(@NonNull TurnBasedMultiplayer.InitiateMatchResult initiateMatchResult) {
                takeMatchTurn(matchId);
            }
        });
    }

    private void takeMatchTurn(String matchId){
        showLoadingPopup(R.string.loading, 0);
        Games.TurnBasedMultiplayer.loadMatch(mGoogleApiClient, matchId).setResultCallback(new MatchLoadedCallback());
    }

    private void declineInvite(String id){
        Games.TurnBasedMultiplayer.declineInvitation(mGoogleApiClient, id);
    }

    private void dismissMatch(String id){
        Games.TurnBasedMultiplayer.dismissMatch(mGoogleApiClient, id);
    }

    private void rematch(String id){
        Games.TurnBasedMultiplayer.rematch(mGoogleApiClient, id).setResultCallback(new MatchInitiatedCallback());
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

            TurnBasedMatch match = result.getMatch();

            // If this player is not the first player in this match, continue.
            if (match.getData() != null) {

            }

            Intent intent = new Intent(context, MatchGameActivity.class);
            intent.putExtra(MatchGameActivity.LEVEL_XML_RES_ID, matchLevelXmlId);
            SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
            String gameFileName = prefs.getString(GAME_FILE_NAME, null);
            if (gameFileName != null)
                intent.putExtra(GameActivity.GAME_BASE_FILE_NAME, gameFileName);
            intent.putExtra(MatchGameActivity.CREATE_MATCH, true);
            intent.putExtra(MatchGameActivity.MATCH, match);
            startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);

            // Otherwise, this is the first player. Initialize the game state.

            // Let the player take the first turn
        }
    }
    public class MatchLoadedCallback implements ResultCallback<TurnBasedMultiplayer.LoadMatchResult>{
        @Override
        public void onResult(@NonNull TurnBasedMultiplayer.LoadMatchResult loadMatchResult) {
            Status status = loadMatchResult.getStatus();
            if (!status.isSuccess()) {
                Log.d(LOG_TAG, status.getStatusMessage());

                return;
            }

            TurnBasedMatch match = loadMatchResult.getMatch();

                hideLoadingPopup();
                Intent intent = new Intent(context, MatchGameActivity.class);
                intent.putExtra(MatchGameActivity.MATCH, match);
                SharedPreferences prefs = getSharedPreferences(GAME_PREFS, MODE_PRIVATE);
                String gameFileName = prefs.getString(GAME_FILE_NAME, null);
                if (gameFileName != null)
                    intent.putExtra(GameActivity.GAME_BASE_FILE_NAME, gameFileName);
                startActivityForResult(intent, REQUEST_TAKE_MATCH_TURN);
                return;
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

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;
    private static final String AUTO_SIGN_IN = "auto_sign_in";

    @Override
    public void onConnected(Bundle connectionHint) {
        // show sign-out button, hide the sign-in button
        rootLayout.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
        rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
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

        findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
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

    // Call when the sign-in button is clicked
    public void signInClicked(View v) {
            // start the asynchronous sign in flow
            mSignInClicked = true;
            mAutoStartSignInFlow = true;
            mGoogleApiClient.connect();
            SharedPreferences prefs = getSharedPreferences(ACTIVITY_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(AUTO_SIGN_IN, true);
            editor.apply();
    }

    public void signOutClicked(View v){
        // sign out.
        mSignInClicked = false;
        SharedPreferences prefs = getSharedPreferences(ACTIVITY_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(AUTO_SIGN_IN, false);
        editor.apply();
        mAutoStartSignInFlow = false;
        if (mGoogleApiClient.isConnected()) Games.signOut(mGoogleApiClient);

        // show sign-in button, hide the sign-out button
        rootLayout.findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.sign_out_button).setVisibility(View.GONE);
    }

    private void updatePlayerInfo(){
        Player player = Games.Players.getCurrentPlayer(mGoogleApiClient);
        TextView nameTextView = (TextView)rootLayout.findViewById(R.id.player_name_textview);
        nameTextView.setText(player.getDisplayName());

        TextView titleView = (TextView)rootLayout.findViewById(R.id.player_title_textview);
        titleView.setText(player.getTitle());

        TextView fullNameView = (TextView)rootLayout.findViewById(R.id.player_full_name_textview);
        fullNameView.setText(player.getName());

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
        } else if (!mGoogleApiClient.isConnecting())
            mGoogleApiClient.connect();
    }


    // Utility Methods

    private void showLoadingPopup(int resId, int delay){
        View loadingPopup = Utils.progressPopup(context, resId);
        rootLayout.addView(loadingPopup);
        Animations.slideUp(loadingPopup, 100, delay, rootLayout.getHeight() / 3).start();
    }

    private void hideLoadingPopup(){
        final View popup = rootLayout.findViewById(R.id.loading_popup);
        if (popup != null){
            AnimatorSet set = Animations.slideDown(popup, 150, 0, rootLayout.getHeight() / 3);
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
