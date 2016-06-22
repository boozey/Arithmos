package com.nakedape.arithmos;

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Nathan on 5/8/2016.
 */
public class ArithmosGameBase {
    transient private static final String LOG_TAG = "ArithmosGameBaseData";

    transient public static final String CRAZY_EIGHTS = "CRAZY_EIGHTS";
    transient public static final String EASY_123 = "EASY_123";
    transient public static final String LUCKY_7 = "LUCKY_7";
    transient public static final String SPECIAL_BOMB = "SPECIAL_BOMB";
    transient public static final String SPECIAL_SKIP = "SPECIAL_SKIP";
    transient public static final String SPECIAL_OP_ORDER = "SPECIAL_OP_ORDER";
    transient public static final String SPECIAL_CHANGE = "SPECIAL_CHANGE";
    transient public static final String SPECIAL_ZERO = "SPECIAL_ZERO";

    // Order of challenges is that of the array
    transient public static final String[] challenges = {CRAZY_EIGHTS, EASY_123, LUCKY_7};

    public static int getChallengeDisplayNameResId(String challenge){
        switch (challenge){
            case CRAZY_EIGHTS:
                return R.string.challenge_name_crazy_eights;
            case EASY_123:
                return R.string.challenge_name_easy_as_123;
            case LUCKY_7:
                return R.string.challenge_name_lucky_7;
            default:
                return -1;
        }
    }

    public static int[] getLevelXmlIds(String challenge){
        switch (challenge){
            case EASY_123:
                return new int[] {R.xml.game_level_easy_123_4x4, R.xml.game_level_easy_123_4x4_timed, R.xml.game_level_easy_123_5x5,
                        R.xml.game_level_easy_123_5x5_301, R.xml.game_level_easy_123_5x5_timed,
                        R.xml.game_level_easy_123_6x6};
            case CRAZY_EIGHTS:
                return new int[] {R.xml.game_level_crazy_eights_4x4, R.xml.game_level_crazy_eights_4x4_301, R.xml.game_level_crazy_eights_5x5,
                        R.xml.game_level_crazy_eights_5x5_301, R.xml.game_level_crazy_eights_5x5_timed, R.xml.game_level_crazy_eights_6x6,
                        R.xml.game_level_crazy_eights_6x6_301, R.xml.game_level_crazy_eights_6x6_timed};
            case LUCKY_7:
                return new int[] {R.xml.game_level_lucky_7_4x4};
            default:
                return getLevelXmlIds(EASY_123);
        }
    }

    public static int[] getLevelDisplayNameResIds(String challenge){
        switch (challenge){
            case EASY_123:
                return new int[] {R.string.level_4x4, R.string.level_4x4_timed,
                        R.string.level_5x5, R.string.level_5x5_301, R.string.level_5x5_timed,
                        R.string.level_6x6};
            case CRAZY_EIGHTS:
                return new int[] {R.string.level_4x4, R.string.level_4x4_301, R.string.level_5x5, R.string.level_5x5_301, R.string.level_5x5_timed,
                        R.string.level_6x6, R.string.level_6x6_301, R.string.level_6x6_timed};
            case LUCKY_7:
                return new int[] {R.string.level_4x4};
            default:
                return getLevelDisplayNameResIds(EASY_123);
        }
    }

    transient private static final int serializationVersion = 4;
    transient private static final long weekInMillis = 604800000;
    transient private static final long dayInMillis = 86400000;
    transient private ArrayList<String> unlockedLevels;
    transient private HashMap<String, Integer> stars;
    transient private int jewelCount;
    transient private HashMap<String, Integer> specials;
    transient private ArrayList<GameActivityItem> activityItems;
    transient private boolean needsSaving = false;
    transient private long timeStamp = 0;

    public ArithmosGameBase(){
        unlockedLevels = new ArrayList<>();
        unlockedLevels.add(EASY_123 + "0");
        stars = new HashMap<>();
        jewelCount = 0;
        specials = new HashMap<>(1);
        specials.put(SPECIAL_SKIP, 1);
        activityItems = new ArrayList<>();
    }

    public void setSaved(boolean state){
        needsSaving = state;
    }

    public boolean needsSaving(){return needsSaving;}

    public void resetGame(){
        unlockedLevels.add(challenges[0] + String.valueOf(0));
        unlockAllLevels();
        stars = new HashMap<>();
        activityItems = new ArrayList<>();
        needsSaving = true;
    }

    public long timeStamp() {return timeStamp;}

    // Level unlocking
    public boolean isLevelUnlocked(String challengeName, int level){
        return unlockedLevels.contains(challengeName + String.valueOf(level));
    }

