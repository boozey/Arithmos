package com.nakedape.arithmos;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Nathan on 4/4/2016.
 */
public class ArithmosGame {
    private static final String LOG_TAG = "ArithmosGame";

    public static final String ADD = "+";
    public static final String SUBTRACT = "-";
    public static final String MULTIPLY = "*";
    public static final String DIVIDE = "/";
    public static final String UNDEF = "?";
    public static final String[] OPERATIONS = {MULTIPLY, DIVIDE, ADD, SUBTRACT};
    public static String getOperationDisplayString(@NonNull String operation){
        switch (operation){
            case DIVIDE:
                return "\u00F7";
            case MULTIPLY:
                return "\u00D7";
            case SUBTRACT:
                return "\u2212";
            case ADD:
                return "+";
            default:
                return "?";
        }
    }
    public static final String BONUS = "bonus_";

    public static final String SEPARATOR = ":";

    transient public String PLAYER1 = "p1";
    transient public String PLAYER2 = "p2";
    transient public static final String TIE = "tie";

    transient private String[][] gameBoard;
    transient private int p1_301Total = 0, p2_301Total;
    transient private int[] goalNumbers;
    transient private ArrayList<String> goalList, remainingGoals;
    transient private String currentPlayer;
    transient private int p1Points, p2Points, lastScore = 0;
    transient private ArrayList<ArrayList<int[]>> p1Runs, p2Runs;
    transient private ArrayList<int[]> computerFoundRun;
    transient private int lastValue = 0;
    transient private int numStars = 0;
    transient private boolean reached301 = false, isLevelPassed = false;
    transient private String[] p1AvailableOperations, p2AvailableOperations;
    transient private HashMap<String, Integer> p1OpLimitCounts, p2OpLimitCounts;
    transient private boolean evalLtoR = false;
    transient private HashMap<String, Integer> p1BonusCounts, p2BonusCounts;
    transient private String p1Message, p2Message;
    transient private int jewelCount = 0;
    transient private long elapsedTime = 0;
    transient private ArrayList<String> p1GoalsWon, p2GoalsWon;
    transient private int goalIndex = 0;

    public ArithmosGame(){}
    public ArithmosGame(int size, int goalMin, int goalMax, int goalType){
        gameBoard = new String[2*size - 1][2*size - 1];
        this.goalType = goalType;
        switch (goalType){
            case ArithmosLevel.GOAL_SINGLE_NUM:
            case ArithmosLevel.GOAL_MULT_NUM:
                setupRandomBoard(goalMin, goalMax);
                break;
            case ArithmosLevel.GOAL_MULTIPLES:
                break;
            case ArithmosLevel.GOAL_301:
                break;
        }
        currentPlayer = PLAYER1;
        p1Runs = new ArrayList<>();
        p2Runs = new ArrayList<>();
    }
    public ArithmosGame(ArithmosLevel level){
        goalType = level.getGoalType();
        goalNumbers = level.getGoalNumbers();
        challengeName = level.getChallenge();
        challengeLevel = level.getChallengeLevel();
        leaderboardId = level.getLeaderboardId();
        starLevels = level.getStarLevels();
        timeLimit = level.getTimeLimitMillis();
        setupBoard(level.getGridSize(), level.getGridNumbers(), level.getGridSpecialNumbers(), level.getBonuses(), level.getRuns());
        if (goalType != ArithmosLevel.GOAL_301) {
            numGoalsToWin = level.getNumGoalsToWin();
            initializeGoalList(level.getGoalNumbers());
        }
        p1AvailableOperations = OPERATIONS.clone();
        currentPlayer = PLAYER1;
        p1Runs = new ArrayList<>();
        p2Runs = new ArrayList<>();
        p1OpLimitCounts = new HashMap<>();
        p1BonusCounts = new HashMap<>();
    }
    public ArithmosGame(ArithmosLevel level, String p1Id, String p2Id){
        PLAYER1 = p1Id;
        PLAYER2 = p2Id;
        goalType = level.getGoalType();
        goalNumbers = level.getGoalNumbers();
        challengeName = level.getChallenge();
        challengeLevel = level.getChallengeLevel();
        leaderboardId = level.getLeaderboardId();
        starLevels = level.getStarLevels();
        timeLimit = level.getTimeLimitMillis();
        setupBoard(level.getGridSize(), level.getGridNumbers(), level.getGridSpecialNumbers(), level.getBonuses(), level.getRuns());
        if (goalType != ArithmosLevel.GOAL_301) {
            numGoalsToWin = level.getNumGoalsToWin();
            initializeGoalList(level.getGoalNumbers());
        }
        p1AvailableOperations = OPERATIONS.clone();
        p2AvailableOperations = OPERATIONS.clone();
        currentPlayer = PLAYER1;
        p1Runs = new ArrayList<>();
        p2Runs = new ArrayList<>();
        p1OpLimitCounts = new HashMap<>();
        p2OpLimitCounts = new HashMap<>();
        p1BonusCounts = new HashMap<>();
        p2BonusCounts = new HashMap<>();
    }
    public ArithmosGame(byte[] gameData){
        loadGameData(gameData);
    }

