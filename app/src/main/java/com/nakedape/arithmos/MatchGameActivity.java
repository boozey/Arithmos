package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.multiplayer.ParticipantResult;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMultiplayer;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;

public class MatchGameActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, GameBoard.OnAchievementListener,
        GameBoard.OnPlayCompleted, GameBoard.OnGameOverListener,
        GameBoard.OnJewelListener, GameBoard.OnBombListener, GameBoard.OnCancelBombListener {

    private static final String LOG_TAG = "MatchGameActivity";

    // Intent Extra tags
    public static final String LEVEL_XML_RES_ID = "level_xml_res_id";
    public static final String CREATE_MATCH = "create_match";
    public static final String MATCH = "match";

    // Saving/recreating the activity state
    private static final String JEWEL_COUNT = "JEWEL_COUNT";
    private static final String HAS_LEVEL_PASSED_SHOWN = "HAS_LEVEL_PASSED_SHOWN";
    private static final String PLAY_COUNT = "PLAY_COUNT";
    private static final String IS_GOALVIEW_PLAYING = "IS_GOALVIEW_PLAYING";
    public static final String matchCacheFileName = "arithmos_match_cache";
    public static final String levelCacheFileName = "arithmos_level_cache";
    private static final String gameCacheFileName = "arithmos_game_cache";

    private Context context;
    private RelativeLayout rootLayout;
    private ArithmosGame game;
    private GameBoard gameBoard;
    private TextView scoreTextView;
    private int prevScore = 0, playCount = 0, jewelCount = 0;

    // Ads
    private boolean showAds = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER)
                // add other APIs and scopes here as needed
                .build();

        setContentView(R.layout.activity_match_game);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);

        SharedPreferences generalPrefs = getSharedPreferences(MainActivity.GENERAL_PREFS, MODE_PRIVATE);
        // Setup Ads
        showAds = generalPrefs.getBoolean(MainActivity.SHOW_ADS, true);
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
        }

        gameBoard = (GameBoard)rootLayout.findViewById(R.id.game_board);
        gameBoard.setOnAchievementListener(this);
        gameBoard.setOnPlayCompleted(this);
        gameBoard.setOnGameOverListener(this);
        gameBoard.setOnJewelListener(this);
        gameBoard.setOnBombListener(this);
        gameBoard.setOnCancelBombListener(this);
        gameBase = new ArithmosGameBase();

        // Load saved state and cached data
        if (savedInstanceState != null) {
            jewelCount = savedInstanceState.getInt(JEWEL_COUNT, 0);
            playCount = savedInstanceState.getInt(PLAY_COUNT, 0);
            match = savedInstanceState.getParcelable(MATCH);
            hasLevelPassedShown = savedInstanceState.getBoolean(HAS_LEVEL_PASSED_SHOWN);
            isGoalViewPlaying = savedInstanceState.getBoolean(IS_GOALVIEW_PLAYING, false);
        }

        if (!loadCachedGame()) gameBase = new ArithmosGameBase();

        if (loadCachedLevel() && match != null) {
            matchHasLoaded = true;
        }
        else {
            loadCachedMatch();
            // Prepare for match game
            Intent data = getIntent();
            if (match == null && data.hasExtra(MATCH)) {
                match = data.getParcelableExtra(MATCH);
                cacheMatch();
                showLoadingPopup(R.string.loading, 200);
            }
            else {
                finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
        gameBoard.startGame();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (game != null && isGoalViewPlaying) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    GoalView goalView = (GoalView) rootLayout.findViewById(R.id.goal_view);
                    goalView.startGoalAnimation();
                }
            });
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        if (game != null && game.getGoalType() == ArithmosLevel.GOAL_SINGLE_NUM) {
            GoalView goalView = (GoalView) rootLayout.findViewById(R.id.goal_view);
            goalView.stopGoalAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        gameBoard.stopGame();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putInt(JEWEL_COUNT, jewelCount);
        savedInstanceState.putBoolean(HAS_LEVEL_PASSED_SHOWN, hasLevelPassedShown);
        savedInstanceState.putInt(PLAY_COUNT, playCount);
        savedInstanceState.putParcelable(MATCH, match);
        GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
        savedInstanceState.putBoolean(IS_GOALVIEW_PLAYING, goalView.isPlaying());

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                if (rootLayout.findViewById(R.id.match_turn_end_popup) != null){
                    return true;
                }
                else if (rootLayout.findViewById(R.id.match_finished_popup) != null){
                    return true;
                }
                else if (rootLayout.findViewById(R.id.generic_popup) == null) {
                    showFinishTurnPrompt();
                }
                else {
                    AnimatorSet set = Animations.slideOutDown(rootLayout.findViewById(R.id.generic_popup), 150, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(rootLayout.findViewById(R.id.generic_popup));
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
                return true;
            default:
                return super.onKeyDown(keycode, e);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent){
        // Google play games services sign-in
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
    }


    // Game saving and loading
    private ArithmosGameBase gameBase;
    private File levelCache, gameCache;
    private boolean retryFinishTurn = false, retryFinishMatch = false, matchHasLoaded = false;
    private int levelXmlId;

    private void setupGameUi(){
        gameBoard.setGame(game);

        TextView p1NameView = (TextView)rootLayout.findViewById(R.id.p1_name_textview);
        TextView p2NameView = (TextView)rootLayout.findViewById(R.id.p2_name_textview);
        TextView p1ScoreView = (TextView)rootLayout.findViewById(R.id.p1_score_textview);
        TextView p2ScoreView = (TextView)rootLayout.findViewById(R.id.p2_score_textview);
        p1NameView.setText(match.getParticipant(game.PLAYER1).getDisplayName());
        p2NameView.setText(match.getParticipant(game.PLAYER2).getDisplayName());
        p1ScoreView.setText(NumberFormat.getIntegerInstance().format(game.getScore(game.PLAYER1)));
        p2ScoreView.setText(NumberFormat.getIntegerInstance().format(game.getScore(game.PLAYER2)));
        prevScore = game.getScore(game.getCurrentPlayer());

        if (game.getCurrentPlayer().equals(game.PLAYER1))
            scoreTextView = p1ScoreView;
        else
            scoreTextView = p2ScoreView;

        if (game.getGoalType() == ArithmosLevel.GOAL_301){
            rootLayout.findViewById(R.id.goal_view).setVisibility(View.GONE);

            // Show score and 301 total together for other player
            if (game.getCurrentPlayer().equals(game.PLAYER1)){
                p2ScoreView.setText(getString(R.string.score_slash_301, NumberFormat.getIntegerInstance().format(game.getScore(game.PLAYER2)),
                        String.valueOf(game.get301Total(game.PLAYER2))));
            } else {
                p1ScoreView.setText(getString(R.string.score_slash_301, NumberFormat.getIntegerInstance().format(game.getScore(game.PLAYER1)),
                        String.valueOf(game.get301Total(game.PLAYER1))));
            }

            TextView three01 = (TextView)rootLayout.findViewById(R.id.three01_textview);
            three01.setVisibility(View.VISIBLE);
            String value = String.valueOf(game.get301Total());
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                String finalValue = "";
                for (int i = 0; i < value.length(); i++) {
                    finalValue += value.charAt(i) + "\n";
                }
                three01.setText(finalValue.trim());
            } else
                three01.setText(value);
        } else {
            rootLayout.findViewById(R.id.three01_textview).setVisibility(View.GONE);
            GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
            goalView.setVisibility(View.VISIBLE);
            goalView.setGame(game);
        }

        // Show unavailabe operations
        rootLayout.findViewById(R.id.slash_div).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_mult).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_sub).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_add).setVisibility(View.VISIBLE);

        for (String s : game.availableOperations()){
            switch (s){
                case ArithmosGame.ADD:
                    rootLayout.findViewById(R.id.slash_add).setVisibility(View.GONE);
                    break;
                case ArithmosGame.SUBTRACT:
                    rootLayout.findViewById(R.id.slash_sub).setVisibility(View.GONE);
                    break;
                case ArithmosGame.MULTIPLY:
                    rootLayout.findViewById(R.id.slash_mult).setVisibility(View.GONE);
                    break;
                case ArithmosGame.DIVIDE:
                    rootLayout.findViewById(R.id.slash_div).setVisibility(View.GONE);
                    break;
            }
        }
        setupSpecials();
        matchHasLoaded = true;
    }

    private void setupSpecials(){
        if (game == null || gameBase == null) return;
        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
        bomb.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BombLongClick(v);
                return true;
            }
        });
        int count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_BOMB);
        if (count > 0)
            Animations.popIn(bomb, 200, 100).start();
        if (count > 1) {
            bomb.setCompoundDrawablePadding(-12);
            bomb.setText(String.valueOf(count));
        }

        TextView calc = (TextView)rootLayout.findViewById(R.id.calc_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_OP_ORDER);
        if (count > 0)
            Animations.popIn(calc, 200, 175).start();
        if (count > 1) {
            calc.setCompoundDrawablePadding(-12);
            calc.setText(String.valueOf(count));
        }

        TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
        pencil.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PencilLongClick(v);
                return false;
            }
        });
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_CHANGE);
        if (count > 0)
            Animations.popIn(pencil, 200, 250).start();
        if (count > 1) {
            pencil.setCompoundDrawablePadding(-12);
            pencil.setText(String.valueOf(count));
        }

        if (game.getGoalType() != ArithmosLevel.GOAL_301) {
            TextView arrow = (TextView) rootLayout.findViewById(R.id.skip_button);
            count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP);
            if (count > 0)
                Animations.popIn(arrow, 200, 325).start();
            if (count > 1) {
                arrow.setCompoundDrawablePadding(-12);
                arrow.setText(String.valueOf(count));
            }
        }

        TextView zero = (TextView)rootLayout.findViewById(R.id.zero_button);
        zero.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ZeroButtonClick(v);
                return false;
            }
        });
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_ZERO);
        if (count > 0)
            Animations.popIn(zero, 200, 325).start();
        if (count > 1) {
            zero.setCompoundDrawablePadding(-8);
            zero.setText(String.valueOf(count));
        }

        if (game.getGoalType() != ArithmosLevel.GOAL_301){
            TextView autoView = (TextView)rootLayout.findViewById(R.id.auto_run_button);
            count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_AUTO_RUN);
            if (count > 0 && autoView.getVisibility() != View.VISIBLE)
                Animations.popIn(autoView, 200, 325).start();
            if (count > 1) {
                autoView.setCompoundDrawablePadding(-8);
                autoView.setText(String.valueOf(count));
            }
        }
    }

    private void showLevelInfoPopup(){
        final View layout = getLayoutInflater().inflate(R.layout.level_info_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView titleView = (TextView)layout.findViewById(R.id.title_textview);
        titleView.setText(ArithmosGameBase.getChallengeDisplayNameResId(game.getChallengeName()));

        TextView subTitleView = (TextView)layout.findViewById(R.id.subtitle_textview);
        subTitleView.setText(ArithmosGameBase.getLevelDisplayNameResIds(game.getChallengeName())[game.getChallengeLevel()]);

        TextView descView = (TextView)layout.findViewById(R.id.description_textview);
        descView.setVisibility(View.VISIBLE);
        if (game.getGoalType() != ArithmosLevel.GOAL_301) {
            if (game.getNumGoalsToWin() < game.getGoalList().size()){
                descView.setText(getString(R.string.find_x_of_x_numbers, game.getNumGoalsToWin(), game.getGoalList().size()));
            } else {
                descView.setText(getString(R.string.find_x_numbers, game.getNumGoalsToWin()));
            }
        }else {
            descView.setText(R.string.three01_description);
        }

        TextView oneStarView = (TextView)layout.findViewById(R.id.one_star);
        oneStarView.setText(NumberFormat.getIntegerInstance().format(game.getPointsForStar(1)));

        TextView twoStarView = (TextView)layout.findViewById(R.id.two_star);
        twoStarView.setText(NumberFormat.getIntegerInstance().format(game.getPointsForStar(2)));

        TextView threeStarView = (TextView)layout.findViewById(R.id.three_star);
        threeStarView.setText(NumberFormat.getIntegerInstance().format(game.getPointsForStar(3)));

        TextView timeView = (TextView)layout.findViewById(R.id.time_limit_text);
        timeView.setVisibility(View.GONE);

        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 100, 0, rootLayout.getHeight() / 3);
                // Time is started in OnAnimationEnd
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                        GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
                        goalView.startGoalAnimation();
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

    private void showLoadingPopup(int resId, int delay){
        View loadingPopup = Utils.progressPopup(context, resId);
        rootLayout.addView(loadingPopup);
        Animations.slideUp(loadingPopup, 100, delay, rootLayout.getHeight() / 3).start();
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

    private void cacheGame(){
        if (gameCache == null)
            gameCache = new File(getCacheDir(), gameCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(gameCache);
            byte[] data = gameBase.getByteData();
            outputStream.write(data, 0, data.length);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadCachedGame(){
        gameCache = new File(getCacheDir(), gameCacheFileName);
        if (gameCache.exists()){
            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(gameCache);
                byte[] data = new byte[inputStream.available()];
                if (inputStream.read(data) > 0) {
                    gameBase.loadByteData(data);
                    gameBase.setSaved(false);
                    setupSpecials();
                    TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                    Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                    Log.d(LOG_TAG, "Game state loaded from cache");
                    return true;
                }
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private void cacheLevel(){
        if (levelCache == null)
            levelCache = new File(getCacheDir(), levelCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(levelCache);
            byte[] data = game.getSaveGameData();
            outputStream.write(data, 0, data.length);
            outputStream.close();
            cacheMatch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadCachedLevel(){
        levelCache = new File(getCacheDir(), levelCacheFileName);
        if (levelCache.exists()){
            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(levelCache);
                byte[] data = new byte[inputStream.available()];
                if (inputStream.read(data) > 0){
                    game = new ArithmosGame(data);
                    Log.d(LOG_TAG, "Level loaded from cache");
                    return true;
                }
                inputStream.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        return false;
    }

    private void cacheMatch(){
        File matchCacheFile = new File(getCacheDir(), matchCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(matchCacheFile);
            ObjectOutputStream oos = new ObjectOutputStream(outputStream);
            oos.writeObject(new MatchCacheData(match.getMatchId(), playCount));
            oos.close();
            outputStream.close();
            Log.d(LOG_TAG, "Match cached. Playcount = " + playCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class MatchCacheData implements Serializable{
        transient private int serializationVersion = 0;
        transient public String matchId;
        transient public int playCount;
        public MatchCacheData(String matchId, int playCount){
            this.matchId = matchId;
            this.playCount = playCount;
        }
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeInt(serializationVersion);
            out.writeObject(matchId);
            out.writeInt(playCount);
        }
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException{
            int version = in.readInt();
            matchId = (String)in.readObject();
            playCount = in.readInt();
        }
    }

    private void loadCachedMatch(){
        File matchCacheFile = new File(getCacheDir(), matchCacheFileName);
        if (matchCacheFile.exists()) {
            FileInputStream fis;
            ObjectInputStream ois;
            try {
                fis = new FileInputStream(matchCacheFile);
                ois = new ObjectInputStream(fis);
                MatchCacheData data = (MatchCacheData) ois.readObject();
                playCount = data.playCount;
                ois.close();
                fis.close();
                Log.d(LOG_TAG, "Match loaded from cache");
            } catch (Exception e) {
                e.printStackTrace();
                matchCacheFile.delete();
            }
        }
    }

    private void showFinishTurnPrompt(){
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
        msgView.setText(getResources().getQuantityString(R.plurals.finish_turn_prompt, 3 - playCount, 3 - playCount));

        layout.findViewById(R.id.checkBox).setVisibility(View.GONE);

        Button button1 = (Button)layout.findViewById(R.id.button1);
        button1.setText(R.string.submit_turn);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootLayout.removeView(layout);
                Animations.slideOutDown(layout, 150, 0, rootLayout.getHeight() / 3).start();
                showEndTurnPopup();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.forfeit_match);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isLevelPassed = true;
                OnGameOver(result);
                Animations.slideOutDown(layout, 150, 0, rootLayout.getHeight() / 3).start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 100, 0, rootLayout.getHeight() / 3).start();
    }

    private void showLevelPassedPopup(){
        if (hasLevelPassedShown) return;
        hasLevelPassedShown = true;
        final View layout = getLayoutInflater().inflate(R.layout.quick_popup, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layout.setLayoutParams(params);
        layout.setVisibility(View.INVISIBLE);
        rootLayout.addView(layout);
        layout.post(new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = Animations.slideInDownAndOutUp(layout, 3000, animStartDelay + gameBoard.getAnimDelay(), layout.getMeasuredHeight());
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

    }

    private void showEndTurnPopup(){
        final View layout = getLayoutInflater().inflate(R.layout.match_end_turn_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        ImageView playerView = (ImageView)layout.findViewById(R.id.player_icon);
        ImageManager.create(context).loadImage(playerView, match.getParticipant(game.getNextPlayer()).getIconImageUri());

        TextView textView = (TextView)layout.findViewById(R.id.player_comment);
        textView.setText(game.getMessage(game.getNextPlayer()));

        final EditText messageView = (EditText)layout.findViewById(R.id.message);
        messageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                game.setMessage(game.getCurrentPlayer(), messageView.getText().toString());
                finishTurn();
                AnimatorSet set = Animations.slideOutDown(layout, 100, 0, rootLayout.getHeight() / 3);
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
                return true;
            }
        });

        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                game.setMessage(game.getCurrentPlayer(), messageView.getText().toString());
                finishTurn();
                AnimatorSet set = Animations.slideOutDown(layout, 100, 0, rootLayout.getHeight() / 3);
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

    private void showEndMatchPopup(int delay) {
        recordAchievements();
        final View layout = getLayoutInflater().inflate(R.layout.match_finished_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        String winner = game.getWinner(), loser;
        if (winner.equals(ArithmosGame.TIE)) {
            winner = game.PLAYER1;
            loser = game.PLAYER2;
            TextView titleView = (TextView) layout.findViewById(R.id.title_textview);
            titleView.setText(R.string.tie);
        } else {
            if (winner.equals(game.PLAYER1)) loser = game.PLAYER2;
            else loser = game.PLAYER1;

            TextView titleView = (TextView) layout.findViewById(R.id.title_textview);
            if (match.getParticipantId(Games.Players.getCurrentPlayer(mGoogleApiClient).getPlayerId()).equals(winner))
                titleView.setText(R.string.you_won);
            else
                titleView.setText(R.string.you_lost);
        }
        // Show winner info
        TextView winnerNameView = (TextView) layout.findViewById(R.id.winner_name_textview);
        winnerNameView.setText(match.getParticipant(winner).getDisplayName());

        TextView winnerScoreView = (TextView) layout.findViewById(R.id.winner_score_textview);
        winnerScoreView.setText(NumberFormat.getIntegerInstance().format(game.getScore(winner)));

        TextView winnerJewelView = (TextView) layout.findViewById(R.id.winner_jewel_count);
        winnerJewelView.setText(getString(R.string.number_after_x, game.getBonusCount(winner, ArithmosLevel.BONUS_RED_JEWEL)));

        TextView winnerBalloonView = (TextView) layout.findViewById(R.id.winner_balloon_count);
        winnerBalloonView.setText(getString(R.string.number_after_x, game.getBonusCount(winner, ArithmosLevel.BONUS_BALLOONS)));

        TextView winnerLockView = (TextView) layout.findViewById(R.id.winner_lock_count);
        winnerLockView.setText(getString(R.string.number_after_x, game.getBonusCount(winner, ArithmosLevel.BONUS_OP_LOCK)));

        // Show loser info
        TextView loserNameView = (TextView) layout.findViewById(R.id.loser_name_textview);
        loserNameView.setText(match.getParticipant(loser).getDisplayName());

        TextView loserScoreView = (TextView) layout.findViewById(R.id.loser_score_textview);
        loserScoreView.setText(NumberFormat.getIntegerInstance().format(game.getScore(loser)));

        TextView loserJewelView = (TextView) layout.findViewById(R.id.loser_jewel_count);
        loserJewelView.setText(getString(R.string.number_after_x, game.getBonusCount(loser, ArithmosLevel.BONUS_RED_JEWEL)));

        TextView loserBalloonView = (TextView) layout.findViewById(R.id.loser_balloon_count);
        loserBalloonView.setText(getString(R.string.number_after_x, game.getBonusCount(loser, ArithmosLevel.BONUS_BALLOONS)));

        TextView loserLockView = (TextView) layout.findViewById(R.id.loser_lock_count);
        loserLockView.setText(getString(R.string.number_after_x, game.getBonusCount(loser, ArithmosLevel.BONUS_OP_LOCK)));

        Button okButton = (Button) layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishMatch();
                AnimatorSet set = Animations.slideOutDown(layout, 100, 0, rootLayout.getHeight() / 3);
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
        AnimatorSet set = Animations.slideUp(layout, 100, delay, rootLayout.getHeight() / 3);
        set.start();
    }

    private void recordAchievements(){
        if (game.isLevelPassed()) {
            if (game.getGoalType() == ArithmosLevel.GOAL_301 && game.getRunCount() <= 3)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_301_in_3));
            if (gameBase.getJewelCount() >= 1000)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_the_one_percent));
        }
    }

    public void finishTurn(){
        if (rootLayout.findViewById(R.id.loading_popup) == null) {
            View loadingPopup = Utils.progressPopup(context, R.string.saving_game_message);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 100, 0, rootLayout.getHeight() / 3).start();
        }
        if (mGoogleApiClient.isConnected()) {
            if (jewelCount > 0)
                Games.Achievements.increment(mGoogleApiClient, getString(R.string.achievement_collector), jewelCount);
            Games.TurnBasedMultiplayer.takeTurn(mGoogleApiClient, match.getMatchId(), game.getSaveGameData(), game.getNextPlayer()).setResultCallback(new ResultCallback<TurnBasedMultiplayer.UpdateMatchResult>() {
                @Override
                public void onResult(@NonNull TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
                    cacheGame();
                    if (levelCache != null) levelCache.delete();
                    File matchCache = new File(getCacheDir(), matchCacheFileName);
                    if (matchCache.exists()) {
                        if (matchCache.delete()) Log.d(LOG_TAG, "Match cache deleted");
                        else Log.d(LOG_TAG, "Error deleting match cache");
                    }

                    finish();
                }
            });
        } else {
            retryFinishTurn = true;
            mGoogleApiClient.connect();
        }
    }

    public void finishMatch(){
        if (rootLayout.findViewById(R.id.loading_popup) == null) {
            View loadingPopup = Utils.progressPopup(context, R.string.saving_game_message);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 100, 0, rootLayout.getHeight() / 3).start();
        }
        if (mGoogleApiClient.isConnected()) {
            if (jewelCount > 0)
                Games.Achievements.increment(mGoogleApiClient, getString(R.string.achievement_collector), jewelCount);
            if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_ACTIVE) {
                ParticipantResult p1Result, p2Result;
                if (game.getScore(game.PLAYER1) > game.getScore(game.PLAYER2)) {
                    p1Result = new ParticipantResult(game.PLAYER1, ParticipantResult.MATCH_RESULT_WIN, 1);
                    p2Result = new ParticipantResult(game.PLAYER2, ParticipantResult.MATCH_RESULT_LOSS, 2);
                } else if (game.getScore(game.PLAYER1) < game.getScore(game.PLAYER2)) {
                    p1Result = new ParticipantResult(game.PLAYER1, ParticipantResult.MATCH_RESULT_LOSS, 2);
                    p2Result = new ParticipantResult(game.PLAYER2, ParticipantResult.MATCH_RESULT_WIN, 1);
                } else {
                    p1Result = new ParticipantResult(game.PLAYER1, ParticipantResult.MATCH_RESULT_TIE, 1);
                    p2Result = new ParticipantResult(game.PLAYER2, ParticipantResult.MATCH_RESULT_TIE, 1);
                }
                Games.TurnBasedMultiplayer.finishMatch(mGoogleApiClient, match.getMatchId(), game.getSaveGameData(), p1Result, p2Result).setResultCallback(new ResultCallback<TurnBasedMultiplayer.UpdateMatchResult>() {
                    @Override
                    public void onResult(@NonNull TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
                        cacheGame();
                        if (levelCache != null) levelCache.delete();
                        File matchCache = new File(getCacheDir(), matchCacheFileName);
                        if (matchCache.exists()) matchCache.delete();
                        finish();
                    }
                });
            } else {
                Games.TurnBasedMultiplayer.finishMatch(mGoogleApiClient, match.getMatchId()).setResultCallback(new ResultCallback<TurnBasedMultiplayer.UpdateMatchResult>() {
                    @Override
                    public void onResult(@NonNull TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
                        if (levelCache != null) levelCache.delete();
                        File matchCache = new File(getCacheDir(), matchCacheFileName);
                        if (matchCache.exists()) matchCache.delete();
                        finish();
                    }
                });
            }
        } else {
            retryFinishMatch = true;
            mGoogleApiClient.connect();
        }
    }

    // Game events
    private int animStartDelay = 0;
    private boolean hasLevelPassedShown = false, isGoalViewPlaying = false;

    @Override
    public void OnPlayCompleted(ArithmosGame.GameResult result){
        TextView calc = (TextView) rootLayout.findViewById(R.id.calc_button);
        if (result.result == ArithmosGame.GameResult.SUCCESS) {
            // Animate score
            Animations.CountTo(scoreTextView, prevScore, prevScore + result.score);
            prevScore += result.score;
            if (game.getGoalType() == ArithmosLevel.GOAL_301) {
                TextView three01 = (TextView)rootLayout.findViewById(R.id.three01_textview);
                String value = String.valueOf(game.get301Total()), finalValue = "";
                for (int i = 0; i < value.length(); i++){
                    finalValue += value.charAt(i) + "\n";
                }
                three01.setText(finalValue.trim());
            } else {
                // Update goal list
                GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
                goalView.invalidate();
            }

            // If an evaluate left to right special was use, record and reset
            if (game.isEvalLeftToRight()) {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_OP_ORDER);
                if (count < 1)
                    calc.setVisibility(View.GONE);
                else if (count < 2) {
                    calc.setText("");
                    calc.setCompoundDrawablePadding(0);
                }
                else
                    calc.setText(String.valueOf(count));
                cacheGame();
            }

            cacheLevel();

            // Count the plays, finish after three normally, one turn for 301
            playCount++;
            cacheMatch();
            if ((playCount == 3 || game.getGoalType() == ArithmosLevel.GOAL_301) && !result.noMorePossiblePlays){
                showEndTurnPopup();
            } else {
                showQuickPopup(getResources().getQuantityString(R.plurals.x_moves_remaining, 3 - playCount, 3 - playCount));
            }
        }
        if (game.isEvalLeftToRight()) {
            calc.setBackground(null);
            game.setEvalLeftToRight(false);
        }
    }

    @Override
    public void OnGameOver(ArithmosGame.GameResult result){
        if (result.result == ArithmosGame.GameResult.FORFEIT)
            forfeit = true;
        if (result.noMorePossiblePlays || forfeit)
            showEndMatchPopup(animStartDelay + gameBoard.getAnimDelay() + 200);
        else if (result.isLevelPassed)
            showLevelPassedPopup();
    }

    @Override
    public void OnAchievement(String label){
        Games.Achievements.unlock(mGoogleApiClient, label);
    }

    @Override
    public void OnJewel(){
        gameBase.recordJewels(1);
        cacheGame();
        jewelCount++;
        TextView jewelTextView = (TextView)rootLayout.findViewById(R.id.jewel_count);
        String text = String.valueOf(gameBase.getJewelCount());
        jewelTextView.setText(text);
    }

    @Override
    public void OnBomb(String operation){
        if (!game.isLevelPassed()) {
            final View layout = getLayoutInflater().inflate(R.layout.bomb_popup, null);

            // Animate bomb popup
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layout.setLayoutParams(params);
            layout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            ImageView imageView = (ImageView) layout.findViewById(R.id.image_view);
            switch (operation) {
                case ArithmosGame.ADD:
                    Animations.popIn(rootLayout.findViewById(R.id.slash_add), 100, 0).start();
                    imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_addition));
                    break;
                case ArithmosGame.SUBTRACT:
                    Animations.popIn(rootLayout.findViewById(R.id.slash_sub), 100, 0).start();
                    imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_subtraction));
                    break;
                case ArithmosGame.MULTIPLY:
                    Animations.popIn(rootLayout.findViewById(R.id.slash_mult), 100, 0).start();
                    imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_multiplication));
                    break;
                case ArithmosGame.DIVIDE:
                    Animations.popIn(rootLayout.findViewById(R.id.slash_div), 100, 0).start();
                    imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_division));
                    break;
            }

            rootLayout.addView(layout);
            Animations.slideUp(layout, 200, animStartDelay + gameBoard.getAnimDelay(), rootLayout.getHeight() / 3).start();
            AnimatorSet set = Animations.explodeFade(layout, 200, animStartDelay + 1200 + gameBoard.getAnimDelay());
            animStartDelay += 1400;
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    animStartDelay -= 1400;
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
    }

    @Override
    public void OnCancelBomb(String operation){
        View v = null;
        switch (operation){
            case ArithmosGame.ADD:
                v = rootLayout.findViewById(R.id.slash_add);
                break;
            case ArithmosGame.SUBTRACT:
                v = rootLayout.findViewById(R.id.slash_sub);
                break;
            case ArithmosGame.MULTIPLY:
                v = rootLayout.findViewById(R.id.slash_mult);
                break;
            case ArithmosGame.DIVIDE:
                v = rootLayout.findViewById(R.id.slash_div);
                break;
        }
        if (v != null){
            final View icon = v;
            AnimatorSet set = Animations.fadeOut(icon, 100, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    icon.setVisibility(View.GONE);
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


    // Game specials
    public void SkipGoalNumberClick(View v){
        if (gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP) > 0) {
            int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_SKIP);
            gameBoard.useSkipSpecial();
            TextView skip = (TextView)rootLayout.findViewById(R.id.skip_button);
            if (count < 1)
                skip.setVisibility(View.GONE);
            else if (count < 2) {
                skip.setText("");
                skip.setCompoundDrawablePadding(0);
            }
            else
                skip.setText(String.valueOf(count));
            ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.SUCCESS);
            result.isLevelPassed = game.isLevelPassed();
            OnPlayCompleted(result);
            cacheGame();
        }
    }

    public void CalcButtonClick(View v){
        if (!game.isEvalLeftToRight()) {
            v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_corner_primary_accent, null));
            game.setEvalLeftToRight(true);
        } else {
            v.setBackground(null);
            game.setEvalLeftToRight(false);
        }
    }

    private void BombLongClick(View v){
        gameBoard.setOnDragListener(new BombDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_bomb, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class BombDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    if (gameBoard.placeBomb(event.getX(), event.getY())){
                        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
                        int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_BOMB);
                        if (count < 1)
                            bomb.setVisibility(View.GONE);
                        else if (count < 2) {
                            bomb.setText("");
                            bomb.setCompoundDrawablePadding(0);
                        }
                        else
                            bomb.setText(String.valueOf(count));
                        playCount++;
                        cacheGame();
                        cacheMatch();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void PencilLongClick(View v){
        gameBoard.setOnDragListener(new PencilDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pencil, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class PencilDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    if (gameBoard.isChangeableNumber(event.getX(), event.getY())){
                        // Show number selection popup
                        showNumberSelectionPopup(event.getX(), event.getY());
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void showNumberSelectionPopup(final float x, final float y){
        // Prepare listview to show available numbers
        final ListView numberList = new ListView(context);
        numberList.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.pencil_border_vertical_white_background, null));
        ArithmosLevel level = new ArithmosLevel(context, ArithmosGameBase.getLevelXmlIds(game.getChallengeName())[game.getChallengeLevel()]);
        final ArrayList<Integer> numbers = new ArrayList<>(level.getGridNumbers().length + level.getGridSpecialNumbers().length);
        for (int n : level.getGridNumbers()) {
            if (!numbers.contains(n))
                numbers.add(n);
        }
        for (int n : level.getGridSpecialNumbers()) {
            if (!numbers.contains(n))
                numbers.add(n);
        }
        numberList.setAdapter(new ArrayAdapter<>(context, R.layout.number_list_item, numbers));

        // Setup touch response
        numberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                gameBoard.changeNumber(x, y, String.valueOf(numbers.get(position)));
                TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_CHANGE);
                if (count < 1)
                    pencil.setVisibility(View.GONE);
                else if (count < 2) {
                    pencil.setText("");
                    pencil.setCompoundDrawablePadding(0);
                }
                else
                    pencil.setText(String.valueOf(count));
                cacheGame();
                playCount++;
                cacheMatch();
                rootLayout.removeView(numberList);
            }
        });
        gameBoard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                rootLayout.removeView(numberList);
                gameBoard.setOnTouchListener(null);
                return false;
            }
        });

        // Position listview popup
        int width = 150, height = 300;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
        if (x > gameBoard.getRight() - width)
            params.leftMargin = gameBoard.getLeft() + (int)x - width;
        else
            params.leftMargin = gameBoard.getLeft() + (int)x;
        if (y > gameBoard.getBottom() - height)
            params.topMargin = gameBoard.getTop() + (int)y - height;
        else
            params.topMargin = gameBoard.getTop() + (int)y;
        numberList.setLayoutParams(params);

        // Animate listview popup
        numberList.setVisibility(View.INVISIBLE);
        rootLayout.addView(numberList);
        Animations.slideUp(numberList, 200, 0, rootLayout.getHeight() / 3).start();
    }

    public void OperationButtonClick(View v){
        if (game.getGoalType() != ArithmosLevel.GOAL_301) {
            v.setSelected(!v.isSelected());
            if (v.isSelected()) {
                gameBoard.setOperationPickMode(GameBoard.MANUAL_PICK);
            } else {
                gameBoard.setOperationPickMode(GameBoard.AUTO_PICK);
            }
        }
    }

    public void ZeroButtonClick(View v){
        gameBoard.setOnDragListener(new ZeroDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_zero, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class ZeroDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    if (gameBoard.isChangeableNumber(event.getX(), event.getY())){
                        // Show number selection popup
                        gameBoard.changeNumber(event.getX(), event.getY(), "0");
                        TextView zeroView = (TextView)rootLayout.findViewById(R.id.zero_button);
                        int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_ZERO);
                        if (count < 1)
                            zeroView.setVisibility(View.GONE);
                        else if (count < 2) {
                            zeroView.setText("");
                            zeroView.setCompoundDrawablePadding(0);
                        }
                        else
                            zeroView.setText(String.valueOf(count));
                        cacheGame();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public void AutoRunButtonClick(View v){
        if (gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_AUTO_RUN) > 0) {
            if (gameBoard.useAutoRunSpecial());
            {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_AUTO_RUN);
                TextView skip = (TextView) rootLayout.findViewById(R.id.auto_run_button);
                if (count < -10)
                    skip.setVisibility(View.GONE);
                else if (count < 2) {
                    skip.setText("");
                    skip.setCompoundDrawablePadding(0);
                }
                else
                    skip.setText(String.valueOf(count));
                cacheGame();
            }
        }
    }

    // Turn  match
    private TurnBasedMatch match;
    private boolean forfeit = false;

    private void showStartTurnPopup(){
        final View layout = getLayoutInflater().inflate(R.layout.match_turn_start_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        ImageManager imageManager = ImageManager.create(context);
        ImageView upperPlayerView = (ImageView)layout.findViewById(R.id.upper_player_icon);
        ImageView lowerPlayerView = (ImageView)layout.findViewById(R.id.lower_player_icon);

        if (game.getMessage(game.getCurrentPlayer()) != null && !game.getMessage(game.getCurrentPlayer()).equals("")) {
            imageManager.loadImage(upperPlayerView, match.getParticipant(game.getCurrentPlayer()).getIconImageUri());
            TextView upperComment = (TextView)layout.findViewById(R.id.upper_comment);
            upperComment.setVisibility(View.VISIBLE);
            upperComment.setText(game.getMessage(game.getCurrentPlayer()));
            imageManager.loadImage(lowerPlayerView, match.getParticipant(game.getNextPlayer()).getIconImageUri());
            TextView lowerComment = (TextView)layout.findViewById(R.id.lower_comment);
            lowerComment.setVisibility(View.VISIBLE);
            lowerComment.setText(game.getMessage(game.getNextPlayer()));
        } else if (game.getMessage(game.getNextPlayer()) != null && !game.getMessage(game.getNextPlayer()).equals("")){
            imageManager.loadImage(upperPlayerView, match.getParticipant(game.getNextPlayer()).getIconImageUri());
            TextView upperComment = (TextView)layout.findViewById(R.id.upper_comment);
            upperComment.setVisibility(View.VISIBLE);
            upperComment.setText(game.getMessage(game.getNextPlayer()));
            imageManager.loadImage(lowerPlayerView, match.getParticipant(game.getCurrentPlayer()).getIconImageUri());
            TextView lowerComment = (TextView)layout.findViewById(R.id.lower_comment);
            lowerComment.setText(game.getMessage(game.getCurrentPlayer()));
            lowerComment.setVisibility(View.VISIBLE);
        } else {
            imageManager.loadImage(upperPlayerView, match.getParticipant(game.getCurrentPlayer()).getIconImageUri());
            layout.findViewById(R.id.good_luck_message).setVisibility(View.VISIBLE);
            imageManager.loadImage(lowerPlayerView, match.getParticipant(game.getNextPlayer()).getIconImageUri());
        }


        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 100, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                        if (!match.getLastUpdaterId().equals(game.getCurrentPlayer()) && game.getScore(game.getCurrentPlayer()) == 0)
                            showLevelInfoPopup();
                        else {
                            GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
                            goalView.startGoalAnimation();
                        }
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
    private void initializeMatchGame(){
        ArithmosLevel level = new ArithmosLevel(context, levelXmlId);
        ArrayList<String> playerIds = match.getParticipantIds();
        game = new ArithmosGame(level, playerIds.get(0), playerIds.get(1));
        game.setCurrentPlayer(match.getCreatorId());
    }
    private void initializeTurn(){
        String currentPlayer = match.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient));
        game.setCurrentPlayer(currentPlayer);
        setupGameUi();
        hideLoadingPopup();
    }
    private void matchCompleted(){
        initializeTurn();
        showEndMatchPopup(0);

    }

    // Google Play Games Services
    private GoogleApiClient mGoogleApiClient;

    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;

    @Override
    public void onConnected(Bundle connectionHint) {
        if (retryFinishTurn)
            finishTurn();
        else {
            if (!matchHasLoaded) {
                if (match.getData() != null) {
                    game = new ArithmosGame(match.getData());
                } else {
                    levelXmlId = getIntent().getIntExtra(LEVEL_XML_RES_ID, R.xml.game_level_crazy_eights_6x6);
                    initializeMatchGame();
                    initializeTurn();
                }
            }

            Log.d(LOG_TAG, "Turn starting. Playcount = " + playCount);
            if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_COMPLETE) {
                matchCompleted();
            } else {
                if (playCount == 0) {
                    initializeTurn();
                    showStartTurnPopup();
                } else if (playCount >= 3 || (game.getGoalType() == ArithmosLevel.GOAL_301 && playCount >= 1)) {
                    initializeTurn();
                    showEndTurnPopup();
                } else {
                    initializeTurn();
                }
            }
        }
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
                    RC_SIGN_IN, getString(R.string.gamehelper_sign_in_failed))) {
                mResolvingConnectionFailure = false;
            }
        }

        // Put code here to display the sign-in button
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Attempt to reconnect
        mGoogleApiClient.connect();
    }


    // Utility methods
    private void showQuickPopup(int stringResId){
        showQuickPopup(getString(stringResId));
    }
    private void showQuickPopup(String text){
        final View layout = getLayoutInflater().inflate(R.layout.quick_popup, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layout.setLayoutParams(params);
        layout.setVisibility(View.INVISIBLE);

        TextView textView = (TextView)layout.findViewById(R.id.textView1);
        textView.setText(text);

        rootLayout.addView(layout);
        layout.post(new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = Animations.slideInDownAndOutUp(layout, 3000, animStartDelay + gameBoard.getAnimDelay(), layout.getMeasuredHeight());
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
    }
}
