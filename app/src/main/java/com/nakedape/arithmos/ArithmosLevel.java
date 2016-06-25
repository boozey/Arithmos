package com.nakedape.arithmos;

import android.content.Context;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Nathan on 5/7/2016.
 */
public class ArithmosLevel {
    public static final int GOAL_SINGLE_NUM = 4002;
    public static final int GOAL_MULT_NUM = 4001;
    public static final int GOAL_MULTIPLES = 4003;
    public static final int GOAL_301 = 4004;
    public static final String BONUS_OP_LOCK = "bonus_lock";
    public static final String BONUS_LOCK_ADD = "bonus_lock+";
    public static final String BONUS_LOCK_SUB = "bonus_lock-";
    public static final String BONUS_LOCK_MULT = "bonus_lock*";
    public static final String BONUS_LOCK_DIV = "bonus_lock/";
    public static final String BONUS_BALLOONS = "bonus_balloons";
    public static final String BONUS_RED_JEWEL = "bonus_red_jewel";
    public static final String BONUS_BOMB ="bonus_bomb";
    public static final String BONUS_APPLE = "bonus_apple";
    public static final String BONUS_BANANAS = "bonus_bananas";
    public static final String BONUS_CHERRIES = "bonus_cherries";

    private int[] gridNumbers;
    public int[] getGridNumbers() { return gridNumbers; }

    private int[] gridSpecialNumbers;
    public int[] getGridSpecialNumbers() { return gridSpecialNumbers; }

    private int[] goalNumbers;
    public int[] getGoalNumbers() { return goalNumbers; }

    private int numGoalsToWin;
    public int getNumGoalsToWin() {return numGoalsToWin;}

    private int gridSize;
    public int getGridSize(){ return gridSize; }

    private int goalType = GOAL_MULT_NUM;
    public int getGoalType() { return goalType; }

    private int timeLimitMillis = -1;
    public int getTimeLimitMillis() { return timeLimitMillis; }
    public boolean hasTimeLimit() { return timeLimitMillis > -1 ;}

    private String challenge;
    public String getChallenge() { return challenge; }

    private int challengeLevel;
    public int getChallengeLevel() { return challengeLevel; }

    public int getChallengeDisplayNameId(){
        return ArithmosGameBase.getChallengeDisplayNameResId(challenge);
    }
    public int getLevelDisplayNameId(){
        return ArithmosGameBase.getLevelDisplayNameResIds(challenge)[challengeLevel];
    }

    private int[] starLevels;
    public int[] getStarLevels() {return starLevels;}

    private String leaderboardId;
    public String getLeaderboardId() { return leaderboardId; }

    private ArrayList<String> bonuses;
    public String[] getBonuses(){
        String[] result = new String[bonuses.size()];
        for (int i = 0; i < bonuses.size(); i++)
            result[i] = bonuses.get(i);
        return result;
    }

    private ArrayList<String[]> runs;
    public ArrayList<String[]> getRuns() {return runs;}

    public ArithmosLevel(Context context, int levelResId){
        bonuses = new ArrayList<>(5);
        runs = new ArrayList<>(5);
        XmlResourceParser parser = context.getResources().getXml(levelResId);
        // Process xml level data
        try {
            int eventType = -1;
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    switch (parser.getName()) {
                        case "Level":
                            parseLevelTag(parser);
                            break;
                        case "GridBaseNumbers":
                            parseGridBaseTag(parser);
                            break;
                        case "GridSpecialNumbers":
                            parseGridSpecialTag(parser);
                            break;
                        case "GoalNumbers":
                            parseGoalNumbersTag(parser);
                            break;
                        case "Bonus":
                            parseBonusTag(parser);
                            break;
                        case "StarLevels":
                            parseStarLevelsTag(parser);
                            break;
                        case "Run":
                            parseRunTag(parser);
                            break;
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e){
            e.printStackTrace();
        }
    }

    private void parseLevelTag(XmlResourceParser parser){
        for (int i = 0; i < parser.getAttributeCount(); i++){
            switch (parser.getAttributeName(i)){
                case "challenge_name":
                    challenge = parser.getAttributeValue(i);
                    break;
                case "challenge_level":
                    challengeLevel = parser.getAttributeIntValue(i, 0);
                    break;
                case "gridsize":
                    gridSize = parser.getAttributeIntValue(i, 6);
                    break;
                case "leaderboardid":
                    leaderboardId = parser.getAttributeValue(i);
                    break;
                case "goal_type":
                    String name = parser.getAttributeValue(i);
                    if (name.equals("301")) goalType = GOAL_301;
                    else goalType = GOAL_MULT_NUM;
                    break;
                case "time_limit":
                    timeLimitMillis = 1000 * parser.getAttributeIntValue(i, -1);
                    break;
            }
        }
    }
    private void parseGridBaseTag(XmlResourceParser parser) throws XmlPullParserException, IOException{
            parser.next();
            String text = parser.getText().replace(" ", "");
            String[] numbers = text.split(",");
            gridNumbers = new int[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                gridNumbers[i] = Integer.valueOf(numbers[i]);
            }
    }
    private void parseGridSpecialTag(XmlResourceParser parser) throws XmlPullParserException, IOException{
            parser.next();
            String text = parser.getText().replace(" ", "");
            String[] numbers = text.split(",");
            gridSpecialNumbers = new int[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                gridSpecialNumbers[i] = Integer.valueOf(numbers[i]);
            }
    }
    private void parseGoalNumbersTag(XmlResourceParser parser) throws XmlPullParserException, IOException{
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case "count":
                    numGoalsToWin = parser.getAttributeIntValue(i, 0);
                    break;
            }
        }
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] numbers = text.split(",");
        goalNumbers = new int[numbers.length];
        for (int i = 0; i < numbers.length; i++){
            goalNumbers[i] = Integer.valueOf(numbers[i]);
        }
        if (goalNumbers.length > 1) goalType = GOAL_MULT_NUM;
        else goalType = GOAL_SINGLE_NUM;
        numGoalsToWin = numGoalsToWin > 0 ? numGoalsToWin : goalNumbers.length;
    }
    private void parseBonusTag(XmlResourceParser parser) throws XmlPullParserException, IOException{
        String type = BONUS_BALLOONS;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case "name":
                    type = "bonus_" + parser.getAttributeValue(i);
                    break;
            }
        }
        parser.next();
        int count = Integer.valueOf(parser.getText());
        for (int i = 0; i < count; i++)
            bonuses.add(type);
    }
    private void parseStarLevelsTag(XmlResourceParser parser) throws XmlPullParserException, IOException {
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] numStrings = text.split(",");
        starLevels = new int[numStrings.length];
        for (int i = 0; i < numStrings.length; i++)
            starLevels[i] = Integer.valueOf(numStrings[i]);
    }
    private void parseRunTag(XmlResourceParser parser) throws XmlPullParserException, IOException {
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] entries = text.split(",");
        runs.add(entries);
    }
}