    // Initialization
    private void setupBoard(int size, int[] numberList, int[] specialNumberList, String[] bonuses, ArrayList<String[]> runs){
        gameBoard = new String[2*size - 1][2*size - 1];
        Random random = new Random();
        int num;

        // Place pre-defined runs
        if (runs != null){
            for (String[] run : runs){
                findPlaceForRun(run);
            }
        }

        // Add bonuses
        for (String s : bonuses){
            int r, c;
            do {
                r = random.nextInt(gameBoard.length);
                c = random.nextInt(gameBoard.length);
            } while (r % 2 == 0 && c % 2 == 0 || (getPiece(r, c) != null && !getPiece(r, c).equals(UNDEF)));
            gameBoard[r][c] = s + SEPARATOR + UNDEF;
        }

        // Fill board with numbers
        for (int r = 0; r < gameBoard.length; r++)
            for (int c = 0; c <gameBoard[0].length; c++){
                if (r % 2 == 0 && c % 2 == 0) {
                    if (random.nextInt(100) > 80) {
                        num = specialNumberList[random.nextInt(specialNumberList.length)];
                    } else {
                        num = numberList[random.nextInt(numberList.length)];
                    }
                    if (gameBoard[r][c] == null) gameBoard[r][c] = String.valueOf(num) + SEPARATOR + UNDEF;
                } else if (gameBoard[r][c] == null){
                    gameBoard[r][c] = UNDEF + SEPARATOR + UNDEF;
                }
            }

    }
    private void findPlaceForRun(String[] run){
        Random random = new Random();
        char[][] orders = {{'v', 'h', 'l', 'r'}, {'v', 'h', 'r', 'l'}, {'v', 'l', 'h', 'r'},
                {'v', 'l', 'r', 'h'}, {'v', 'r', 'h', 'l'}, {'v', 'r', 'l', 'h'},
                {'h', 'l', 'v', 'r'}, {'h', 'l', 'r', 'v'}, {'h', 'r', 'v', 'l'},
                {'h', 'r', 'l', 'v'}, {'h', 'v', 'l', 'r'}, {'h', 'v', 'r', 'l'},
                {'l', 'v', 'h', 'r'}, {'l', 'v', 'r', 'h'}, {'l', 'r', 'v', 'h'},
                {'l', 'r', 'h', 'v'}, {'l', 'h', 'v', 'r'}, {'l', 'h', 'r', 'v'},
                {'r', 'h', 'v', 'l'}, {'r', 'h', 'l', 'v'}, {'r', 'v', 'l', 'h'},
                {'r', 'v', 'h', 'l'}, {'r', 'l', 'v', 'h'}, {'r', 'l', 'h', 'v'}};
        char[] order = orders[random.nextInt(orders.length)];

        boolean placed = false;
        for (int r = 0; r < gameBoard.length; r += 2)
            for (int c = 0; c < gameBoard[r].length; c += 2) {
                if (getPiece(r, c) == null){
                    int i = 0;
                    do {
                        switch (order[i]){
                            case 'h':
                                placed = checkHoriz(r, c, run);
                                break;
                            case 'v':
                                placed = checkVert(r, c, run);
                                break;
                            case 'r':
                                placed = checkDiagRight(r, c, run);
                                break;
                            case 'l':
                                placed = checkDiagLeft(r, c, run);
                                break;
                        }
                        i++;
                    } while (i < order.length && !placed);
                    if (placed) return;
                }
            }
    }
    private boolean checkHoriz(int r, int c, String[] run){
        int i = 0;
        do {
            i++;
        } while (c + i < gameBoard[0].length && getPiece(r, c + i) == null && i < run.length);
        if (i == run.length) {
            placeHoriz(run, r, c);
            return true;
        }
        else return false;
    }
    private void placeHoriz(String[] run, int r, int c){
        for (int i = 0; i < run.length && c + i < gameBoard[r].length; i++)
            gameBoard[r][c + i] = run[i] + SEPARATOR + UNDEF;
    }
    private boolean checkVert(int r, int c, String[] run){
        int i = 0;
        do {
            i++;
        } while (r + i < gameBoard.length && getPiece(r + i, c) == null && i < run.length);
        if (i == run.length) {
            placeVert(run, r, c);
            return true;
        }
        else return false;
    }
    private void placeVert(String[] run, int r, int c){
        for (int i = 0; i < run.length && r + i < gameBoard.length; i++)
            gameBoard[r + i][c] = run[i] + SEPARATOR + UNDEF;
    }
    private boolean checkDiagRight(int r, int c, String[] run){
        int i = 0;
        do {
            i++;
        } while (r + i < gameBoard.length && c + i < gameBoard[0].length && getPiece(r + i, c + i) == null && i < run.length);
        if (i == run.length) {
            placeDiagRight(run, r, c);
            return true;
        }
        else return false;
    }
    private void placeDiagRight(String[] run, int r, int c){
        for (int i = 0; i < run.length && r + i < gameBoard.length && c + i < gameBoard[0].length; i++)
            gameBoard[r + i][c + i] = run[i] + SEPARATOR + UNDEF;
    }
    private boolean checkDiagLeft(int r, int c, String[] run){
        int i = 0;
        do {
            i++;
        } while (r + i < gameBoard.length && c - i >= 0 && getPiece(r + i, c - i) == null && i < run.length);
        if (i == run.length) {
            placeDiagLeft(run, r, c);
            return true;
        }
        else return false;
    }
    private void placeDiagLeft(String[] run, int r, int c){
        for (int i = 0; i < run.length && r + i < gameBoard.length && c - i >= 0; i++)
            gameBoard[r + i][c - i] = run[i] + SEPARATOR + UNDEF;
    }

    private void setupRandomBoard(int goalMin, int goalMax){
        Random r = new Random();
        goalNumbers = new int[10];
        for (int i = 0; i < goalNumbers.length; i++){
            goalNumbers[i] = r.nextInt(goalMax - goalMin) + goalMin;
        }
        initializeGoalList(goalNumbers);

        int num;
        for (int i = 0; i < gameBoard.length; i++)
            for (int j = 0; j < gameBoard[i].length; j++){
                if (r.nextInt(100) > 80){
                    num = r.nextInt(40) + 1;
                } else {
                    num = r.nextInt(10);
                }
                gameBoard[i][j] = i % 2 == 0 && j % 2 == 0 ? String.valueOf(num + 1) + SEPARATOR + UNDEF : UNDEF + SEPARATOR + UNDEF;
            }
    }
    public String[][] getGameBoard(){
        return gameBoard;
    }
    public ArrayList<ArrayList<int[]>> getP1Runs() { return p1Runs; }
    public ArrayList<ArrayList<int[]>> getP2Runs() { return p2Runs; }

    // Level info
    transient private String leaderboardId;
    transient private String challengeName;
    transient private int challengeLevel;
    transient private int[] starLevels;
    transient private long timeLimit = -1;
    transient private int goalType;
    transient private int numGoalsToWin;

    public String getChallengeName() { return challengeName; }
    public int getChallengeLevel() { return challengeLevel; }
    public String getLeaderboardId() {return leaderboardId;}
    public long getTimeLimit(){
        return timeLimit;
    }
    public boolean hasTimeLimit() {
        return timeLimit > 0;
    }
    public int getNumGoalsToWin() {return numGoalsToWin;}