    public int[] unlockNextLevel(String challengeName, int levelPassed){
        int nextLevel = levelPassed + 1;
        if (nextLevel < getLevelXmlIds(challengeName).length) {
            if (!isLevelUnlocked(challengeName, nextLevel)) {
                unlockedLevels.add(challengeName + String.valueOf(nextLevel));
                needsSaving = true;
            }
            return new int[] {getChallengeDisplayNameResId(challengeName), getLevelDisplayNameResIds(challengeName)[nextLevel]};
        } else {
            switch (challengeName) {
                case CRAZY_EIGHTS:
                    return unlockNextLevel(EASY_123, -1);
                case EASY_123:
                    return unlockNextLevel(LUCKY_7, -1);
            }
        }
        return null;
    }

    private void unlockAllLevels(){
        for (String challenge : challenges){
            for (int i = 0; i < getLevelXmlIds(challenge).length; i++)
                unlockedLevels.add(challenge + i);
        }
    }


    // Bonuses
    public void recordStars(String challengeName, int level, int numStars){
        if (numStars > 0) {
            if (stars.containsKey(challengeName + String.valueOf(level))) {
                int record = stars.get(challengeName + String.valueOf(level));
                if (numStars > record) {
                    stars.put(challengeName + String.valueOf(level), numStars);
                    Log.d(LOG_TAG, "Number stars updated to " + stars.get(challengeName + String.valueOf(level)));
                }
            } else
                stars.put(challengeName + String.valueOf(level), numStars);
            needsSaving = true;
        }
    }

    public int getNumStars(String challengeName, int level){
        if (stars.containsKey(challengeName + String.valueOf(level)))
            return stars.get(challengeName + String.valueOf(level));
        else
            return 0;
    }

    public void recordJewels(int num){
        jewelCount += num;
        needsSaving = true;
    }

    public void spendJewels(int num){
        jewelCount -= num;
        needsSaving = true;
    }

    public int getJewelCount(){
        return jewelCount;
    }


    // Specials
    public int getSpecialCount(String special){
        if (specials.containsKey(special))
            return specials.get(special);
        else
            return 0;
    }

    public void addSpecial(String special, int amount){
        amount += getSpecialCount(special);
        specials.put(special, amount);
        needsSaving = true;
    }

    public int useSpecial(String special){
        int count = 0;
        if (specials.containsKey(special)){
            count = specials.get(special);
            count = Math.max(--count, 0);
            specials.put(special, count);
            needsSaving = true;
        }
        return count;
    }


    // Activity
    public ArrayList<GameActivityItem> getActivityItems(){
        return activityItems;
    }

    public void addActivityItem(GameActivityItem item){
        ArrayList<GameActivityItem> temp = new ArrayList<>(activityItems.size() + 1);
        temp.add(item);
        temp.addAll(activityItems);
        activityItems = temp;
        needsSaving = true;
    }

    public boolean removeActivityItem(GameActivityItem item){
        if (activityItems.remove(item)){
            needsSaving = true;
            return true;
        } else {
            return false;
        }
    }

    public void removeSavedGameActivityItem(String uniqueName){
        int index = -1;
        for (int i = 0; i < activityItems.size(); i++){
            if (activityItems.get(i).uniqueName != null && activityItems.get(i).uniqueName.equals(uniqueName)) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            activityItems.remove(index);
            needsSaving = true;
        }
    }

    public void resetActivityList() {
        activityItems = new ArrayList<>();
        needsSaving = true;
    }


    // Serialization
    public byte[] getByteData(){
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
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(serializationVersion);
        out.writeObject(unlockedLevels);
        out.writeObject(stars);
        out.writeInt(jewelCount);
        out.writeObject(specials);
        out.writeObject(activityItems);
        timeStamp = SystemClock.elapsedRealtime();
        out.writeLong(timeStamp);
    }

    public void loadByteData(byte[] data){
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            readObject(ois);
        } catch (IOException | ClassNotFoundException e) {e.printStackTrace();}
    }
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        unlockedLevels = (ArrayList<String>)in.readObject();
        stars = (HashMap<String, Integer>)in.readObject();
        jewelCount = in.readInt();
        if (version >= 2){
            specials = (HashMap<String, Integer>)in.readObject();
        }
        if (version >= 3){
            ArrayList<GameActivityItem> items = (ArrayList<GameActivityItem>)in.readObject();
            // Remove outdated entries
            activityItems = new ArrayList<>(items.size());
            for (GameActivityItem item : items){
                if (item.timeStamp > System.currentTimeMillis() - dayInMillis)
                    activityItems.add(item);
            }
        } if (version >= 4)
            timeStamp = in.readLong();

        needsSaving = false;
    }
}
