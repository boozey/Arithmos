package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

import java.io.IOException;
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
    public static final String GAME_BASE_FILE_NAME = "game_base_file_name";
    public static final String MATCH = "match";

    private Context context;
    private RelativeLayout rootLayout;
    private ArithmosGame game;
    private GameBoard gameBoard;
    private TextView scoreTextView;
    private int prevScore = 0, playCount = 0, jewelCount = 0;

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
        gameBoard = (GameBoard)rootLayout.findViewById(R.id.game_board);
        gameBoard.setOnAchievementListener(this);
        gameBoard.setOnPlayCompleted(this);
        gameBoard.setOnGameOverListener(this);
        gameBoard.setOnJewelListener(this);
        gameBoard.setOnBombListener(this);
        gameBoard.setOnCancelBombListener(this);
        gameBase = new ArithmosGameBase();
        rootLayout.findViewById(R.id.bomb_button).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BombLongClick(v);
                return true;
            }
        });
        rootLayout.findViewById(R.id.pencil_button).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PencilLongClick(v);
                return true;
            }
        });

        // Prepare for match game
        Intent data = getIntent();
        if (data.hasExtra(MATCH))
            match = data.getParcelableExtra(MATCH);
        else {
            finish();
        }

        showLoadingPopup(R.string.loading, 200);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

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
                    AnimatorSet set = Animations.slideDown(rootLayout.findViewById(R.id.generic_popup), 150, 0, rootLayout.getHeight() / 3);
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
    private boolean retrySaveGameState = false, gameBaseNeedsDownload = true,
            retryFinishTurn = false, retryFinishMatch = false, matchHasLoaded = false;
    private int levelXmlId;

    private void setupSpecials(){
        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
        int count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_BOMB);
        if (count > 0)
            Animations.popIn(bomb, 100, 100).start();
        if (count > 1)
            bomb.setText(String.valueOf(count));

        TextView calc = (TextView)rootLayout.findViewById(R.id.calc_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_OP_ORDER);
        if (count > 0)
            Animations.popIn(calc, 100, 150).start();
        if (count > 1)
            calc.setText(String.valueOf(count));

        TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_CHANGE);
        if (count > 0)
            Animations.popIn(pencil, 100, 200).start();
        if (count > 1)
            pencil.setText(String.valueOf(count));

        TextView arrow = (TextView)rootLayout.findViewById(R.id.skip_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP);
        if (count > 0)
            Animations.popIn(arrow, 100, 250).start();
        if (count > 1)
            arrow.setText(String.valueOf(count));
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
        String titleText = getString(ArithmosGameBase.getChallengeDisplayNameResId(game.getChallengeName())) + " " +
                getString(ArithmosGameBase.getLevelDisplayNameResIds(game.getChallengeName())[game.getChallengeLevel()]);
        titleView.setText(titleText);

        TextView oneStarView = (TextView)layout.findViewById(R.id.one_star);
        oneStarView.setText(String.valueOf(game.getPointsForStar(1)));

        TextView twoStarView = (TextView)layout.findViewById(R.id.two_star);
        twoStarView.setText(String.valueOf(game.getPointsForStar(1)));

        TextView threeStarView = (TextView)layout.findViewById(R.id.three_star);
        threeStarView.setText(String.valueOf(game.getPointsForStar(1)));

        TextView timeView = (TextView)layout.findViewById(R.id.time_limit_text);
        timeView.setVisibility(View.GONE);

        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideDown(layout, 100, 0, rootLayout.getHeight() / 3);
                // Time is started in OnAnimationEnd
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

    private void loadGameState(String gameFileName){
        Games.Snapshots.open(mGoogleApiClient, gameFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
            @Override
            public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                try {
                    gameBase.loadByteData(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());
                    if (rootLayout.getVisibility() == View.VISIBLE) {
                        setupSpecials();
                        TextView jewelText = (TextView) rootLayout.findViewById(R.id.jewel_count);
                        Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                    }
                    gameBaseNeedsDownload = false;

                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private void saveGameState(){
        if (gameBase.needsSaving()) {
            if (mGoogleApiClient.isConnected() && getIntent().hasExtra(GAME_BASE_FILE_NAME)) {
                retrySaveGameState = false;
                String gameFileName = getIntent().getStringExtra(GAME_BASE_FILE_NAME);
                Games.Snapshots.open(mGoogleApiClient, gameFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                    @Override
                    public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                        String desc = "Arithmos Game Data";
                        writeSnapshot(openSnapshotResult.getSnapshot(), gameBase.getByteData(), desc);
                        Log.i(LOG_TAG, "Game state saved");
                    }
                });
            } else {
                retrySaveGameState = true;
                mGoogleApiClient.connect();
            }
        }
    }

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
        ImageView p1Icon = (ImageView)layout.findViewById(R.id.p1_icon);
        imageManager.loadImage(p1Icon, match.getParticipant(game.PLAYER1).getIconImageUri());

        ImageView p2Icon = (ImageView)layout.findViewById(R.id.p2_icon);
        imageManager.loadImage(p2Icon, match.getParticipant(game.PLAYER2).getIconImageUri());

        if (game.getP1Message() != null){
            TextView textView = (TextView)layout.findViewById(R.id.p1_comment);
            textView.setText(game.getP1Message());
        }
        if (game.getP2Message() != null){
            TextView textView = (TextView)layout.findViewById(R.id.p2_comment);
            textView.setText(game.getP2Message());
        }


        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideDown(layout, 100, 0, rootLayout.getHeight() / 3);
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
                Animations.slideDown(layout, 150, 0, rootLayout.getHeight() / 3).start();
                showEndTurnPopup();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.forfeit_match);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isGameOver = true;
                OnGameOver(result);
                Animations.slideDown(layout, 150, 0, rootLayout.getHeight() / 3).start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 100, 0, rootLayout.getHeight() / 3).start();
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

        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText messageView = (EditText)layout.findViewById(R.id.message);
                game.setMessage(game.getCurrentPlayer(), messageView.getText().toString());
                finishTurn();
                AnimatorSet set = Animations.slideDown(layout, 100, 0, rootLayout.getHeight() / 3);
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

    private void showEndMatchPopup() {
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
        winnerScoreView.setText(String.valueOf(game.getScore(winner)));

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
        loserScoreView.setText(String.valueOf(game.getScore(loser)));

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
                AnimatorSet set = Animations.slideDown(layout, 100, 0, rootLayout.getHeight() / 3);
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

    private void recordAchievements(){
        if (game.isGameOver()) {
            if (game.getGoalType() == ArithmosLevel.GOAL_301 && game.getRunCount() <= 3)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_301_in_3));
            if (gameBase.getJewelCount() >= 1000)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_the_one_percent));
        }
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
                    saveGameState();
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
                        saveGameState();
                        finish();
                    }
                });
            } else {
                Games.TurnBasedMultiplayer.finishMatch(mGoogleApiClient, match.getMatchId()).setResultCallback(new ResultCallback<TurnBasedMultiplayer.UpdateMatchResult>() {
                    @Override
                    public void onResult(@NonNull TurnBasedMultiplayer.UpdateMatchResult updateMatchResult) {
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
    @Override
    public void OnPlayCompleted(ArithmosGame.GameResult result){
        TextView calc = (TextView) rootLayout.findViewById(R.id.calc_button);
        if (result.result == ArithmosGame.GameResult.SUCCESS) {
            // Animate score
            if (game.getGoalType() == ArithmosLevel.GOAL_301) {
                Animations.CountTo(scoreTextView, prevScore, game.get301Total());
                prevScore = game.get301Total();
            } else {
                Animations.CountTo(scoreTextView, prevScore, prevScore + result.score);
                prevScore += result.score;
                ListView upcomingGoalsList = (ListView)rootLayout.findViewById(R.id.upcoming_goal_listview);
                ArrayAdapter<String> adapter = (ArrayAdapter<String>)upcomingGoalsList.getAdapter();
                adapter.notifyDataSetChanged();
            }

            // If an evaluate left to right special was use, record and reset
            if (game.isEvalLeftToRight()) {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_OP_ORDER);
                if (count < 1)
                    calc.setVisibility(View.GONE);
                else if (count < 2)
                    calc.setText("");
                else
                    calc.setText(String.valueOf(count));
            }

            // Count the plays, finish after three
            playCount++;
            if (playCount == 3 && !result.isGameOver){
                showEndTurnPopup();
            }
        }
        if (game.isEvalLeftToRight()) {
            calc.setBackground(null);
            game.setEvalLeftToRight(false);
        }
    }
    private void animateNewGoalNumber(){
        // Animate new goal if it is a type that changes
        if (game.getGoalType() == ArithmosLevel.GOAL_MULT_NUM) {
            final ListView upcomingGoalsList = (ListView)rootLayout.findViewById(R.id.upcoming_goal_listview);
            TextView currentGoal = (TextView)upcomingGoalsList.getChildAt(0).findViewById(R.id.textView1);
            AnimatorSet set = Animations.explodeFade(currentGoal, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // Highlight new goal
                    TextView nextGoal = (TextView)upcomingGoalsList.getChildAt(0).findViewById(R.id.textView1);
                    nextGoal.setAlpha(1f);
                    nextGoal.setScaleX(1f);
                    nextGoal.setScaleY(1f);
                    nextGoal.setTextColor(Color.RED);
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)upcomingGoalsList.getAdapter();
                    adapter.notifyDataSetChanged();
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
    public void OnGameOver(ArithmosGame.GameResult result){
        if (result.result == ArithmosGame.GameResult.FORFEIT)
            forfeit = true;
        showEndMatchPopup();
    }

    @Override
    public void OnAchievement(String label){
        Games.Achievements.unlock(mGoogleApiClient, label);
    }

    @Override
    public void OnJewel(){
        gameBase.recordJewels(1);
        jewelCount++;
        TextView jewelTextView = (TextView)rootLayout.findViewById(R.id.jewel_count);
        String text = String.valueOf(gameBase.getJewelCount());
        jewelTextView.setText(text);
    }

    @Override
    public void OnBomb(String operation){
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
        switch (operation){
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
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
        AnimatorSet set = Animations.explodeFade(layout, 200, 1200);
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
            game.skipGoalNumber();
            TextView skip = (TextView)rootLayout.findViewById(R.id.skip_button);
            if (count < 1)
                skip.setVisibility(View.GONE);
            else if (count < 2)
                skip.setText("");
            else
                skip.setText(String.valueOf(count));
            ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.SUCCESS);
            result.isGameOver = game.isGameOver();
            OnPlayCompleted(result);
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
                        else if (count < 2)
                            bomb.setText("");
                        else
                            bomb.setText(String.valueOf(count));
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
        numberList.setAdapter(new ArrayAdapter<>(context, R.layout.goal_list_item, numbers));

        // Setup touch response
        numberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                gameBoard.changeNumber(x, y, String.valueOf(numbers.get(position)));
                TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_CHANGE);
                if (count < 1)
                    pencil.setVisibility(View.GONE);
                else if (count < 2)
                    pencil.setText("");
                else
                    pencil.setText(String.valueOf(count));
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

    // Turn  match
    private TurnBasedMatch match;
    private boolean forfeit = false;
    private void initializeMatchGame(){
        final ArithmosLevel level = new ArithmosLevel(context, levelXmlId);
        ArrayList<String> playerIds = match.getParticipantIds();
        game = new ArithmosGame(level, playerIds.get(0), playerIds.get(1));
        game.setCurrentPlayer(match.getCreatorId());
    }
    private void initializeTurn(){
        String currentPlayer = match.getParticipantId(Games.Players.getCurrentPlayerId(mGoogleApiClient));
        game.setCurrentPlayer(currentPlayer);
        gameBoard.setGame(game);

        TextView p1NameView = (TextView)rootLayout.findViewById(R.id.p1_name_textview);
        p1NameView.setText(match.getParticipant(game.PLAYER1).getDisplayName());

        TextView p2NameView = (TextView)rootLayout.findViewById(R.id.p2_name_textview);
        p2NameView.setText(match.getParticipant(game.PLAYER2).getDisplayName());

        TextView p1ScoreView = (TextView)rootLayout.findViewById(R.id.p1_score_textview);

        TextView p2ScoreView = (TextView)rootLayout.findViewById(R.id.p2_score_textview);

        if (currentPlayer.equals(game.PLAYER1))
            scoreTextView = p1ScoreView;
        else
            scoreTextView = p2ScoreView;

        if (game.getGoalType() == ArithmosLevel.GOAL_301){
            p1ScoreView.setText(String.valueOf(game.get301Total(game.PLAYER1)));
            p2ScoreView.setText(String.valueOf(game.get301Total(game.PLAYER2)));
            prevScore = game.get301Total();
        } else {
            p1ScoreView.setText(String.valueOf(game.getScore(game.PLAYER1)));
            p2ScoreView.setText(String.valueOf(game.getScore(game.PLAYER2)));

            prevScore = game.getScore(game.getCurrentPlayer());

            final ListView goalList = (ListView) rootLayout.findViewById(R.id.upcoming_goal_listview);
            goalList.setAdapter(new ArrayAdapter<>(context, R.layout.goal_list_item, game.getUpcomingGoals()));
            goalList.setVisibility(View.VISIBLE);

            if (!gameBaseNeedsDownload) {
                setupSpecials();
                TextView jewelText = (TextView) rootLayout.findViewById(R.id.jewel_count);
                Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
            }
        }
        matchHasLoaded = true;
        hideLoadingPopup();
        if (game.getScore(game.getCurrentPlayer()) == 0)
            showLevelInfoPopup();
    }
    private void matchCompleted(){
        initializeTurn();
        showEndMatchPopup();

    }

    // Google Play Games Services
    private GoogleApiClient mGoogleApiClient;

    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;

    @Override
    public void onConnected(Bundle connectionHint) {
        if (retrySaveGameState) saveGameState();
        else if (getIntent().hasExtra(GAME_BASE_FILE_NAME) && gameBaseNeedsDownload)
            loadGameState(getIntent().getStringExtra(GAME_BASE_FILE_NAME));

        if (!matchHasLoaded) {
            if (match.getData() != null) {
                game = new ArithmosGame(match.getData());
                if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_ACTIVE) {
                    initializeTurn();
                    showStartTurnPopup();
                }
                else if (match.getStatus() == TurnBasedMatch.MATCH_STATUS_COMPLETE)
                    matchCompleted();
            } else {
                levelXmlId = getIntent().getIntExtra(LEVEL_XML_RES_ID, R.xml.game_level_crazy_eights_6x6);
                initializeMatchGame();
                initializeTurn();
            }
        }

        if (retryFinishTurn)
            finishTurn();
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
}