    // Goal
    public int getGoalType(){
        return goalType;
    }
    public int getCurrentGoal(){
        if (remainingGoals != null && remainingGoals.size() > 0)
            return getGoalAt(goalIndex);
        else return -1;
    }
    public int getGoalAt(int index){
        if (remainingGoals != null && index < remainingGoals.size() && index >= 0)
            return Integer.valueOf(remainingGoals.get(index));
        else return -1;
    }
    public void nextGoalIndex(){
        goalIndex = (++goalIndex) % remainingGoals.size();
    }
    public ArrayList<String> getRemainingGoals() {
        return remainingGoals;
    }
    public ArrayList<String> getGoalList() {return goalList;}
    public ArrayList<String> getGoalsWon(String player){
        if (player.equals(PLAYER1))
            return p1GoalsWon;
        else
            return p2GoalsWon;
    }
    private boolean removeTopFromGoalList(){
        if (goalType == ArithmosLevel.GOAL_MULT_NUM) {
            remainingGoals.remove(0);
            return remainingGoals.size() > 0;
        }
        return true;
    }
    private boolean removeFromRemainingGoals(int value){
        if (remainingGoals != null && remainingGoals.size() > 0){
            remainingGoals.remove(String.valueOf(value));
            return remainingGoals.size() > 0;
        }
        return true;
    }
    private void recordGoal(int value){
        if (currentPlayer.equals(PLAYER1)) {
            if (!p1GoalsWon.contains(String.valueOf(value))) {
                p1GoalsWon.add(String.valueOf(value));
            }
        }
        else if (!p2GoalsWon.contains(String.valueOf(value)))
            p2GoalsWon.add(String.valueOf(value));
    }
    private int remainingGoalCount(){
        return remainingGoals.size() + goalList.size();
    }
    private void initializeGoalList(int[] goalNumbers){
        remainingGoals = new ArrayList<>(goalNumbers.length);
        goalList = new ArrayList<>(goalNumbers.length);
        for (int x : goalNumbers){
            remainingGoals.add(String.valueOf(x));
            goalList.add(String.valueOf(x));
        }
        p1GoalsWon = new ArrayList<>();
        p2GoalsWon = new ArrayList<>();
    }
    public int get301Total() {
        if (currentPlayer.equals(PLAYER1))
            return p1_301Total;
        else
            return p2_301Total;
    }
    private void addTo301Total(int value){
        if (currentPlayer.equals(PLAYER1))
            p1_301Total += value;
        else
            p2_301Total += value;
    }
    public GameResult skipGoalNumber(){
        GameResult result = new GameResult(GameResult.SUCCESS);
        if (!removeTopFromGoalList()) result.isLevelPassed = true;
        // Update counters for operation limits and reset when appropriate
        HashMap<String, Integer> opLimitCounts;
        if (currentPlayer.equals(PLAYER1))
            opLimitCounts = p1OpLimitCounts;
        else
            opLimitCounts = p2OpLimitCounts;
        for (String op : ArithmosGame.OPERATIONS){
            if (opLimitCounts.containsKey(op)){
                int count = opLimitCounts.get(op);
                if (--count == 0){
                    opLimitCounts.remove(op);
                    replaceOperation(op);
                    result.opsReplaced.add(op);
                } else
                    opLimitCounts.put(op, count);
            }
        }
        return result;
    }

    // Game info
    public void setCurrentPlayer(String playerId){
        currentPlayer = playerId;
    }
    public String getCurrentPlayer(){
        return currentPlayer;
    }
    public String getNextPlayer(){
        if (currentPlayer.equals(PLAYER1))
            return PLAYER2;
        else
            return PLAYER1;
    }
    public String[] availableOperations(){
        if (currentPlayer.equals(PLAYER1))
            return p1AvailableOperations;
        else
            return p2AvailableOperations;
    }
    public void removeOperation(String operation){
        if (currentPlayer.equals(PLAYER1)) {
            if (!p1OpLimitCounts.containsKey(operation)) {
                String[] temp = new String[p1AvailableOperations.length - 1];
                for (int i = 0, j = 0; i < p1AvailableOperations.length; i++) {
                    if (!p1AvailableOperations[i].equals(operation))
                        temp[j++] = p1AvailableOperations[i];
                }
                p1AvailableOperations = temp;
            }
            p1OpLimitCounts.put(operation, 3);
        } else {
            if (!p2OpLimitCounts.containsKey(operation)) {
                String[] temp = new String[p1AvailableOperations.length - 1];
                for (int i = 0, j = 0; i < p1AvailableOperations.length; i++) {
                    if (!p1AvailableOperations[i].equals(operation))
                        temp[j++] = p1AvailableOperations[i];
                }
                p1AvailableOperations = temp;
            }
            p1OpLimitCounts.put(operation, 3);
        }
    }
    public void replaceOperation(String operation){
        ArrayList<String> opsArrayList;

        if (currentPlayer.equals(PLAYER1)) {
            opsArrayList = new ArrayList<>(p1AvailableOperations.length + 1);
            for (String s : p1AvailableOperations)
                opsArrayList.add(s);
            opsArrayList.add(operation);
        } else {
            opsArrayList = new ArrayList<>(p2AvailableOperations.length + 1);
            for (String s : p2AvailableOperations)
                opsArrayList.add(s);
            opsArrayList.add(operation);
        }

        String[] temp = new String[opsArrayList.size()];
        int i = 0;
        if (opsArrayList.contains(MULTIPLY))
            temp[i++] = MULTIPLY;
        if (opsArrayList.contains(DIVIDE))
            temp[i++] = DIVIDE;
        if (opsArrayList.contains(ADD))
            temp[i++] = ADD;
        if (opsArrayList.contains(SUBTRACT))
            temp[i] = SUBTRACT;

        if (currentPlayer.equals(PLAYER1))
            p1AvailableOperations = temp;
        else
            p2AvailableOperations = temp;
    }
    public int getTotalNumberTiles(){
        return ((gameBoard.length + 1) / 2) * ((gameBoard.length + 1) / 2);
    }
    public int getNumTilesPlayed(){
        int total = 0;
        for (int r = 0; r < gameBoard.length; r += 2)
            for (int c = 0; c < gameBoard[0].length; c += 2){
                if (isPiecePlayed(new int[]{r, c})) total++;
            }
        return total;
    }
    public int getNumTilesRemaining(){
        return getTotalNumberTiles() - getNumTilesPlayed();
    }
    public int getBonusCount(String name){
        if (currentPlayer.equals(PLAYER1)) {
            if (p1BonusCounts.containsKey(name))
                return p1BonusCounts.get(name);
            else return 0;
        } else {
            if (p2BonusCounts.containsKey(name))
                return p2BonusCounts.get(name);
            else return 0;
        }
    }
    public boolean isLevelPassed(){
        if (!isLevelPassed) {
            if (getGoalType() == ArithmosLevel.GOAL_301)
                isLevelPassed = hasReached301();
            else {
                isLevelPassed = p1GoalsWon.size() + p2GoalsWon.size() >= numGoalsToWin;
                Log.d(LOG_TAG, "numGoalsToWin = " + numGoalsToWin);
                Log.d(LOG_TAG, "goalsWon = " + (p1GoalsWon.size() + p2GoalsWon.size()));
            }
        }
        return isLevelPassed;
    }

    // Two player
    public void setMessage(String playerId, String message){
        if (playerId.equals(PLAYER1))
            p1Message = message;
        else if (playerId.equals(PLAYER2))
            p2Message = message;
    }
    public String getP1Message() {return p1Message;}
    public String getP2Message(){ return p2Message;}
    public String getMessage(String playerId){
        if (playerId.equals(PLAYER1))
            return p1Message;
        else return p2Message;
    }
    public int get301Total(String player){
        if (player.equals(PLAYER1))
            return p1_301Total;
        else
            return p2_301Total;
    }
    public String getWinner(){
        if (getScore(PLAYER1) > getScore(PLAYER2))
            return PLAYER1;
        else if (getScore(PLAYER1) < getScore(PLAYER2))
            return PLAYER2;
        else if (p1BonusCounts.size() > p2BonusCounts.size())
            return PLAYER1;
        else if (p1BonusCounts.size() < p2BonusCounts.size())
            return PLAYER2;
        else return TIE;
    }
    public int getBonusCount(String player, String bonus){
        if (player.equals(PLAYER1)){
            if (p1BonusCounts.containsKey(bonus))
                return p1BonusCounts.get(bonus);
            else return 0;
        } else if (player.equals(PLAYER2)){
            if (p2BonusCounts.containsKey(bonus))
                return p2BonusCounts.get(bonus);
            else return 0;
        } else return 0;
    }
    public int getRunCount(){
        return getRunCount(currentPlayer);
    }
    public int getRunCount(String playerId){
        if (playerId.equals(PLAYER1))
            return p1Runs.size();
        else
            return p2Runs.size();
    }
    public int getOpLimitCount(String operation){
        if (currentPlayer.equals(PLAYER1)) {
            if (p1OpLimitCounts.containsKey(operation))
                return p1OpLimitCounts.get(operation);
        } else {
            if (p2OpLimitCounts.containsKey(operation))
                return p2OpLimitCounts.get(operation);
        }
        return 0;
    }
    public int getLastValue(){
        return lastValue;
    }
    public int getJewelCount() { return jewelCount;}
    public void resetJewelCount() { jewelCount = 0;}
    public long getElapasedTime(){
        return elapsedTime;
    }

    // Run checking
    public GameResult checkSelection(ArrayList<int[]> run, ArrayList<String> operations){
        GameResult result = new GameResult(GameResult.FAILURE);
        if ((run.size() - 1) / 2 != operations.size()) return result;

        String exp = "";
        int i = 0;
        for (int[] tile : run){
            String[] a = gameBoard[tile[0]][tile[1]].split(SEPARATOR);
            if (a[1].equals(currentPlayer)) return result;
            if (tile[0] % 2 == 0 && tile[1] % 2 == 0)
                exp += a[0] + " ";
            else
                exp += operations.get(i++) + " ";
        }
        if (meetsGoal(exp, true)){
            result.result = GameResult.SUCCESS;
            result.value = lastValue;

            // Find bonuses in selection
            ArrayList<String> bonusNames = new ArrayList<>(1);
            for (int[] a : run)
                if (isBonus(a)) {
                    result.bonusLocations.add(a);
                    String bonusName = getPiece(a[0], a[1]);
                    bonusNames.add(bonusName);
                    incrementBonusCount(bonusName);
                }

            // Find trapped bonuses
            ArrayList<int[]> bonusList = findTrappedBonuses(run);
            for (int[] b : bonusList){
                if (isBonus(b) && !isOpLockBonus(b) && !isBombBonus(b) && !result.bonusLocations.contains(b)) {
                    result.bonusLocations.add(b);
                    String bonusName = getPiece(b[0], b[1]);
                    bonusNames.add(bonusName);
                    incrementBonusCount(bonusName);
                }
            }

            // Update game board
            String[] baseExp = new String[run.size()];
            i = 0;
            for (int j = 0; j < run.size(); j++ ){
                int[] tile = run.get(j);
                if (tile[0] % 2 != 0 || tile[1] % 2 != 0){
                    baseExp[j] = operations.get(i++);
                    gameBoard[tile[0]][tile[1]] = baseExp[j] + SEPARATOR + currentPlayer;
                } else {
                    String[] a = gameBoard[tile[0]][tile[1]].split(SEPARATOR);
                    baseExp[j] = a[0];
                    gameBoard[tile[0]][tile[1]] = a[0] + SEPARATOR + currentPlayer;
                }
            }

            // Determine score and number of stars earned
            int currentScore = getScore(currentPlayer), numStarsEarned = 0;
            lastScore = scoreSelection(baseExp, bonusNames);
            result.score = lastScore;
            if (currentScore + lastScore >= starLevels[2])
                numStarsEarned = 3;
            else if (currentScore + lastScore >= starLevels[1])
                numStarsEarned = 2;
            else if (currentScore + lastScore >= starLevels[0])
                numStarsEarned = 1;
            if (numStarsEarned > numStars){
                numStars = numStarsEarned;
                result.numStars = numStarsEarned;
            }

            // Update counters for operation limits and reset when appropriate
            HashMap<String, Integer> opLimitCounts;
            if (currentPlayer.equals(PLAYER1))
                opLimitCounts = p1OpLimitCounts;
            else
                opLimitCounts = p2OpLimitCounts;
            for (String op : ArithmosGame.OPERATIONS){
                if (opLimitCounts.containsKey(op)){
                    int count = opLimitCounts.get(op);
                    if (--count == 0){
                        opLimitCounts.remove(op);
                        replaceOperation(op);
                        result.opsReplaced.add(op);
                    } else
                        opLimitCounts.put(op, count);
                }
            }

            if (currentPlayer.equals(PLAYER1)) {
                p1Points += lastScore;
                p1Runs.add(run);
            } else {
                p2Points += lastScore;
                p2Runs.add(run);
            }
            result.isLevelPassed = isLevelPassed();
            return result;
        } else {
            return result;
        }
    }
    public GameResult checkAutoFillSelection(ArrayList<int[]> run, boolean record){
        GameResult result = new GameResult(GameResult.FAILURE);
        computerFoundRun = null;
        // Make sure first and last arrays correspond to number tiles
        int[] temp = run.get(0);
        if (temp[0] % 2 != 0 || temp[1] % 2 != 0) run.remove(0);
        temp = run.get(run.size() - 1);
        if (temp[0] % 2 != 0 || temp[1] % 2 != 0) run.remove(run.size() - 1);
        if (run.size() < 3) return result;


        //
        // Create and check all possible expressions
        //

        // Total number of combinations is the number of OPERATIONS to the power of the number of
        // places an operation can be placed
        String[] availableOperations;
        if (currentPlayer.equals(PLAYER1))
            availableOperations = p1AvailableOperations;
        else
            availableOperations = p2AvailableOperations;

        int numCombinations = (int)Math.pow(availableOperations.length, run.size() - 1);
        // Array of values selected with space to insert OPERATIONS in between each pair of values
        String[] baseExp = new String[run.size()];
        // One counter for each location an operation can be placed.  Num OPERATIONS is one less
        // than the number of values selected
        int[] counters = new int[(run.size() - 1) / 2];

        // Loop until all possible combinations have been checked
        for (int count = 0; count < numCombinations; count++) {
            // Loop through selected values and insert next combination of OPERATIONS
            for (int i = 0, j = 0; i < baseExp.length; i += 2) {
                int[] a = run.get(i);
                String[] s = gameBoard[a[0]][a[1]].split(SEPARATOR);
                // return false if tile has already been used
                if (s[1].equals(getCurrentPlayer())) return result;
                baseExp[i] = s[0];
                // Fill position with operation
                if (j < counters.length) {
                    baseExp[i + 1] = availableOperations[counters[j]];

                    // Replace operation if it is a lock bonus
                    if (isOpLockBonus(run.get(i + 1))) {
                        a = run.get(i + 1);
                        s = gameBoard[a[0]][a[1]].split(SEPARATOR);
                        baseExp[i + 1] = s[0].replace(ArithmosLevel.BONUS_OP_LOCK, "");
                    }
                    j++;
                }
            }

            // Build expression string to check from array
            String exp = "";
            for (String s : baseExp)
                exp += s + " ";

            // Check if expression evaluates to the currentGoal and return true if it does
            if (meetsGoal(exp, record)){
                result.value = lastValue;
                result.result = GameResult.SUCCESS;
                if (record) {

                    // Find bonuses
                    ArrayList<String> bonusNames = new ArrayList<>(1);
                    for (int[] a : run)
                        if (isBonus(a)) {
                            result.bonusLocations.add(a);
                            String bonusName = getPiece(a[0], a[1]);
                            bonusNames.add(bonusName);
                            incrementBonusCount(bonusName);
                        }

                    // Find trapped bonuses
                    ArrayList<int[]> bonusList = findTrappedBonuses(run);
                    for (int[] b : bonusList){
                        if (isBonus(b) && !isOpLockBonus(b) && !isBombBonus(b) && !result.bonusLocations.contains(b)) {
                            result.bonusLocations.add(b);
                            String bonusName = getPiece(b[0], b[1]);
                            bonusNames.add(bonusName);
                            incrementBonusCount(bonusName);
                        }
                    }

                    //Update game board
                    for (int i = 0; i < run.size(); i++) {
                        int[] l = run.get(i);
                        gameBoard[l[0]][l[1]] = baseExp[i] + SEPARATOR + currentPlayer;
                    }

                    // Determine score and number of stars earned
                    int currentScore = getScore(currentPlayer), numStarsEarned = 0;
                    lastScore = scoreSelection(baseExp, bonusNames);
                    result.score = lastScore;
                    if (currentScore + lastScore >= starLevels[2])
                        numStarsEarned = 3;
                    else if (currentScore + lastScore >= starLevels[1])
                        numStarsEarned = 2;
                    else if (currentScore + lastScore >= starLevels[0])
                        numStarsEarned = 1;
                    if (numStarsEarned > numStars){
                        numStars = numStarsEarned;
                        result.numStars = numStarsEarned;
                    }

                    // Update counters for operation limits and reset when appropriate
                    HashMap<String, Integer> opLimitCounts;
                    if (currentPlayer.equals(PLAYER1))
                        opLimitCounts = p1OpLimitCounts;
                    else
                        opLimitCounts = p2OpLimitCounts;
                    for (String op : ArithmosGame.OPERATIONS){
                        if (opLimitCounts.containsKey(op)){
                            int playCount = opLimitCounts.get(op);
                            if (--playCount <= 0){
                                opLimitCounts.remove(op);
                                replaceOperation(op);
                                result.opsReplaced.add(op);
                            } else
                                opLimitCounts.put(op, playCount);
                        }
                    }

                    // Record score for the current player
                    if (currentPlayer.equals(PLAYER1)) {
                        p1Points += lastScore;
                        p1Runs.add(run);
                    } else {
                        p2Points += lastScore;
                        p2Runs.add(run);
                    }
                    result.isLevelPassed = isLevelPassed();
                    return result;
                } else{
                    computerFoundRun = new ArrayList<>(run.size());
                    computerFoundRun.addAll(run);
                }
                return result;
            }

            // Increment counters to try next combination of OPERATIONS
            // Counters increment modulo the number of OPERATIONS
            int index = 0;
            boolean carry = false;
            do {
                counters[index]++;
                if (counters[index] == availableOperations.length) {
                    counters[index] = 0;
                    carry = true;
                } else {
                    carry = false;
                }
                index++;
            } while (carry && index < counters.length);
        } // End main loop

        // If this statement is reached, all possible combinations of OPERATIONS failed
        return result;
    }
    private ArrayList<int[]> findTrappedBonuses(ArrayList<int[]> run){
        ArrayList<int[]> bonusLocations = new ArrayList<>(run.size());
        for (int[] a : run){
            int r = a[0], c = a[1];
            if (isPiecePlayed(r + 2, c) && isBonus(r + 1, c)) {
                int[] bonus = new int[] {r + 1, c};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r - 2, c) && isBonus(r - 1, c)){
                int[] bonus = new int[] {r - 1, c};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r, c + 2) && isBonus(r, c + 1)){
                int[] bonus = new int[] {r, c + 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r, c - 2) && isBonus(r, c - 1)){
                int[] bonus = new int[] {r, c - 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r - 2, c - 2) && isBonus(r - 1, c - 1)){
                int[] bonus = new int[] {r - 1, c - 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r + 2, c - 2) && isBonus(r + 1, c - 1)){
                int[] bonus = new int[] {r + 1, c - 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r - 2, c + 2) && isBonus(r - 1, c + 1)){
                int[] bonus = new int[] {r - 1, c + 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
            if (isPiecePlayed(r + 2, c + 2) && isBonus(r + 1, c + 1)){
                int[] bonus = new int[] {r + 1, c + 1};
                if (!bonusLocations.contains(bonus)) {
                    bonusLocations.add(bonus);
                }
            }
        }

        return bonusLocations;
    }
    private boolean meetsGoal(String exp, boolean record){
        double value;
        boolean meets;
        if (evalLtoR) value = evalLeftToRight(exp);
        else value = eval(exp);
        if (value != (int) value) return false;
        switch (goalType){
            case ArithmosLevel.GOAL_SINGLE_NUM:
                meets = getCurrentGoal() == (int)value;
                if (record && meets) {
                    removeFromRemainingGoals((int)value);
                    recordGoal((int)value);
                    lastValue = (int)value;
                }
                return meets;
            case ArithmosLevel.GOAL_MULT_NUM:
                if (getCurrentGoal() < 0) return true;
                else {
                    meets = remainingGoals.contains(String.valueOf((int)value));
                    if (meets && record) {
                        removeFromRemainingGoals((int)value);
                        recordGoal((int)value);
                        lastValue = (int)value;
                    }
                    return meets;
                }
            case ArithmosLevel.GOAL_301:
                if (value < 0) return false;
                if (get301Total() + (int)value < 301){
                    if (record) {
                        addTo301Total((int) value);
                        lastValue = (int) value;
                    }
                    return true;
                } else if (get301Total() + value == 301){
                    if (record) {
                        addTo301Total((int) value);
                        lastValue = (int) value;
                        reached301 = true;
                    }
                    return true;
                } else
                    return false;
            default:
                return false;
        }
    }
    public boolean hasReached301() {return reached301;}
    public static double eval(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            // Grammar:
            // expression = term | expression `+` term | expression `-` term
            // term = factor | term `*` factor | term `/` factor
            // factor = `+` factor | `-` factor | `(` expression `)`
            //        | number | functionName factor | factor `^` factor

            double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) x /= parseFactor(); // division
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor(); // unary plus
                if (eat('-')) return -parseFactor(); // unary minus

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') { // functions
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    x = parseFactor();
                    if (func.equals("sqrt")) x = Math.sqrt(x);
                    else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                    else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                    else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                    else throw new RuntimeException("Unknown function: " + func);
                } else {
                    throw new RuntimeException("Unexpected: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation

                return x;
            }
        }.parse();
    }
    public void setEvalLeftToRight(boolean isLeftToRight){
        evalLtoR = isLeftToRight;
    }
    public boolean isEvalLeftToRight(){
        return evalLtoR;
    }
    public static double evalLeftToRight(String exp){
        exp = exp.trim();
        String[] strings = exp.split(" ");
        double result = Double.valueOf(strings[0]);
        for (int i = 1; i < strings.length - 1; i += 2) {
            double value = Double.valueOf(strings[i + 1]);
            switch (strings[i]) {
                case ADD:
                    result += value;
                    break;
                case SUBTRACT:
                    result -= value;
                    break;
                case MULTIPLY:
                    result *= value;
                    break;
                case DIVIDE:
                    result /= value;
                    break;
            }
        }
        return result;
    }
    private void incrementBonusCount(String bonusName){
        int count = 1;
        if (bonusName.contains(ArithmosLevel.BONUS_OP_LOCK))
            bonusName = ArithmosLevel.BONUS_OP_LOCK;
        if (currentPlayer.equals(PLAYER1)) {
            if (p1BonusCounts.containsKey(bonusName)) {
                count += p1BonusCounts.get(bonusName);
            }
            p1BonusCounts.put(bonusName, count);
        } else {
            if (p2BonusCounts.containsKey(bonusName)) {
                count += p2BonusCounts.get(bonusName);
            }
            p2BonusCounts.put(bonusName, count);
        }
    }
    public ArrayList<int[]> getComputerFoundRun(){ return computerFoundRun;}
    public GameResult threadAutoCheckSelection(ArrayList<int[]> run){
        GameResult result = new GameResult(GameResult.FAILURE);
        computerFoundRun = null;
        // Make sure first and last arrays correspond to number tiles
        int[] temp = run.get(0);
        if (temp[0] % 2 != 0 || temp[1] % 2 != 0) run.remove(0);
        temp = run.get(run.size() - 1);
        if (temp[0] % 2 != 0 || temp[1] % 2 != 0) run.remove(run.size() - 1);
        if (run.size() < 3) return result;


        //
        // Create and check all possible expressions
        //

        // Total number of combinations is the number of OPERATIONS to the power of the number of
        // places an operation can be placed
        String[] availableOperations;
        if (currentPlayer.equals(PLAYER1))
            availableOperations = p1AvailableOperations;
        else
            availableOperations = p2AvailableOperations;

        int numCombinations = (int)Math.pow(availableOperations.length, run.size() - 1);
        // Array of values selected with space to insert OPERATIONS in between each pair of values
        String[] baseExp = new String[run.size()];
        // One counter for each location an operation can be placed.  Num OPERATIONS is one less
        // than the number of values selected
        int[] counters = new int[(run.size() - 1) / 2];

        // Loop until all possible combinations have been checked
        for (int count = 0; count < numCombinations; count++) {
            // Loop through selected values and insert next combination of OPERATIONS
            for (int i = 0, j = 0; i < baseExp.length; i += 2) {
                int[] a = run.get(i);
                String[] s = gameBoard[a[0]][a[1]].split(SEPARATOR);
                // return false if tile has already been used
                if (s[1].equals(getCurrentPlayer())) return result;
                baseExp[i] = s[0];
                // Fill position with operation
                if (j < counters.length) {
                    baseExp[i + 1] = availableOperations[counters[j]];

                    // Replace operation if it is a lock bonus
                    if (isOpLockBonus(run.get(i + 1))) {
                        a = run.get(i + 1);
                        s = gameBoard[a[0]][a[1]].split(SEPARATOR);
                        baseExp[i + 1] = s[0].replace(ArithmosLevel.BONUS_OP_LOCK, "");
                    }
                    j++;
                }
            }

            // Build expression string to check from array
            String exp = "";
            for (String s : baseExp)
                exp += s + " ";

            // Check if expression evaluates to the currentGoal and return true if it does
            if (meetGoalAutoCheck(exp)) {
                result.result = GameResult.SUCCESS;
                computerFoundRun = new ArrayList<>(run.size());
                computerFoundRun.addAll(run);
                return result;
            }

            // Increment counters to try next combination of OPERATIONS
            // Counters increment modulo the number of OPERATIONS
            int index = 0;
            boolean carry = false;
            do {
                counters[index]++;
                if (counters[index] == availableOperations.length) {
                    counters[index] = 0;
                    carry = true;
                } else {
                    carry = false;
                }
                index++;
            } while (carry && index < counters.length);
        } // End main loop

        // If this statement is reached, all possible combinations of OPERATIONS failed
        return result;
    }
    public boolean meetGoalAutoCheck(String exp){
        double value;
        if (evalLtoR) value = evalLeftToRight(exp);
        else value = eval(exp);
        if (value != (int) value) return false;
        switch (goalType){
            case ArithmosLevel.GOAL_SINGLE_NUM:
            case ArithmosLevel.GOAL_MULT_NUM:
                if (getCurrentGoal() < 0) return false;
                else {
                    return remainingGoals.contains(String.valueOf((int)value));

                }
            case ArithmosLevel.GOAL_301:
                if (value < 0) return false;
                return get301Total() + (int)value <= 301;
            default:
                return false;
        }
    }

    // Scoring
    private int scoreSelection(String[] expression, ArrayList<String> bonuses){
        int score = 0, add_subCount = 0, mult_divCount = 0;
        for (String s: expression){
            switch (s){
                case ADD:
                    add_subCount++;
                    break;
                case SUBTRACT:
                    add_subCount++;
                    break;
                case MULTIPLY:
                    mult_divCount++;
                    break;
                case DIVIDE:
                    mult_divCount++;
                    break;
                default:
                    int value = Integer.valueOf(s);
                    if (value <= 5) score += 5;
                    else if (value <= 10) score += 10;
                    else if (value <= 20) score += 20;
                    else if (value <= 50) score += 50;
                    else score += 100;
                    break;
            }
        }
        score *= Math.max(1, (expression.length - 1) / 2);
        for (String s : bonuses){
            switch (s){
                case ArithmosLevel.BONUS_LOCK_SUB:
                case ArithmosLevel.BONUS_LOCK_ADD:
                    score *= 3;
                    break;
                case ArithmosLevel.BONUS_LOCK_DIV:
                case ArithmosLevel.BONUS_LOCK_MULT:
                    score *= 5;
                    break;
                case ArithmosLevel.BONUS_RED_JEWEL:
                    score += 25;
                    jewelCount++;
                    break;
                case ArithmosLevel.BONUS_BALLOONS:
                    score += 200;
                    break;
                case ArithmosLevel.BONUS_APPLE:
                    score += 50;
                    break;
                case ArithmosLevel.BONUS_BANANAS:
                    score += 100;
                    break;
                case ArithmosLevel.BONUS_CHERRIES:
                    score += 150;
                    break;
            }
        }
        return score;
    }
    public int getScore(String player){
        if (player.equals(PLAYER1))
            return p1Points;
        else
            return p2Points;
    }
    public int getLastScore(){
        return lastScore;
    }
    public int getNumStars() {return numStars;}
    public int getPointsForStar(int numStars){
        return starLevels[Math.max(0, Math.min(numStars - 1, 2))];
    }

    // Individual tiles
    public String getPiece(int row, int col){
        if (row >= 0 && row < gameBoard.length && col >= 0 && col < gameBoard[row].length) {
            String s = gameBoard[row][col];
            if (s == null) return null;
            String[] values = s.split(SEPARATOR);
            return values[0];
        } else
            return null;
    }
    public void setPiece(String value, int row, int col) {
        gameBoard[row][col] = value + SEPARATOR + UNDEF;
    }
    public boolean isPiecePlayed(int[] location){
        return isPiecePlayed(location[0], location[1]);
    }
    public boolean isPiecePlayed(int r, int c){
        if (r >= 0 && r < gameBoard.length && c >= 0 && c < gameBoard[r].length){
            String[] a = gameBoard[r][c].split(SEPARATOR);
            return !a[1].equals(UNDEF);
        } else
            return false;
    }
    public boolean isPieceOwnerByCurrentPlayer(int r, int c){
        String[] strings = gameBoard[r][c].split(SEPARATOR);
        String owner = strings[1];
        return owner.equals(currentPlayer);
    }
    public boolean isPieceOwned(int r, int c, String player){
        String[] strings = gameBoard[r][c].split(SEPARATOR);
        String owner = strings[1];
        return owner.equals(player);

    }
    public boolean isBonus(int[] location){
        return isBonus(location[0], location[1]);
    }
    public boolean isBonus(int r, int c){
        if (r < 0 || r >= gameBoard.length || c < 0 || c >= gameBoard[r].length)
            return false;
        else if (gameBoard[r][c] == null) return false;
        else return gameBoard[r][c].contains(BONUS);
    }
    public boolean isOpLockBonus(int[] location){
        String s = gameBoard[location[0]][location[1]];
        return s.contains(ArithmosLevel.BONUS_OP_LOCK);
    }
    public boolean isBombBonus(int[] location){
        return isBombBonus(location[0], location[1]);
    }
    public boolean isBombBonus(int r, int c){
        if (r < 0 || r >= gameBoard.length || c < 0 || c >= gameBoard[r].length)
            return false;
        else if (gameBoard[r][c] == null) return false;
        else return gameBoard[r][c].contains(ArithmosLevel.BONUS_BOMB);
    }
    public String getLockedOperation(int[] location){
        String[] s = gameBoard[location[0]][location[1]].split(SEPARATOR);
        return s[0].replace(ArithmosLevel.BONUS_OP_LOCK, "");
    }


    // Serialization
    transient private static final int serializationVersion = 6;

    public byte[] getSaveGameData(){
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            writeObject(oos);
            oos.close();
            bos.close();
        } catch (IOException e)
        {e.printStackTrace();}
        return bos.toByteArray();
    }
    public void writeObject(ObjectOutputStream out) throws IOException {
        // Serialization version 1
        out.writeInt(serializationVersion);
        out.writeObject(gameBoard);
        out.writeObject(goalNumbers);
        out.writeObject(goalList);
        out.writeObject(remainingGoals);
        out.writeInt(p1_301Total);
        out.writeInt(p2_301Total);
        out.writeInt(goalType);
        out.writeObject(PLAYER1);
        out.writeObject(PLAYER2);
        out.writeInt(p1Points);
        out.writeInt(p2Points);
        out.writeObject(leaderboardId);
        out.writeObject(challengeName);
        out.writeInt(challengeLevel);
        out.writeObject(p1Runs);
        out.writeObject(p2Runs);
        out.writeObject(starLevels);
        out.writeInt(numStars);
        out.writeObject(p1AvailableOperations);
        out.writeObject(p2AvailableOperations);
        out.writeObject(p1OpLimitCounts);
        out.writeObject(p2OpLimitCounts);
        out.writeObject(p1BonusCounts);
        out.writeObject(p2BonusCounts);
        // Version 2
        out.writeObject(p1Message);
        out.writeObject(p2Message);
        // Version 3
        out.writeLong(timeLimit);
        // Version 4
        out.writeLong(elapsedTime);
        out.writeInt(jewelCount);
        // Version 5
        out.writeObject(p1GoalsWon);
        out.writeObject(p2GoalsWon);
        // Version 6
        out.writeInt(numGoalsToWin);
    }
    public void loadGameData(byte[] gameData){
        ByteArrayInputStream bis = new ByteArrayInputStream(gameData);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            readObject(ois);
        } catch (IOException | ClassNotFoundException e) {e.printStackTrace();}
    }
    @SuppressWarnings("unchecked")
    public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        if (version >= 1) {
            gameBoard = (String[][])in.readObject();
            goalNumbers = (int[])in.readObject();
            goalList = (ArrayList<String>)in.readObject();
            remainingGoals = (ArrayList<String>)in.readObject();
            p1_301Total = in.readInt();
            p2_301Total = in.readInt();
            goalType = in.readInt();
            PLAYER1 = (String)in.readObject();
            PLAYER2 = (String)in.readObject() ;
            p1Points = in.readInt();
            p2Points = in.readInt();
            leaderboardId = (String)in.readObject();
            challengeName = (String)in.readObject();
            challengeLevel = in.readInt();
            p1Runs = (ArrayList<ArrayList<int[]>>)in.readObject();
            p2Runs = (ArrayList<ArrayList<int[]>>)in.readObject();
            starLevels = (int[])in.readObject();
            numStars = in.readInt();
            p1AvailableOperations = (String[])in.readObject();
            p2AvailableOperations = (String[])in.readObject();
            p1OpLimitCounts = (HashMap<String, Integer>)in.readObject();
            p2OpLimitCounts = (HashMap<String, Integer>)in.readObject();
            p1BonusCounts = (HashMap<String, Integer>)in.readObject();
            p2BonusCounts = (HashMap<String, Integer>)in.readObject();
            currentPlayer = PLAYER1;
            p1GoalsWon = new ArrayList<>();
            p2GoalsWon = new ArrayList<>();
            numGoalsToWin = remainingGoals != null ? remainingGoals.size() : 0;
        }
        if (version >= 2){
            p1Message = (String)in.readObject();
            p2Message = (String)in.readObject();
        }
        if (version >= 3){
            timeLimit = in.readLong();
        }
        if (version >= 4){
            elapsedTime = in.readLong();
            jewelCount = in.readInt();
        }
        if (version >= 5){
            p1GoalsWon = (ArrayList<String>)in.readObject();
            p2GoalsWon = (ArrayList<String>)in.readObject();
        } if (version >= 6){
            numGoalsToWin = in.readInt();
        }
    }

    public static class GameResult {
        public static final int SUCCESS = 1;
        public static final int FAILURE = -1;
        public static final int TIME_UP = -2;
        public static final int FORFEIT = -3;

        public int result = 0;
        public int score = 0;
        public int numStars = 0;
        public int value = 0;
        public boolean isLevelPassed = false;
        public boolean noMorePossiblePlays = false;
        public ArrayList<int[]> bonusLocations;
        public ArrayList<String> opsReplaced;

        public GameResult() {
            bonusLocations = new ArrayList<>();
            opsReplaced = new ArrayList<>();
        }
        public GameResult(int result) {
            this.result = result;
            bonusLocations = new ArrayList<>();
            opsReplaced = new ArrayList<>();
        }
    }
}
